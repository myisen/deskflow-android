# DeskflowAndroid 架构概览

> 本文档描述 Deskflow Android 客户端的代码结构、模块划分与关键协作关系。目标读者是希望理解代码组织方式、准备贡献修改的开发者。

## 1. 项目定位

DeskflowAndroid 是 [Deskflow](https://deskflow.org)（原名 Synergy）的 Android 客户端。它让一部 Android 设备以 **客户端（client）** 身份加入 Deskflow 网络，接收来自服务端（通常是桌面 PC）的键盘与鼠标事件，并把 Android 屏幕画面、剪贴板内容反向发送回服务端。

平台与构建约束：

| 项 | 值 |
|---|---|
| 包名 | `org.tfv.deskflow` |
| minSdk | 34（Android 14） |
| compileSdk | 36 |
| 构建工具 | AGP 8.10.1 / Kotlin 2.1.21 / JDK 17 |
| 支持 ABI | `armeabi-v7a`、`arm64-v8a`（不含 x86） |
| 语言 | Kotlin 100% |

## 2. 多模块结构

Gradle 工程按关注点拆成 4 个模块：

```
DeskflowAndroid/
├── app/                  Android 主应用（UI + Service + AIDL）
├── client/               Deskflow 协议实现（纯 JVM library）
├── client-cli/           client 模块的命令行测试封装
├── iconics-typeface-library/  图标字体资源库
├── buildSrc/             共享 Gradle convention 与版本读取脚本
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

依赖方向是单向的：`app` 依赖 `client`、`iconics-typeface-library`、`buildSrc`；`client-cli` 依赖 `client`；`client` 本身不依赖 Android SDK，可以在 JVM 单元测试里独立跑。

```
client-cli ──▶ client ◀── app
                   │
                   ▼
             iconics-typeface-library
```

## 3. client 模块：协议与事件

`client` 模块是 Deskflow 协议的 Kotlin 实现。它不认识 Android，只管 **TCP 全双工通信 + 事件编解码 + 事件分发**。

```
client/src/main/java/org/tfv/deskflow/client/
├── ClientEventBus.kt            事件总线，所有订阅点都通过它收事件
├── net/
│   └── FullDuplexSocket.kt      基于 Okio 的全双工 TCP，读写各自独立协程
├── events/                      所有 Deskflow 事件类型
│   ├── ConnectionEvent.kt      连上/断开/握手
│   ├── KeyboardEvent.kt         按键按下/抬起
│   ├── MouseEvent.kt            移动/点击/滚轮
│   ├── ScreenEvent.kt           屏幕加入/离开/分辨率变更
│   └── MessagesEvent.kt        服务端向客户端发的文本消息
├── manager/                     辅助管理器
│   ├── ClipboardSendManager.kt   客户端 → 服务端 剪贴板推送
│   ├── ClipboardReceiveManager.kt 服务端 → 客户端 剪贴板接收
│   └── MessageHandler.kt        事件 → 上层回调 的胶水代码
├── models/
│   ├── ServerTarget.kt          服务端地址 + 证书指纹
│   ├── ClipboardData.kt         剪贴板数据 + marker
│   └── keys/KeyModifierMask.kt  修饰键位掩码（Ctrl/Alt/Shift/Meta）
├── Versions.kt                  Deskflow 协议版本号
└── exceptions/                  网络/协议异常
```

一条典型键盘事件的流经路径：

```
FullDuplexSocket.read loop
  └─▶ 协议解码
      └─▶ KeyboardEvent
          └─▶ ClientEventBus.publish
              └─▶ （订阅者分发：VirtualKeyboardService / 其他组件）
```

## 4. app 模块：Android 集成

`app` 模块是把 client 协议 "接到" Android 系统上的胶水层。核心是 **三个 Service + 一个 UI Activity**。

### 4.1 包结构

```
app/src/main/java/org/tfv/deskflow/
├── Application.kt              Hilt Application，启动时初始化依赖图
├── components/
│   ├── GlobalKeyboardManager.kt  连接 ConnectionService 与 KeyboardService 的中央协调器
│   └── FileManager.kt           证书、配置文件读写
├── data/
│   ├── AppPrefsSerializer.kt   ProtoBuf 格式偏好设置序列化
│   └── AppPrefContextExt.kt     DataStore 访问扩展
├── receivers/
│   └── BootReceiver.kt          BOOT_COMPLETED 自启动 ConnectionService
├── services/
│   ├── ConnectionService.kt     AIDL Service，客户端/服务端连接管理
│   ├── ConnectionServiceClient.kt Activity/UI 侧的 AIDL 客户端包装
│   ├── ConnectionStateModel.kt  连接状态 SharedFlow
│   ├── VirtualKeyboardService.kt Android IME，接收远键盘事件注入系统
│   ├── GlobalInputService.kt    AccessibilityService，接收远鼠标事件
│   └── keyboard/                IME 内部辅助
│       ├── KeyboardEditHistory.kt
│       └── actions/
├── ui/                          Jetpack Compose UI
│   ├── activities/RootActivity.kt
│   ├── screens/                 RootScreen、SettingsScreen
│   ├── components/preview/      悬浮预览窗口
│   ├── components/RootNavHost.kt
│   └── theme/                   Material3 主题
└── aidl/                        IPC 接口
    ├── IConnectionService.aidl
    ├── IConnectionServiceCallback.aidl
    ├── ConnectionState.aidl
    ├── ServerState.aidl
    ├── ScreenState.aidl
    └── Result.aidl
```

### 4.2 三个关键 Service 的协作

```
┌──────────────────────┐    AIDL     ┌──────────────────────────┐
│       UI Activity     │◀──────────▶│    ConnectionService      │
│  RootActivity /       │             │  - TCP 连接 / 握手        │
│  SettingsScreen       │             │  - 持有 FullDuplexSocket  │
└──────────┬───────────┘             │  - 订阅 ClientEventBus    │
           │                          └──────┬────────┬─────────┘
           │                                  │        │
           │ 订阅键盘事件                      │        │ 订阅屏幕/剪贴板事件
           │                                  ▼        ▼
           │                    ┌─────────────────┐  ┌─────────────────────┐
           └───────────────────▶│VirtualKeyboard   │  │  GlobalInput         │
                                │Service (IME)    │  │  Service (Access.)   │
                                │ - onKeyDown/Up  │  │  - onMouseMove/Click │
                                │ - 注入到 Input   │  │  - 注入 via          │
                                │   Connection    │  │    dispatchGesture() │
                                └─────────────────┘  └─────────────────────┘
```

- **ConnectionService**：唯一持有 `FullDuplexSocket` 的组件。负责连接生命周期（connect/disconnect/reconnect）、TLS 证书校验、事件总线订阅。对外通过 AIDL 暴露 `IConnectionService`，UI 进程的所有连接操作（connect、setServer、getState）都走它。
- **VirtualKeyboardService**：实现 Android 标准 `InputMethodService`。订阅 `KeyboardEvent`，收到后通过 `InputConnection.sendKeyEvent()` 把远侧按键注入当前聚焦 App。用户必须在系统"输入法"设置里启用它。
- **GlobalInputService**：继承 `AccessibilityService`。订阅 `MouseEvent` / `ScreenEvent`，收到后通过 `dispatchGesture()` 注入全局触控事件。需要用户授予辅助功能权限。

### 4.3 AIDL 接口（进程间通信）

App 主进程与悬浮预览/其他组件之间用 AIDL 通信：

| 接口 | 方向 | 用途 |
|---|---|---|
| `IConnectionService` | UI → Service | 连接管理：`connect()`, `disconnect()`, `setServer()`, `getState()` |
| `IConnectionServiceCallback` | Service → UI | 状态推送：`onStateChanged()`, `onError()` |
| `ConnectionState` | — | Parcelable，包含连接状态、服务端地址、已连接客户端列表 |
| `ServerState` | — | Parcelable，服务端协议版本、加密状态 |
| `ScreenState` | — | Parcelable，屏幕 ID、尺寸 |
| `Result` | — | Parcelable，操作结果（ok / error + message） |

`GlobalKeyboardManager` 是 UI 侧用的协调器：它通过 `ConnectionServiceClient`（AIDL 客户端）访问 `ConnectionService`，再把状态变化反射到 UI Compose State。

## 5. UI 层（Jetpack Compose + Material 3）

Compose 组织为"一屏一文件"，导航使用 `NavHost` + 自定义路由：

```
RootActivity
  └─▶ RootNavHost
       ├─▶ SettingsScreen       主设置页（服务端地址、加密、IME 切换引导）
       └─▶ 悬浮预览             Service 状态迷你卡片
```

状态源：
- `AppPrefsSerializer`（ProtoBuf + DataStore）— 持久化偏好
- `ConnectionStateModel`（SharedFlow）— 连接状态，所有订阅者都观察同一个 Flow

## 6. 数据流：以"一次按键"为例

```
[桌面 PC 服务端]
  │ TCP
  ▼
[FullDuplexSocket.read loop]                    ← client 模块，IO Dispatcher
  │ 协议解码 protobuf
  ▼
KeyboardEvent(key=A, mask=Ctrl)
  │
  ▼
ClientEventBus.publish
  │
  ├──▶ ConnectionService 日志记录
  ├──▶ MessageHandler 转换
  └──▶ VirtualKeyboardService                   ← app 模块，IME 线程
           │
           ▼
       InputConnection.sendKeyEvent(KeyEvent.ACTION_DOWN, KEYCODE_A)
           │
           ▼
       [Android 输入法框架 → 当前聚焦 App]
```

屏幕画面方向（Android → PC）路径类似但用 `AccessibilityService` 的 `takeScreenshot()` 能力（Android 14+），走 `ClipboardSendManager` / `ClipboardReceiveManager` 走 Deskflow 的剪贴板同步事件。

## 7. 技术栈清单

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.1.21 |
| Android 构建 | AGP | 8.10.1 |
| UI | Jetpack Compose + Material 3 | BOM 2025.06 |
| 导航 | Navigation Compose | 2.9.0 |
| DI | Hilt | 2.56 |
| 并发 | kotlinx-coroutines | 1.10.2 |
| 序列化 | kotlinx-serialization-json + ProtoBuf | 1.8.1 / 4.31.0 |
| 持久化 | DataStore + ProtoBuf | 1.1.7 |
| 网络 | Okio（通过 FullDuplexSocket） | — |
| 事件总线 | 自研 ClientEventBus（基于 Flow） | — |
| 日志 | kotlin-logging + SLF4J Android | 7.0.7 / 1.7.36 |
| 测试 | JUnit 5 + AndroidX Test | 5.10.1 |
| 图标 | Iconics | 5.4.0 |
| 功能编程 | Arrow-KT（可选） | 2.1.0 |

## 8. 权限矩阵

AndroidManifest 里声明的权限：

| 权限 | 用途 |
|---|---|
| `BIND_INPUT_METHOD` | 注册为系统 IME（VirtualKeyboardService） |
| `SYSTEM_ALERT_WINDOW` | 悬浮预览小窗 |
| `INTERNET` + `ACCESS_NETWORK_STATE` | 连接 Deskflow 服务端 |
| `NEARBY_WIFI_DEVICES` + `ACCESS_WIFI_STATE` | 局域网设备发现 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启动 ConnectionService |
| `POST_NOTIFICATIONS` | 前台通知显示连接状态 |
| `org.tfv.deskflow.CONNECTION_SERVICE_PERMISSION` | 自定义签名权限，保护 ConnectionService 的 AIDL 入口不被第三方随意调用 |

## 9. 测试矩阵

当前测试覆盖：
- `client` 模块有完整 JVM 单元测试（`client/src/test`），无需 Android 设备即可运行
- `app/src/androidTest` 有 `components/test` 下的仪器化测试

CI（GitHub Actions）每次 push `v**` tag 自动构建 AAB + APK 并上传 Release。

## 10. 快速上手

```bash
# 克隆 + 构建（需要 Android Studio 或完整 Android SDK）
./gradlew assembleDebug

# 只跑 client 模块单元测试（不需要 Android SDK）
./gradlew :client:test

# 手动触发 GitHub Release（API 或 UI）
# UI: https://github.com/myisen/deskflow-android/actions/workflows/release.yml
```

安装后需要在系统设置里：
1. 启用 Deskflow 的 **虚拟键盘**（输入法设置）
2. 授予 **辅助功能** 权限
3. 允许 **悬浮窗**（Android 13+）
4. 在 App 里填入 Deskflow 服务端地址 → 连接
