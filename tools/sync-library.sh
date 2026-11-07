#!/bin/bash
# Copies the Mac music library's database and the exact playlist order to the phone.
# Nothing else is touched. Usage: tools/sync-library.sh [adb-serial]
set -euo pipefail
LIB="$HOME/Music/Music/Music Library.musiclibrary"
TARGET="/sdcard/Music/Music/Music Library.musiclibrary"
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB=(adb)
[ $# -ge 1 ] && ADB=(adb -s "$1")
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if pgrep -xq Music; then
  osascript "$HERE/playlist-order.applescript" > "$TMP/playlist-order.txt"
else
  echo "Music is not running: playlist-order.txt is left as it is." >&2
fi

"${ADB[@]}" shell mkdir -p "\"$TARGET\""
"${ADB[@]}" push "$LIB/Library.musicdb" "$TARGET/Library.musicdb"
[ -s "$TMP/playlist-order.txt" ] && "${ADB[@]}" push "$TMP/playlist-order.txt" "$TARGET/playlist-order.txt"
echo "Done."
