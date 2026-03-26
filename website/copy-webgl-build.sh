#!/usr/bin/env bash
# Copies a Unity WebGL build into website/public/game so the game is served at /game/index.html.
# Canonical Unity project: unity_code-3/DiceGame-main 6/DiceGame-1.2/DiceGame  (see that folder’s README.md)
# WebGL is built separately from the Android APK (Build Settings → WebGL in Unity).
#
# Before replacing Build/, the current /game/ tree is copied to public/game-webgl-previous/
# so you can roll back with:  ./copy-webgl-build.sh --restore-previous
# The long-term “known good” copy remains public/game-webgl-stable/ (use --use-stable to restore it).
#
# Source order (first folder that contains Build/WebGL.loader.js wins):
#   1. WEBGL_SRC env (absolute path, or repo-relative)
#   2. unity_code-3/.../DiceGame-1.2/DiceGame/Builds/WebGL  (default Unity output next to Assets)
#      or .../DiceGame-1.2/Builds/WebGL  (if you chose parent folder as build destination)
#   3. web_gl/DiceGame/Builds/WebGL
#   4. unity/DiceGame-1.1/DiceGame/Builds/WebGL
#
# Flags:
#   --use-stable          Restore from game-webgl-stable/ into /game/
#   --restore-previous    Restore from game-webgl-previous/ into /game/
#   --no-backup           Skip saving current /game/ before copy (not recommended)

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$SCRIPT_DIR/public/game"
BACKUP_DIR="$SCRIPT_DIR/public/game-webgl-previous"
STABLE_DIR="$SCRIPT_DIR/public/game-webgl-stable"

backup_current_game() {
  if [ ! -f "$DEST/Build/WebGL.loader.js" ]; then
    echo "No existing WebGL in $DEST — nothing to back up."
    return 0
  fi
  echo "Backing up current /game/ → game-webgl-previous/ ..."
  rm -rf "$BACKUP_DIR"
  mkdir -p "$BACKUP_DIR"
  cp -a "$DEST/." "$BACKUP_DIR/"
  echo "Rollback copy: $BACKUP_DIR ($(du -sh "$BACKUP_DIR" | cut -f1))"
}

# --- flags ---
NO_BACKUP=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --no-backup) NO_BACKUP=1 ;;
    *) ARGS+=("$a") ;;
  esac
done
set -- "${ARGS[@]}"

if [ "${1:-}" = "--use-stable" ]; then
  if [ ! -d "$STABLE_DIR/Build" ] || [ ! -f "$STABLE_DIR/Build/WebGL.loader.js" ]; then
    echo "Stable WebGL build not found at: $STABLE_DIR"
    exit 1
  fi
  if [ "$NO_BACKUP" != 1 ]; then
    backup_current_game
  fi
  echo "Restoring WebGL from game-webgl-stable/ → $DEST ..."
  mkdir -p "$DEST"
  rm -rf "$DEST/Build" "$DEST/TemplateData"
  cp -R "$STABLE_DIR/Build" "$STABLE_DIR/TemplateData" "$DEST/"
  [ -f "$STABLE_DIR/index.html" ] && cp "$STABLE_DIR/index.html" "$DEST/"
  echo "Done. /game/ is using game-webgl-stable."
  exit 0
fi

if [ "${1:-}" = "--restore-previous" ]; then
  if [ ! -f "$BACKUP_DIR/Build/WebGL.loader.js" ]; then
    echo "No backup found at $BACKUP_DIR (need Build/WebGL.loader.js)."
    exit 1
  fi
  echo "Restoring /game/ from game-webgl-previous/ ..."
  mkdir -p "$DEST"
  rm -rf "$DEST/Build" "$DEST/TemplateData"
  cp -R "$BACKUP_DIR/Build" "$BACKUP_DIR/TemplateData" "$DEST/"
  [ -f "$BACKUP_DIR/index.html" ] && cp "$BACKUP_DIR/index.html" "$DEST/"
  echo "Done. Rolled back to the copy that was live before the last deploy."
  exit 0
fi

pick_webgl_src() {
  local c
  for c in "$@"; do
    [ -z "$c" ] && continue
    if [ -f "$c/Build/WebGL.loader.js" ]; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

V12_INNER="$REPO_ROOT/unity_code-3/DiceGame-main 6/DiceGame-1.2/DiceGame/Builds/WebGL"
V12_PARENT="$REPO_ROOT/unity_code-3/DiceGame-main 6/DiceGame-1.2/Builds/WebGL"
WEBGL_REPO="$REPO_ROOT/web_gl/DiceGame/Builds/WebGL"
OLD="$REPO_ROOT/unity/DiceGame-1.1/DiceGame/Builds/WebGL"

if [ -n "${WEBGL_SRC:-}" ] && [[ "$WEBGL_SRC" != /* ]]; then
  WEBGL_SRC="$REPO_ROOT/$WEBGL_SRC"
fi

if ! SRC="$(pick_webgl_src "${WEBGL_SRC:-}" "$V12_INNER" "$V12_PARENT" "$WEBGL_REPO" "$OLD")"; then
  echo "No WebGL build found (need Build/WebGL.loader.js). Checked:"
  echo "  - WEBGL_SRC"
  echo "  - $V12_INNER"
  echo "  - $V12_PARENT"
  echo "  - $WEBGL_REPO"
  echo "  - $OLD"
  echo ""
  echo "Unity (DiceGame-1.2): File → Build Settings → WebGL → Build"
  echo "Suggested output:  DiceGame/Builds/WebGL  (inside DiceGame-1.2)"
  echo "Then run this script again. Current site /game/ was saved to game-webgl-previous/ if you ran a backup."
  echo "Rollback:  $0 --restore-previous"
  echo "Stable:    $0 --use-stable"
  exit 1
fi

echo "Using WebGL source: $SRC"

if [ "$NO_BACKUP" != 1 ]; then
  backup_current_game
fi

echo "Copying WebGL build to $DEST ..."
mkdir -p "$DEST"

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
    echo "Error: No valid Build (need WebGL.loader.js)."
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
    echo "Warning: No TemplateData in build and no web_gl fallback."
  fi
fi

# Addressables / loose files: Unity WebGL writes StreamingAssets/ next to Build/ (required or the game gets HTML from SPA fallback).
if [ -d "$SRC/StreamingAssets" ] && [ -n "$(ls -A "$SRC/StreamingAssets" 2>/dev/null)" ]; then
  rm -rf "$DEST/StreamingAssets"
  cp -R "$SRC/StreamingAssets" "$DEST/"
  echo "Copied StreamingAssets/ from Unity build."
else
  echo "Note: No StreamingAssets/ in Unity output (empty or absent). If the game still needs Addressables catalogs, build WebGL again and ensure Addressables build writes to StreamingAssets."
fi

echo "Done. Custom site shell stays in $DEST/index.html (not overwritten by Unity’s index)."
echo "Rollback last deploy:  $0 --restore-previous"
echo "Restore stable copy:   $0 --use-stable"
