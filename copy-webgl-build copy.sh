#!/usr/bin/env bash
# Copies the Unity WebGL build into the website so the game loads at /game/index.html.
# Run from repo root (Sikwin copy) or from website/.

set -e
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
# If we're in website/, run from here; else use website/copy-webgl-build.sh
if [ -f "$REPO_ROOT/website/copy-webgl-build.sh" ]; then
  exec "$REPO_ROOT/website/copy-webgl-build.sh"
fi

SRC="$REPO_ROOT/web_gl/DiceGame/Builds/WebGL"
DEST="$REPO_ROOT/website/public/game"

if [ ! -d "$SRC" ]; then
  echo "WebGL build not found at: $SRC"
  echo "Build the DiceGame project in Unity (File → Build Settings → WebGL → Build) first."
  exit 1
fi

echo "Copying WebGL build to $DEST ..."
rm -rf "$DEST"
mkdir -p "$DEST"
cp -R "$SRC"/* "$DEST"/
echo "Done. The game will be available at /game/index.html when you run the website."
exit 0
