#!/usr/bin/env bash
# Copy the latest Kotlin+Unity APK into website/public as GunduAta.apk (download name matches branding).
# Default source: sikwin debug build (build first: assembleDebug, often from /tmp if path has ':').
# Override standalone Gundu Ata only: APK_SRC="/path/to/Gundu Ata.apk" ./copy-apk-for-download.sh
#
#   ./copy-apk-for-download.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_SRC="${APK_SRC:-${REPO_ROOT}/sikwin/app/build/outputs/apk/debug/Sikwin-debug.apk}"
APK_DST="${SCRIPT_DIR}/public/GunduAta.apk"

if [ ! -f "$APK_SRC" ]; then
  echo "APK not found: $APK_SRC"
  echo "Build Sikwin: rsync sikwin to /tmp and ./gradlew :app:assembleDebug, or set APK_SRC=..."
  exit 1
fi
cp "$APK_SRC" "$APK_DST"
echo "Copied APK to $APK_DST ($(du -h "$APK_DST" | cut -f1))"
