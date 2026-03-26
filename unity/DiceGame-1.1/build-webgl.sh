#!/usr/bin/env bash
# Build WebGL from command line using Unity batch mode.
# Close Unity Editor if you have this project open before running.

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_PATH="$SCRIPT_DIR/DiceGame"
BUILD_OUTPUT="$PROJECT_PATH/Builds/WebGL"
LOG_FILE="/tmp/unity_webgl_build.log"

# Unity path - change if your Hub version differs
if [ -x "/Applications/Unity/Hub/Editor/6000.3.8f1/Unity.app/Contents/MacOS/Unity" ]; then
  UNITY="/Applications/Unity/Hub/Editor/6000.3.8f1/Unity.app/Contents/MacOS/Unity"
elif [ -x "/Applications/Unity/Unity.app/Contents/MacOS/Unity" ]; then
  UNITY="/Applications/Unity/Unity.app/Contents/MacOS/Unity"
else
  # Try latest Hub editor
  LATEST=$(ls -d /Applications/Unity/Hub/Editor/*/Unity.app 2>/dev/null | tail -1)
  if [ -n "$LATEST" ] && [ -x "$LATEST/Contents/MacOS/Unity" ]; then
    UNITY="$LATEST/Contents/MacOS/Unity"
  else
    echo "Unity not found. Install Unity or set UNITY in this script to your Unity executable."
    exit 1
  fi
fi

echo "Project: $PROJECT_PATH"
# On macOS, building on external volumes (e.g. /Volumes/FLASH) creates ._* resource fork files that break
# IL2CPP and Emscripten. Build from a copy on local disk to avoid that.
BUILD_ROOT="/tmp/DiceGameWebGLBuild"
if [ -d "$BUILD_ROOT" ]; then rm -rf "$BUILD_ROOT"; fi
echo "Copying project to $BUILD_ROOT for build (avoids ._* issues on external volumes)..."
mkdir -p "$BUILD_ROOT"
cp -R "$SCRIPT_DIR/DiceGame" "$BUILD_ROOT/"
# Remove any ._ files that may have been copied
find "$BUILD_ROOT" -name '._*' -delete 2>/dev/null || true
PROJECT_PATH="$BUILD_ROOT/DiceGame"
BUILD_OUTPUT="$PROJECT_PATH/Builds/WebGL"

echo "Building WebGL (close Unity Editor if this project is open)..."
export COPYFILE_DISABLE=1
if ! "$UNITY" -quit -batchmode -projectPath "$PROJECT_PATH" -executeMethod BuildWebGL.BuildFromCommandLine -logFile "$LOG_FILE"; then
  if grep -q "another Unity instance" "$LOG_FILE" 2>/dev/null; then
    echo "Close the Unity Editor (this project open), then run this script again."
  else
    echo "Build failed. See $LOG_FILE"
    tail -80 "$LOG_FILE"
  fi
  exit 1
fi

if [ ! -d "$BUILD_OUTPUT/Build" ]; then
  echo "Build did not produce Builds/WebGL/Build."
  exit 1
fi

ORIGINAL_OUTPUT="$SCRIPT_DIR/DiceGame/Builds/WebGL"
mkdir -p "$SCRIPT_DIR/DiceGame/Builds"
rm -rf "$ORIGINAL_OUTPUT"
cp -R "$BUILD_OUTPUT" "$ORIGINAL_OUTPUT"
echo "WebGL build succeeded. Output copied to: $ORIGINAL_OUTPUT"
echo "Next: run from repo root:  website/copy-webgl-build.sh   to deploy to the website."
