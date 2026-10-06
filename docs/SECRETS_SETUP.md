# 正式 Release 签名接入指引

本文档指导如何把 Deskflow Android 的 Release APK 从 **CI 自动临时 debug 签名** 切换到 **正式 release keystore 签名**。

## 当前状态

| 项 | 值 |
|---|---|
| 本地 keystore | `/root/.android-signing/deskflow-release.jks` |
| 备份包 | `release-artifacts/deskflow-release-keystore-backup.tar.gz` |
| alias | `release` |
| storepass | `Deskflow2026!` |
| validity | 10000 天 |
| SHA-256 指纹 | `12:74:15:75:D7:80:C7:8F:B2:07:11:C2:30:DC:A3:ED:9B:0F:8D:8B:EE:72:08:8E:B8:60:F8:06:9E:F8:96:D4` |

## Step 1：备份 keystore

沙箱重启会丢失所有 `/root` 下的文件。**立刻**从沙箱下载备份包到你本地机器：

```bash
# 在沙箱里执行一次（已经替你跑完了）
bash scripts/backup-release-keystore.sh
```

然后下载 `release-artifacts/deskflow-release-keystore-backup.tar.gz` 到你本地，用密码管理库（1Password / Bitwarden / 离线 USB）保存。**千万不要提交到 git，不要上传到公共网盘。**

解压后包含：

```
deskflow-release.jks          ← keystore 本体（唯一权威）
deskflow-release-key.b64     ← base64 编码，GitHub Secret 粘贴用
secrets.env                   ← 三个 secrets 的名字 + 值
```

## Step 2：配置 GitHub Actions Secrets

1. 打开 https://github.com/myisen/deskflow-android/settings/secrets/actions
2. 点 **New repository secret**
3. 依次添加三个 secrets（值从 Step 1 备份包解压后的 `deskflow-release-key.b64` 和 `secrets.env` 里取）：

| Secret Name | Value Source | 说明 |
|---|---|---|
| `SIGNING_KEY` | `cat deskflow-release-key.b64` 的全文 | keystore 的 base64 编码 |
| `SIGNING_KEY_ALIAS` | `secrets.env` 里的 `SIGNING_KEY_ALIAS` | 当前是 `release` |
| `SIGNING_KEY_PASSWORD` | `secrets.env` 里的 `SIGNING_KEY_PASSWORD` | 当前是 `Deskflow2026!` |

三个 secrets **全部配好** 后，CI workflow 的 Sign APK step 会检测到它们非空，走正式签名分支（而不是生成临时 debug keystore）。

## Step 3：触发 Release

三个 secrets 都配好后，push 一个新 tag：

```bash
git tag -a v1.0.7 -m "Official signed release"
git push origin v1.0.7
```

GitHub Actions Release workflow 会：
1. `./gradlew clean assembleRelease`（CI 无 local.properties → 产出 unsigned APK）
2. Sign step 检测到 secrets 都非空 → 用 `echo "$SIGNING_KEY" | base64 -d > /tmp/release.jks` 还原 keystore
3. `apksigner sign --ks /tmp/release.jks ...` 签出正式 APK
4. `apksigner verify` 通过后上传到 Release

下载后执行：

```bash
/opt/android-sdk/platform-tools/adb install -r DeskflowAndroid-v1.0.7.apk
```

**Signer 证书应该是**：
```
CN=Deskflow Android Official
SHA-256: 12741575d780c78fb20711c230dca3ed9b0f8d8bee72088eb860f8069ef896d4
```

和本地构建的 [DeskflowAndroid-official-release.apk](file:///workspace/release-artifacts/DeskflowAndroid-official-release.apk) 证书完全一致。

## 本地开发签名

本地开发机上，把以下内容放进 `local.properties`（已加入 `.gitignore`，不会被提交）：

```properties
sdk.dir=/path/to/android-sdk

# Release signing (local only)
RELEASE_KEYSTORE=/absolute/path/to/deskflow-release.jks
RELEASE_KEY_ALIAS=release
RELEASE_STORE_PASSWORD=Deskflow2026!
RELEASE_KEY_PASSWORD=Deskflow2026!
```

之后 `./gradlew assembleRelease` 产出的 APK 自动用正式 keystore 签名。没有 `local.properties` 时（比如 CI），Gradle 产出 unsigned APK，由 workflow shell 的 apksigner 二次签。

## 签名策略

```
本地机器                    CI Runner
─────────────────────────────────────────────────────────────
有 local.properties        有 GitHub Secrets
 → Gradle 直接签名           → workflow shell 还原 keystore
 → 正式签名                 → apksigner sign → 正式签名
                           → 上传 Release

无 local.properties        无 Secrets（当前 v1.0.5 v1.0.6）
 → Gradle 产出 unsigned      → keytool 生成临时 debug keystore
 → 通常不走这条路径          → apksigner sign → debug 签名
                             → 能装但非正式
```

## ⚠️ 安全红线

- **keystore 丢了就再也不能更新同包名应用**（Google Play 会拒签）
- 当前这个 keystore 生成在 `/root/.android-signing/deskflow-release.jks`，**沙箱重启会丢**
- 已经备份到 `release-artifacts/deskflow-release-keystore-backup.tar.gz`，**必须自己下载到安全位置**
- **不要**把 keystore 提交到 git，不要上传到公共网盘，不要通过 IM 发送明文
- 后续更新密码或 alias 时，**同时更新本地 keystore + GitHub Secrets + 备份包**
