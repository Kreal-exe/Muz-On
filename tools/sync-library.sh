#!/bin/bash
# Keeps the phone and Music on the Mac in step, both ways. Nothing else is touched.
#  1. What was changed on the phone is applied in Music: hearts (Music then updates its smart
#     playlists by its own rules), playlists made on the phone, and songs added, removed or moved,
#     names changed and playlists deleted in regular playlists (tools/apply-changes.js).
#     A heart set on a song the phone knew only as a file is found in Music by its path.
#  2. Songs of the Mac library whose files the phone does not have yet are copied to the phone, into
#     the same folders (~/Music/<path> -> /sdcard/Music/<path>), and handed to its media library.
#     Nothing on the phone is deleted or overwritten. MUZON_NO_FILES=1 skips this step.
#     Folders listed in sync-exceptions.txt (beside made-playlists.txt, see EXCEPTIONS) are never
#     copied: one folder under Music/ per line, # for notes.
#  3. The library database, the exact playlist order and the list of applied changes go to the
#     phone, which then shows Music's playlists as they now are.
# Usage: tools/sync-library.sh [adb-serial]   (MUZON_APP=dev.kreal.muzon.debug for a debug build)
set -euo pipefail
LIB="$HOME/Music/Music/Music Library.musiclibrary"
TARGET="/sdcard/Music/Music/Music Library.musiclibrary"
# MUZON_APP=dev.kreal.muzon.debug syncs a debug build instead.
APPDIR="/sdcard/Android/data/${MUZON_APP:-dev.kreal.muzon}/files"
STATE="$HOME/Library/Application Support/Muz On/made-playlists.txt"
EXCEPTIONS="$HOME/Library/Application Support/Muz On/sync-exceptions.txt"
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

if [ "${MUZON_NO_FILES:-0}" != 1 ]; then
  open -ga Music
  "${ADB[@]}" exec-out "find /sdcard/Music -type f 2>/dev/null" > "$TMP/phone-files.txt"
  osascript -l JavaScript "$HERE/missing-on-phone.js" "$TMP/phone-files.txt" "$EXCEPTIONS" > "$TMP/missing.txt"
  count=$(grep -c . "$TMP/missing.txt" || true)
  if [ "$count" -gt 0 ]; then
    echo "New songs for the phone: $count"
    : > "$TMP/scan.txt"
    n=0
    while IFS= read -r rel; do
      [ -z "$rel" ] && continue
      n=$((n + 1))
      echo "  [$n/$count] $rel"
      if "${ADB[@]}" push -q "$HOME/Music/$rel" "/sdcard/Music/$rel" </dev/null >/dev/null; then
        printf '%s\0' "/sdcard/Music/$rel" >> "$TMP/scan.txt"
      else
        echo "    not copied" >&2
      fi
    done < "$TMP/missing.txt"
    # Hand the new files to the phone's media library, so players see them at once.
    if [ -s "$TMP/scan.txt" ]; then
      "${ADB[@]}" push -q "$TMP/scan.txt" /data/local/tmp/muzon-scan.txt >/dev/null
      "${ADB[@]}" exec-out "xargs -0 -P 4 -n 1 sh -c 'content call --uri content://media/external --method scan_file --arg \"\$0\" >/dev/null 2>&1' < /data/local/tmp/muzon-scan.txt; rm -f /data/local/tmp/muzon-scan.txt" || true
    fi
  else
    echo "The phone has every song of the library."
  fi
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
