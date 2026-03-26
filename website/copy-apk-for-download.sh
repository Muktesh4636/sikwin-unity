#!/usr/bin/env bash
# Copy the APK users get from the site (Chrome → /gundu-ata.apk → saves as GunduAta.apk).
#
# Priority (Kotlin + Unity app first — what you want for "full" Gundu Ata):
#   1. PREBUILT_APK if set
#   2. sikwin Gradle debug: Gunduata-debug.apk (current name)
#   3. sikwin Gradle debug: Sikwin-debug.apk (legacy filename after older builds)
#   4. Standalone Unity-only: unity_code-3/.../Gundu Ata v1.2.apk (fallback)
#
# After changing Kotlin/Unity, rebuild and run this before deploy:
#   cd sikwin && … assembleSikwinDebug
#   cd website && ./copy-apk-for-download.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_DST="${SCRIPT_DIR}/public/gundu-ata.apk"

UNITY_ONLY="${REPO_ROOT}/unity_code-3/DiceGame-main 6/Gundu Ata v1.2.apk"
GUNDUATA_DEBUG="${REPO_ROOT}/sikwin/app/build/outputs/apk/sikwin/debug/Gunduata-debug.apk"
SIKWIN_LEGACY="${REPO_ROOT}/sikwin/app/build/outputs/apk/sikwin/debug/Sikwin-debug.apk"

pick_src() {
  if [ -n "${PREBUILT_APK:-}" ] && [ -f "$PREBUILT_APK" ]; then
    echo "$PREBUILT_APK"
    return 0
  fi
  if [ -f "$GUNDUATA_DEBUG" ]; then
    echo "$GUNDUATA_DEBUG"
    return 0
  fi
  if [ -f "$SIKWIN_LEGACY" ]; then
    echo "$SIKWIN_LEGACY"
    return 0
  fi
  if [ -f "$UNITY_ONLY" ]; then
    echo "$UNITY_ONLY"
    return 0
  fi
  return 1
}

if ! APK_SRC="$(pick_src)"; then
  echo "No APK found. Build Kotlin+Unity first (recommended):"
  echo "  cp -a sikwin /tmp/SikwinKotlinUnity   # if path has ':'"
  echo "  cd /tmp/SikwinKotlinUnity && java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleSikwinDebug"
  echo "  cp /tmp/SikwinKotlinUnity/app/build/outputs/apk/sikwin/debug/Gunduata-debug.apk \\"
  echo "     $REPO_ROOT/sikwin/app/build/outputs/apk/sikwin/debug/"
  echo "Or set PREBUILT_APK=/path/to/your.apk"
  exit 1
fi

cp "$APK_SRC" "$APK_DST"
echo "Site download APK updated: $(basename "$APK_SRC") → public/gundu-ata.apk ($(du -h "$APK_DST" | cut -f1))"
echo "Browsers will fetch https://<your-domain>/gundu-ata.apk (HomePage saves as GunduAta.apk)."
