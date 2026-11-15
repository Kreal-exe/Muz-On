#!/bin/bash
# Keeps the phone and Music on the Mac in step, both ways. Nothing else is touched.
#  1. What was changed on the phone is applied in Music: hearts (Music then updates its smart
#     playlists by its own rules), playlists made on the phone, and songs added, removed or moved,
#     names changed and playlists deleted in regular playlists (tools/apply-changes.js).
#  2. The library database, the exact playlist order and the list of applied changes go to the
#     phone, which then shows Music's playlists as they now are.
# Usage: tools/sync-library.sh [adb-serial]   (MUZON_APP=dev.kreal.muzon.debug for a debug build)
set -euo pipefail
LIB="$HOME/Music/Music/Music Library.musiclibrary"
TARGET="/sdcard/Music/Music/Music Library.musiclibrary"
# MUZON_APP=dev.kreal.muzon.debug syncs a debug build instead.
APPDIR="/sdcard/Android/data/${MUZON_APP:-dev.kreal.muzon}/files"
STATE="$HOME/Library/Application Support/Muz On/made-playlists.txt"
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB=(adb)
[ $# -ge 1 ] && ADB=(adb -s "$1")
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

applied=0
if "${ADB[@]}" pull "$APPDIR/library-changes.json" "$TMP/changes.json" >/dev/null 2>&1 && [ -s "$TMP/changes.json" ]; then
  open -ga Music
  before=$(stat -f %m "$LIB/Library.musicdb")
  osascript -l JavaScript "$HERE/apply-changes.js" "$TMP/changes.json" "$STATE" > "$TMP/applied.txt"
  "${ADB[@]}" shell rm -f "$APPDIR/library-changes.json"
  applied=1
  # Music writes its database a little later; wait for it so the phone gets the new one.
  for _ in $(seq 1 30); do
    [ "$(stat -f %m "$LIB/Library.musicdb")" != "$before" ] && break
    sleep 1
  done
  sleep 2
elif "${ADB[@]}" pull "$APPDIR/favourites-changes.txt" "$TMP/changes.txt" >/dev/null 2>&1 && [ -s "$TMP/changes.txt" ]; then
  # Hearts from Muz On 1.0.0.
  while read -r id on; do
    [ -z "$id" ] && continue
    osascript -e "tell application \"Music\" to set favorited of (first track of library playlist 1 whose persistent ID is \"$id\") to $on" >/dev/null
    echo "favourite $on: $id"
  done < "$TMP/changes.txt"
  "${ADB[@]}" shell rm -f "$APPDIR/favourites-changes.txt"
  sleep 5
fi

if pgrep -xq Music; then
  osascript "$HERE/playlist-order.applescript" > "$TMP/playlist-order.txt"
else
  echo "Music is not running: playlist-order.txt is left as it is." >&2
fi

"${ADB[@]}" shell mkdir -p "\"$TARGET\""
"${ADB[@]}" push "$LIB/Library.musicdb" "$TARGET/Library.musicdb"
[ -s "$TMP/playlist-order.txt" ] && "${ADB[@]}" push "$TMP/playlist-order.txt" "$TARGET/playlist-order.txt"
# Last, so the phone never reads it before the database that shows it. Always written, empty when
# nothing was applied, so a list from an earlier sync never lingers.
[ "$applied" = 1 ] || : > "$TMP/applied.txt"
"${ADB[@]}" push "$TMP/applied.txt" "$TARGET/muzon-applied.txt" >/dev/null
echo "Done."
