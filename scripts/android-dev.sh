#!/usr/bin/env bash
# Deskflow Android 开发环境一键初始化脚本
# Usage:
#   source scripts/android-dev.sh
# 或：
#   bash scripts/android-dev.sh install-debug      # 构建并安装 debug APK
#   bash scripts/android-dev.sh rebuild-debug      # 仅重构建
#   bash scripts/android-dev.sh install-debug <apk> # 安装指定 APK

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------- 环境变量 ----------
export JAVA_HOME="${JAVA_HOME:-/root/.local/share/mise/installs/java/17.0.2}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH"

# Gradle 代理（沙箱内才有，本地机器可忽略）
if [ -n "${HTTP_PROXY:-}" ]; then
  export GRADLE_OPTS="-Dhttp.proxyHost=${HTTP_PROXY#*://} -Dhttp.proxyPort=${HTTP_PROXY##*:} \
                      -Dhttps.proxyHost=${HTTPS_PROXY#*://} -Dhttps.proxyPort=${HTTPS_PROXY##*:}"
fi

# ---------- 检查工具 ----------
check_tool() { command -v "$1" >/dev/null 2>&1; }

echo "✓ JDK : $(java -version 2>&1 | head -1)"
echo "✓ ADB : $(adb version 2>&1 | head -1)"
if check_tool apksigner; then
  echo "✓ apksigner: $(apksigner --version 2>&1 | head -1)"
fi

# ---------- 默认设备 ----------
if [ $# -eq 0 ]; then
  echo
  echo "用法:"
  echo "  source $0                       # 仅设置环境变量"
  echo "  bash   $0 rebuild-debug         # 构建 debug APK"
  echo "  bash   $0 install-debug [apk]  # 构建并安装"
  echo "  bash   $0 logcat                # 实时 logcat"
  echo "  bash   $0 verify <apk>          # 验证签名与 package info"
  exit 0
fi

cd "$PROJECT_ROOT"

# ---------- 子命令 ----------
case "$1" in
  rebuild-debug)
    echo "→ 构建 debug APK（仅 ARM，自动 debug 签名）"
    ./gradlew assembleDebug --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8" \
      2>&1 | tail -5
    APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    echo "✓ 产出: $APK ($(du -h "$APK" | cut -f1))"
    ;;

  rebuild-release)
    echo "→ 构建 release APK（未签名，需 apksigner 后才能安装）"
    ./gradlew assembleRelease --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m" \
      2>&1 | tail -5
    APK=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
    echo "✓ 产出: $APK ($(du -h "$APK" | cut -f1))"
    ;;

  install-debug)
    APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
    if [ ! -f "$APK" ]; then
      echo "APK 不存在，先构建…"
      bash "$0" rebuild-debug
    fi
    APK="${2:-$(find app/build/outputs/apk/debug -name '*.apk' | head -1)}"
    echo "→ adb install -r $APK"
    adb install -r "$APK"
    adb shell am start -n org.tfv.deskflow/.ui.activities.RootActivity
    ;;

  logcat)
    adb logcat -c
    adb logcat | grep -i "deskflow\|FATAL\|AndroidRuntime"
    ;;

  verify)
    APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
    [ ! -f "$APK" ] && { echo "APK 不存在: $APK"; exit 1; }
    echo "=== 签名 ==="
    apksigner verify --print-certs "$APK" 2>&1 | grep -E "Signer|SHA-256|MD5" | head -6
    echo "=== Package Info ==="
    aapt dump badging "$APK" | grep -E "package: name|sdkVersion|native-code|application-label"
    ;;

  *)
    echo "未知命令: $1"
    exit 1
    ;;
esac
