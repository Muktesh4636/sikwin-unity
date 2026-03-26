#!/usr/bin/env bash
# Serves the WebGL build so you can open it in a browser.
# Run after building: Builds/WebGL must exist.

set -e
BUILD_DIR="$(cd "$(dirname "$0")" && pwd)/Builds/WebGL"
if [ ! -d "$BUILD_DIR" ]; then
  echo "No WebGL build found at: $BUILD_DIR"
  echo "Build first: Unity Editor > File > Build Settings > WebGL > Build (or Build and Run)."
  exit 1
fi
cd "$BUILD_DIR"
PORT="${1:-8080}"
echo "Serving WebGL build at http://localhost:$PORT"
echo "Open that URL in your browser (use Ctrl+C to stop)."
echo "If port $PORT is in use, run: $0 8081"
python3 -m http.server "$PORT"
