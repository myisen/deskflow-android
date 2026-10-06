# 修复计划：中文输入 + 息屏问题

> 本文档描述 Deskflow Android 客户端两个问题的根因与修复方案。
> 读者是准备按步骤实施修复的开发者。

## 问题 1：中文输入不工作

### 1.1 根因

Deskflow 协议在服务端（PC）I ME 输入中文时发送 **DKDL (KeyDownLang)** 消息，携带 UTF-8 文本。但 Android 客户端的 client 模块在**5 层管道中层层丢失这个文本**：

| 层 | 文件 | 问题 |
|---|---|---|
| 协议解析 | `KeyDownMessage.readData()` | 只 `readUnsignedShort()×3`，模板里的 `%s` 字符串被跳过 |
| 事件类型 | `KeyboardEvent` data class | 无 `text` / `char` 字段 |
| 事件分发 | `MessageHandler.handle()` | 只传 id/button/mask，text 不往下传 |
| MessageType | `MessageType.kt` | 只注册 `DKEYDOWN("DKDN")`，没注册 `DKDL` |
| IME 输入 | `VirtualKeyboardService.onKeyboardEvent()` | `id.toChar()` 对 Deskflow 键码（通常 >255）产生错误结果 |

### 1.2 修复范围

#### Phase A — client 模块（协议层）

**A1. 扩展 KeyDownMessage 与 KeyRepeatMessage，支持 DKDL/DKRL 的文本字段**

- `KeyDownMessage` 加 `var text: String = ""` 字段
- `readData()` 里：如果 template specifiers 有 string（`DKDL`、`DKRP`），在 3 个 short 后读 UTF-8 字符串
- 同样改 `KeyRepeatMessage`（模板 `DKRP%2i%2i%2i%2i%s` 已有 string）

**A2. MessageType 增加 DKEYDOWNLANG / DKEYREPEATLANG**

```kotlin
DKEYDOWNLANG("DKDL", "[Data] Key Down (lang-aware)"),
DKEYREPEATLANG("DKRP", "[Data] Key Repeat (lang-aware)"),
```

**A3. KeyboardEvent 增加可选 text 字段**

```kotlin
data class KeyboardEvent(
    val type: Type,
    val id: UInt,
    val button: UInt = 0u,
    val mask: UInt,
    val count: Short = 0,
    val text: String? = null,  // ← 新增：DKDL/DKRP 携带的文本
)
```

**A4. MessageHandler 把 text 传下去**

```kotlin
is KeyDownMessage -> {
    ClientEventBus.emit(
        KeyboardEvent.down(message.id, message.button, message.mask, text = message.text)
    )
}
```

#### Phase B — app 模块（IME 层）

**B1. VirtualKeyboardService.onKeyboardEvent() 优先用 text**

```kotlin
// 当前
val keyChar = id.toChar()
applyCommand(keyChar.toString(), ic, et)

// 修改后
event.text?.let {
    // DKDL/DKRP 携带了文本（中文、日文、Unicode 都走这里）
    ic.commitText(it, 1)
} ?: run {
    // 回退路径：普通按键（ASCII 字母、数字、F 键等）
    val keyChar = id.toChar()
    applyCommand(keyChar.toString(), ic, et)
}
```

**B2. 加日志：收到 DKDL 文本时打印 UTF-8 bytes**

方便联调，看 Deskflow 服务端到底发了什么。

#### Phase C — CCHR 协议扩展（可选增强）

Deskflow 还有个 `CCHR` (CharEvent / TextEvent) 消息，模板格式大概是 `CCHR%2i%2i%2i` 或带 string。当前 Android 客户端完全没实现。可以：
- 在 MessageTemplate 里注册 CCHR
- 新建 `CharMessage` 类
- 新建 `TextEvent` ClientEvent
- VirtualKeyboardService 订阅 TextEvent 并用 `commitText`

这个改动**不是必须**，因为现代 Deskflow 服务端 IME 场景主要发 DKDL。但 CCHR 能处理某些特殊 Unicode 边界情况。

### 1.3 测试验证

- **ASCII 字母**：PC 输入 `abc` → Android App 输入框出现 `abc`（回归）
- **中文**：PC 中文输入法（搜狗/谷歌拼音）输入 `nihao` → Android 出现 `你好`（目标）
- **混合**：`abc你好123`（目标）
- **特殊键**：Ctrl+C、Alt+Tab、F5（回归，不应受影响）
- **输入法切换**：PC 上切中文输入法再切回来（边界）

### 1.4 风险点

- Deskflow 服务端发的 DKDL 文本可能是**部分组合字符**（如 UTF-8 多字节序列未闭合），IME 层应该能正确处理这种情况
- Deskflow 不同版本协议略有差异，需要确认连接的服务端是否发送 DKDL（有些可能只发 DKDN）
- `id.toChar()` 在 `id > 0xFFFF` 时会截断，但 Deskflow 键码不会超 16 位

---

## 问题 2：输入时仍然息屏

### 2.1 根因

Android 系统息屏计时器只看**原生触摸/按键事件**。Deskflow 客户端的远端输入走 `InputConnection.commitText()` — Android 系统**可能不把这算作用户交互**。

| 缺失 | 影响 |
|---|---|
| `WAKE_LOCK` 权限 | 无法拿 WakeLock |
| `FLAG_KEEP_SCREEN_ON` | IME 窗口显示时不阻止息屏 |
| 任何 WakeLock | 持续输入时屏幕变暗 → 息屏 |

### 2.2 修复范围

#### Phase A — Manifest 声明

`AndroidManifest.xml` 加一行：

```xml
<uses-permission android:name="android.permission.WAKE_LOCK"/>
```

#### Phase B — VirtualKeyboardService（精准唤醒）

在 `onCreateInputView()` 里给窗口加 `FLAG_KEEP_SCREEN_ON`：

```kotlin
override fun onCreateInputView(): View {
    val win = window.window ?: return super.onCreateInputView()
    win.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    ...
}
```

键盘窗口显示时保持屏幕常亮；键盘隐藏时（用户离开输入框）系统自动移除该 flag。

#### Phase C — 可选：WakeLock 兜底

如果 FLAG_KEEP_SCREEN_ON 在某些 OEM 上不生效，加一个 PARTIAL_WAKE_LOCK：

```kotlin
// onCreate 时
val pm = getSystemService(POWER_SERVICE) as PowerManager
wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Deskflow:Input")
wakeLock.setReferenceCounted(false)

// onKeyboardEvent 收到事件时（每次 touch 重置）
if (wakeLock.isHeld) wakeLock.release()
wakeLock.acquire(2000L)  // 2 秒超时，持续输入会不断续期
```

`PARTIAL_WAKE_LOCK` 只保持 CPU 运行，不强制屏幕亮 — 省电。`FLAG_KEEP_SCREEN_ON` 是屏幕级别的，配合用效果更好。

#### Phase D — ConnectionService 前台通知

当前 `ConnectionService` 已有 `startForeground()`（否则早就被 Android 杀死了）。确认它的通知 priority 至少是 `IMPORTANCE_LOW`，不会被系统折叠。必要时提升到 `IMPORTANCE_DEFAULT`。

### 2.3 测试验证

- **连接后持续输入**：PC 上连续打字 3 分钟 → Android 屏幕保持亮
- **无输入自动息屏**：停止输入后等 timeout（默认 15-30 秒）→ 正常息屏
- **息屏后唤醒**：按电源键唤醒 → 连接保持，继续输入正常
- **断开连接**：关闭 Deskflow 服务端 → WakeLock 正确释放，不泄漏

### 2.4 风险点

- `FLAG_KEEP_SCREEN_ON` 在某些定制 ROM（MIUI、ColorOS）上行为可能不同，需要真机回归
- WakeLock 不释放会导致耗电（wakelock 泄漏），必须用超时 acquire + 生命周期释放
- Android 14+ 前台服务启动限制更严 — 如果 ConnectionService 没被授予特殊权限，WakeLock 可能被系统忽略

---

## 实施优先级

| 顺序 | 工作 | 预计改动量 | 影响面 |
|---|---|---|---|
| 1 | 中文输入 Phase A（client 协议层） | ~80 行，3 个文件 | client 模块 |
| 2 | 中文输入 Phase B（app IME 层） | ~20 行，1 个文件 | app 模块 |
| 3 | 息屏修复 Phase A+B（Manifest + FLAG） | ~5 行，2 个文件 | app 模块 |
| 4 | 息屏修复 Phase C（WakeLock 兜底） | ~40 行，1 个文件 | app 模块 |
| 5 | 中文输入 Phase C（CCHR 可选） | ~100 行 | client + app |

建议先做 1+2（中文输入核心链路），然后 3+4（息屏），5 是后续优化。

---

## 构建与验证命令

```bash
# 本地 debug 构建（改完 client + app 都重编）
bash scripts/android-dev.sh rebuild-debug

# 装真机
bash scripts/android-dev.sh install-debug

# 看日志（中文输入关键）
bash scripts/android-dev.sh logcat | grep -E "KeyboardEvent|DKDL|VirtualKeyboard|commitText"

# 签名 release（改完要发版）
./gradlew assembleRelease
apksigner verify app/build/outputs/apk/release/app-release.apk
```
