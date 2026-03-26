#!/usr/bin/env bash
# Build WebGL from DiceGame-1.2 via Unity batch mode, then copy into public/game/.
#
#   ./build-webgl-and-copy.sh
#   UNITY_EDITOR="/path/to/Unity.app/Contents/MacOS/Unity" ./build-webgl-and-copy.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT="$REPO_ROOT/unity_code-3/DiceGame-main 6/DiceGame-1.2/DiceGame"

if [ ! -d "$PROJECT/Assets" ]; then
  echo "Unity project not found: $PROJECT"
  exit 1
fi

# Unity’s Mac binary is not always +x in every context; -f is enough.
unity_ok() { [ -n "$1" ] && [ -f "$1" ]; }

find_unity_in_hub() {
  local hub="/Applications/Unity/Hub/Editor"
  [ -d "$hub" ] || return 1
  local d u best=""
  # Prefer ProjectSettings version (6000.3.8f1), else newest-looking folder name
  local want=""
  want="$(grep -E '^m_EditorVersion:' "$PROJECT/ProjectSettings/ProjectVersion.txt" 2>/dev/null | head -1 | awk '{print $2}' | tr -d '\r')"
  if [ -n "$want" ] && unity_ok "$hub/$want/Unity.app/Contents/MacOS/Unity"; then
    echo "$hub/$want/Unity.app/Contents/MacOS/Unity"
    return 0
  fi
  for d in "$hub"/*; do
    [ -d "$d" ] || continue
    u="$d/Unity.app/Contents/MacOS/Unity"
    if unity_ok "$u"; then
      best="$u"
    fi
  done
  [ -n "$best" ] && echo "$best" && return 0
  return 1
}

UNITY="${UNITY_EDITOR:-}"
if unity_ok "$UNITY"; then
  :
elif UNITY="$(find_unity_in_hub)"; then
  :
else
  echo "Unity editor binary not found."
  if [ -n "${UNITY_EDITOR:-}" ]; then
    echo "  UNITY_EDITOR is set but file missing or not a file:"
    echo "  $UNITY_EDITOR"
  fi
  echo ""
  echo "Install Unity Hub → Installs → Add 6000.3.8f1 (or your version) + WebGL module."
  echo "Typical path:"
  echo "  /Applications/Unity/Hub/Editor/6000.3.8f1/Unity.app/Contents/MacOS/Unity"
  echo ""
  if [ -d "/Applications/Unity/Hub/Editor" ]; then
    echo "Folders under Hub (look for Unity.app inside):"
    ls -1 /Applications/Unity/Hub/Editor 2>/dev/null || true
  else
    echo "No /Applications/Unity/Hub/Editor — Unity Hub may not be installed, or Editor lives elsewhere."
    echo "Find Unity.app, then set:"
    echo "  export UNITY_EDITOR=\"/path/to/Unity.app/Contents/MacOS/Unity\""
  fi
  echo ""
  echo "Or build WebGL in the Editor (output: DiceGame/Builds/WebGL), then:"
  echo "  ./copy-webgl-build.sh"
  exit 1
fi

echo "Using Unity: $UNITY"
echo "Project: $PROJECT"
echo "Building WebGL (batchmode) — this may take several minutes..."
"$UNITY" -batchmode -quit -nographics \
  -projectPath "$PROJECT" \
  -executeMethod BuildWebGLForWebsite.Build \
  -logFile "$SCRIPT_DIR/unity-webgl-build.log"

echo "Unity log: $SCRIPT_DIR/unity-webgl-build.log"
"$SCRIPT_DIR/copy-webgl-build.sh"
echo "WebGL is in website/public/game/"
