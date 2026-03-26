#!/usr/bin/env bash
# Copy Unity arm64 .so files and assets/bin/Data from a release APK into unityLibrary
# so the Kotlin app embeds the same game as that APK.
#
# Default APK: unity_code-3/DiceGame-main 6/Gundu Ata v1.2.apk
#
#   ./scripts/sync-unity-from-apk.sh
#   ./scripts/sync-unity-from-apk.sh /path/to/GunduAta.apk

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SIKWIN="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$SIKWIN/.." && pwd)"

APK="${1:-$REPO_ROOT/unity_code-3/DiceGame-main 6/Gundu Ata v1.2.apk}"
JNI_OUT="$SIKWIN/unityLibrary/src/main/jniLibs/arm64-v8a"
ASSETS_OUT="$SIKWIN/unityLibrary/src/main/assets/bin/Data"

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK"
  exit 1
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "Extracting from: $APK"
unzip -q -o "$APK" "lib/arm64-v8a/*" "assets/bin/Data/*" -d "$TMP"

mkdir -p "$JNI_OUT"
cp -f "$TMP/lib/arm64-v8a"/*.so "$JNI_OUT/"
echo "Updated jniLibs: $JNI_OUT"

rm -rf "$ASSETS_OUT"
mkdir -p "$ASSETS_OUT"
cp -a "$TMP/assets/bin/Data/." "$ASSETS_OUT/"
echo "Updated assets: $ASSETS_OUT ($(du -sh "$ASSETS_OUT" | cut -f1))"
echo "Done. Rebuild: cd sikwin && ./gradlew assembleSikwinDebug"
