#!/bin/bash
# Stream testing: run this, then open Sikwin app and tap Gundu Ata. Stop with Ctrl+C when done.
# Logs are saved to sikwin_stream_test.log
set -e
DEVICE="${1:-}"
if [ -z "$DEVICE" ]; then
  DEVICE=$(adb devices | grep -E 'device$' | head -1 | awk '{print $1}')
fi
echo "Device: $DEVICE"
adb -s "$DEVICE" logcat -c
echo "Logcat cleared. Open Sikwin → tap Gundu Ata to open the game. Press Ctrl+C when done (or after freeze)."
LOG_FILE="$(cd "$(dirname "$0")" && pwd)/sikwin_stream_test.log"
adb -s "$DEVICE" logcat -v time '*:S' 'UnityPlayerGA:V' 'Unity:V' 'com.sikwin.app:V' 'AndroidRuntime:E' 'Choreographer:V' 2>&1 | tee "$LOG_FILE"
