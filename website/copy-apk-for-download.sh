#!/usr/bin/env bash
# Copy the latest Kotlin+Unity APK into website/public as GunduAta.apk.
# Prefers the /tmp flash-build output (assembleGunduataDebug -I /tmp/flash-build.gradle).
#
#   ./copy-apk-for-download.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_DST="${SCRIPT_DIR}/public/GunduAta.apk"

pick_newest_apk() {
  local candidates=()
  [ -f /tmp/sikwin-builds/app/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk ] && \
    candidates+=("/tmp/sikwin-builds/app/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk")
  [ -f "${REPO_ROOT}/sikwin/app/build/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk" ] && \
    candidates+=("${REPO_ROOT}/sikwin/app/build/outputs/apk/gunduata/debug/PGManagement-gunduata-debug.apk")
  local newest=""
  local newest_mtime=0
  for f in "${candidates[@]}"; do
    local mtime
    mtime="$(stat -f %m "$f" 2>/dev/null || stat -c %Y "$f")"
    if [ "$mtime" -gt "$newest_mtime" ]; then
      newest_mtime="$mtime"
      newest="$f"
    fi
  done
  if [ -z "$newest" ]; then
    newest="$(ls -t "${REPO_ROOT}"/sikwin/app/build/outputs/apk/gunduata/debug/*.apk 2>/dev/null | head -1 || true)"
  fi
  echo "$newest"
}

APK_SRC="${APK_SRC:-$(pick_newest_apk)}"

if [ -z "$APK_SRC" ] || [ ! -f "$APK_SRC" ]; then
  echo "APK not found."
  echo "Build: cd sikwin && ./gradlew :app:assembleGunduataDebug -I /tmp/flash-build.gradle"
  exit 1
fi

cp "$APK_SRC" "$APK_DST"
if [ -d "${SCRIPT_DIR}/dist" ]; then
  cp "$APK_SRC" "${SCRIPT_DIR}/dist/GunduAta.apk"
fi
echo "Copied APK to $APK_DST ($(du -h "$APK_DST" | cut -f1)) from $APK_SRC"
