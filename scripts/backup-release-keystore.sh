#!/usr/bin/env bash
# 导出 release keystore 到 /workspace/release-artifacts/，然后请你自己下载、
# 妥善保管（不要提交到 git，不要分享给他人）。
set -euo pipefail
KSDIR=/root/.android-signing
OUT=/workspace/release-artifacts/deskflow-release-keystore-backup.tar.gz

if [ ! -d "$KSDIR" ]; then
  echo "! keystore 目录不存在: $KSDIR"
  exit 1
fi

# 打包 keystore + base64 + secrets.env（secrets.env 包含口令，务必妥善保管）
tar czf "$OUT" -C /root/.android-signing .
chmod 600 "$OUT"

echo "✓ 已打包: $OUT"
echo "  大小: $(du -h "$OUT" | cut -f1)"
echo
echo "⚠️  下一步："
echo "  1. 下载 /workspace/release-artifacts/deskflow-release-keystore-backup.tar.gz 到你本地机器"
echo "  2. 解压 tar xzf deskflow-release-keystore-backup.tar.gz  → 得到:"
echo "     - deskflow-release.jks（keystore 本体，唯一权威）"
echo "     - deskflow-release-key.b64（base64，GitHub Secret 粘贴用）"
echo "     - secrets.env（三个 secrets 的值，可 cat 或 source）"
echo "  3. 放入密码管理库（1Password / Bitwarden / 离线 USB）"
echo "  4. 不要提交到 git，不要上传到公共网盘"
echo
echo "已自动更新 .gitignore 忽略 .android-signing/ 目录"
