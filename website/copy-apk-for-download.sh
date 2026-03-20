#!/usr/bin/env bash
# Copy the built sikwin APK into website/public so "Download APK" on the site serves it.
# Run after building the Android app, and before deploying the website.
#
#   ./copy-apk-for-download.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_SRC="${REPO_ROOT}/sikwin/app/build/outputs/apk/sikwin/debug/Sai-sikwin-debug.apk"
APK_DST="${SCRIPT_DIR}/public/gundu-ata.apk"

if [ ! -f "$APK_SRC" ]; then
  echo "APK not found. Build it first: cd sikwin && ./gradlew assembleSikwinDebug"
  exit 1
fi
cp "$APK_SRC" "$APK_DST"
echo "Copied APK to $APK_DST ($(du -h "$APK_DST" | cut -f1))"
