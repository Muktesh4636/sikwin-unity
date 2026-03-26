#!/usr/bin/env bash
# Copies the Unity WebGL build from unity/DiceGame-1.1 into website/public/game
# so the game is served at /game/index.html. Run after building the WebGL project in Unity.
#
# Optional: --use-stable   Copy from public/game-webgl-stable/ to public/game/ instead of
#                          from Unity. Use this to restore the preserved "perfect" WebGL build
#                          without overwriting it with a new Unity build.

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$SCRIPT_DIR/public/game"

if [ "${1:-}" = "--use-stable" ]; then
  STABLE="$SCRIPT_DIR/public/game-webgl-stable"
  if [ ! -d "$STABLE/Build" ] || [ ! -f "$STABLE/Build/WebGL.loader.js" ]; then
    echo "Stable WebGL build not found at: $STABLE"
    exit 1
  fi
  echo "Restoring WebGL from stable copy to $DEST ..."
  mkdir -p "$DEST"
  rm -rf "$DEST/Build" "$DEST/TemplateData"
  cp -R "$STABLE/Build" "$STABLE/TemplateData" "$DEST/"
  [ -f "$STABLE/index.html" ] && cp "$STABLE/index.html" "$DEST/"
  echo "Done. The game at /game/ is now using the stable WebGL build."
  exit 0
fi

# Default: copy from Unity build
SRC="$REPO_ROOT/unity/DiceGame-1.1/DiceGame/Builds/WebGL"
if [ ! -d "$SRC" ]; then
  echo "WebGL build not found at: $SRC"
  echo "Open unity/DiceGame-1.1 in Unity, then File → Build Settings → WebGL → Build (choose Builds/WebGL as output)."
  echo "To use the preserved stable build instead, run: $0 --use-stable"
  exit 1
fi

echo "Copying WebGL build from Unity to $DEST ..."
mkdir -p "$DEST"

# Unity build must have WebGL.loader.js or the game won't load in browser
if [ -f "$SRC/Build/WebGL.loader.js" ]; then
  rm -rf "$DEST/Build"
  cp -R "$SRC/Build" "$DEST/"
  echo "Copied Build/ from Unity build."
else
  FALLBACK_BUILD="$REPO_ROOT/web_gl/DiceGame/Builds/WebGL/Build"
  if [ -d "$FALLBACK_BUILD" ] && [ -f "$FALLBACK_BUILD/WebGL.loader.js" ]; then
    rm -rf "$DEST/Build"
    cp -R "$FALLBACK_BUILD" "$DEST/"
    echo "Unity Build/ missing loader; copied Build/ from web_gl (fallback)."
  else
    echo "Error: No valid Build (need WebGL.loader.js). Build WebGL in Unity completely, or ensure web_gl has a full build."
    exit 1
  fi
fi

if [ -d "$SRC/TemplateData" ]; then
  rm -rf "$DEST/TemplateData"
  cp -R "$SRC/TemplateData" "$DEST/"
  echo "Copied TemplateData/ from Unity build."
else
  FALLBACK_TEMPLATE="$REPO_ROOT/web_gl/DiceGame/Builds/WebGL/TemplateData"
  if [ -d "$FALLBACK_TEMPLATE" ]; then
    rm -rf "$DEST/TemplateData"
    cp -R "$FALLBACK_TEMPLATE" "$DEST/"
    echo "Copied TemplateData/ from web_gl (fallback)."
  else
    echo "Warning: No TemplateData in build and no web_gl fallback. Keep existing TemplateData if present."
  fi
fi
echo "Done. The game is at /game/index.html (custom index passes auth tokens to Unity)."
exit 0
