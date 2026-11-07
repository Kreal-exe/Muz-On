# iTunes library rewriter

`itl.py` reads an iTunes for Windows library file (`iTunes Library.itl`, made with iTunes
12.13.10.3) and writes a new one whose songs and playlists are those of the Music library on the
Mac (`Library.musicdb`). Python 3 standard library only; AES goes through the `openssl` command.

```sh
# A summary: tracks, albums, artists, playlists with item counts, a few tracks.
python3 tools/itunes/itl.py read "iTunes Library.itl" [--tracks 10] [--items]

# A new library. The template and the Music database are only read.
cp ~/Music/Music/"Music Library.musiclibrary"/Library.musicdb /tmp/Library.musicdb
python3 tools/itunes/itl.py write --template "iTunes Library.itl" --musicdb /tmp/Library.musicdb \
    [--order playlist-order.txt] [--path-map '/Users/admin/Music=D:\Music'] [--flatten-smart] \
    --out "new/iTunes Library.itl"

# Compare a written file with the Music library: counts, ids, fields, playlist orders.
python3 tools/itunes/itl.py verify "new/iTunes Library.itl" --musicdb /tmp/Library.musicdb [--order playlist-order.txt]
```

`--order` takes the file written by `tools/playlist-order.applescript` (Music must be running),
which has the exact order Music shows; without it the database's order is used. An order that no
longer fits a playlist (other tracks) is ignored for that playlist.

`--path-map MAC=WINDOWS` (repeatable) turns the Mac file paths into the ones iTunes on Windows
uses. Without it the mapping is derived from files known to both libraries; for this library it
is `/Users/admin/Music=D:\Music`, so `/Users/admin/Music/ЛСП/x.mp3` becomes `D:\Music\ЛСП\x.mp3`
and `file://localhost/D:/Music/%D0%9B%D0%A1%D0%9F/x.mp3`.

## What is written

- Every track of the Music library, with its persistent id: title, artist, album artist, album,
  genre, composer, comments, sort fields and the other text fields Music has, year, track and
  disc number and count, duration, date added, play count, loved/disliked, compilation, file
  type, bit rate, sample rate, file size and date modified (read from the file on this Mac), file
  location and URL.
- The album and artist lists iTunes links tracks to; entries of the old library that still fit
  are kept with their artwork and flags.
- The built-in playlists of the old library, kept as they were, with the members of Music's
  playlist of the same kind (Library, Music, Downloaded, ...) or none (Podcasts, Movies, ...).
- Every user playlist of the Music library, with Music's persistent id and its tracks in order.
  All six here are smart: their rules are copied (iTunes and Music store them in the same
  "SLst" form) and so are their settings (live updating, limits; turned from little- to
  big-endian), and they also hold Music's current members. `--flatten-smart` writes them as
  regular playlists holding those members instead. Their view settings (columns, sort) come
  from the old playlist of the same name or rules, or another old smart playlist.
- Everything else in the old file is copied unchanged: library persistent id, preferences,
  Genius data, play history, the master playlist's settings and the sections not understood.
  The body is compressed and encrypted as iTunes does it (zlib level 1, AES-128-ECB over the
  first 102400 bytes); the file length and the track, playlist, album and artist counts in the
  header are updated. Writing the old file back unchanged gives the identical file.

## Limits

- Not tried in iTunes: it does not run on the Mac. Everything above was checked by reading the
  written file back, nothing more.
- Track fields not understood (gapless data, Sound Check, artwork links and similar) are left
  empty for iTunes to fill in; ratings and last played dates are not carried over.
- Smart playlists: iTunes evaluates the rules itself and shows the tracks in the order of the
  playlist's sort column, so the exact order applies on its own only to regular playlists
  (`--flatten-smart`).
- Playlist folders, podcasts, videos and books are not handled (the Music library has none).
- The play history (Up Next, recently played) still names the old library's tracks.

## Trying it in iTunes on Windows

1. Quit iTunes. Keep a copy of the whole `Music\iTunes` folder, at least `iTunes Library.itl`.
2. Make a new folder, e.g. `Music\iTunes New`, and put the written file there named
   `iTunes Library.itl`.
3. Hold Shift while starting iTunes, choose "Choose Library…" and pick that file.
4. Check the track count, that songs play (the files must be under `D:\Music` or wherever the
   path map points) and the six playlists.
5. To go back, start iTunes with Shift again and choose the old library.
