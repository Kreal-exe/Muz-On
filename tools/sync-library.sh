#!/bin/bash
# Keeps the phone and the Mac library in step. Nothing else is touched.
#  1. Hearts set on the phone are applied in Music on the Mac (favourite on or off), which then
#     updates its smart playlists by its own rules.
#  2. The library database and the exact playlist order go to the phone.
# Usage: tools/sync-library.sh [adb-serial]
set -euo pipefail
LIB="$HOME/Music/Music/Music Library.musiclibrary"
TARGET="/sdcard/Music/Music/Music Library.musiclibrary"
CHANGES="/sdcard/Android/data/dev.kreal.muzon/files/favourites-changes.txt"
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB=(adb)
[ $# -ge 1 ] && ADB=(adb -s "$1")
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if "${ADB[@]}" pull "$CHANGES" "$TMP/changes.txt" >/dev/null 2>&1 && [ -s "$TMP/changes.txt" ]; then
  before=$(stat -f %m "$LIB/Library.musicdb")
  while read -r id on; do
    [ -z "$id" ] && continue
    osascript -e "tell application \"Music\" to set favorited of (first track of library playlist 1 whose persistent ID is \"$id\") to $on" >/dev/null
    echo "favourite $on: $id"
  done < "$TMP/changes.txt"
  "${ADB[@]}" shell rm -f "$CHANGES"
  # Music writes its database a little later; wait for it so the phone gets the new one.
  for _ in $(seq 1 30); do
    [ "$(stat -f %m "$LIB/Library.musicdb")" != "$before" ] && break
    sleep 1
  done
fi

if pgrep -xq Music; then
  osascript "$HERE/playlist-order.applescript" > "$TMP/playlist-order.txt"
else
  echo "Music is not running: playlist-order.txt is left as it is." >&2
fi

"${ADB[@]}" shell mkdir -p "\"$TARGET\""
"${ADB[@]}" push "$LIB/Library.musicdb" "$TARGET/Library.musicdb"
[ -s "$TMP/playlist-order.txt" ] && "${ADB[@]}" push "$TMP/playlist-order.txt" "$TARGET/playlist-order.txt"
echo "Done."
