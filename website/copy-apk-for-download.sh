#!/usr/bin/env bash
# Copy the latest Kotlin+Unity APK into website/public as PG-Management.apk (download name matches branding).
# Default source: sikwin gunduata debug build (build first: assembleGunduataDebug, often from /tmp if path has ':').
# Override: APK_SRC="/path/to/PGManagement-gunduata-release.apk" ./copy-apk-for-download.sh
#
#   ./copy-apk-for-download.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_SRC="${APK_SRC:-${REPO_ROOT}/sikwin/app/build/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk}"
if [ ! -f "$APK_SRC" ] && [ -f /tmp/sikwin-builds/app/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk ]; then
  APK_SRC=/tmp/sikwin-builds/app/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk
fi
# Fallback: Gradle may emit a different archivesBaseName; pick newest gunduata debug APK.
if [ ! -f "$APK_SRC" ]; then
  APK_SRC="$(ls -t "${REPO_ROOT}"/sikwin/app/build/outputs/apk/gunduata/debug/*.apk 2>/dev/null | head -1)"
fi
APK_DST="${SCRIPT_DIR}/public/GunduAta.apk"

if [ ! -f "$APK_SRC" ]; then
  echo "APK not found: $APK_SRC"
  echo "Build Sikwin: rsync sikwin to /tmp and ./gradlew :app:assembleDebug, or set APK_SRC=..."
  exit 1
fi
cp "$APK_SRC" "$APK_DST"
echo "Copied APK to $APK_DST ($(du -h "$APK_DST" | cut -f1))"
