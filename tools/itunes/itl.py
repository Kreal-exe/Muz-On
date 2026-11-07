#!/usr/bin/env python3
"""Reads and rewrites an iTunes for Windows library file (iTunes Library.itl, iTunes 12.x).

  itl.py read   <file.itl> [--tracks N] [--items]
  itl.py write  --template old.itl --musicdb Library.musicdb [--order playlist-order.txt]
                [--path-map /Users/me/Music=D:\\Music ...] [--flatten-smart] --out new.itl
  itl.py verify <file.itl> --musicdb Library.musicdb [--order playlist-order.txt]

File layout, as far as this tool needs it (see README.md):
  outer header "hdfm" (big-endian): header length, file length, ..., version string at 0x10,
  track count 0x44, playlist count 0x48, crypt size 0x5C. The body is AES-128-ECB over its first
  min(crypt size, body) bytes rounded down to 16, and the whole body is zlib (level 1).
  Inside, everything is little-endian with reversed tags: "msdh" sections, each holding a list
  header and records: tracks "mith" (section 1), playlists "miph" with items "mtph" (section 2),
  and "mhoh" children for strings and blobs. Sections this tool does not understand are copied.

Python 3 standard library only; AES runs through the openssl command line tool.
"""
import argparse
import collections
import hashlib
import os
import struct
import subprocess
import sys
import urllib.parse
import zlib

KEY = b"BHUILuilfghuila3"
MAC_EPOCH_OFFSET = 2082844800  # seconds from 1904-01-01 to 1970-01-01

# Offsets inside the outer (big-endian) and inner (little-endian) file headers.
H_FILE_LENGTH, H_TRACKS, H_PLAYLISTS, H_ALBUMS, H_ARTISTS, H_CRYPT = 0x08, 0x44, 0x48, 0x4C, 0x54, 0x5C

# Offsets inside a track record ("mith", 756-byte header).
T_ID, T_MODIFIED, T_SIZE, T_DURATION, T_TRACK, T_TRACKS, T_YEAR, T_BITRATE = 16, 32, 36, 40, 44, 48, 52, 56
T_PLAYS, T_COMPILATION, T_PLAYS2, T_DISC, T_DISCS, T_ADDED, T_PID = 76, 82, 96, 104, 106, 120, 128
T_FILETYPE, T_SAMPLERATE, T_ALBUM_ID, T_SIZE2, T_ARTIST_ID, T_ID2, T_LOVED = 140, 152, 220, 324, 480, 500, 703

# Album ("miah", section 9) and artist ("miih", section 11) entries: id at 16, persistent id at 20.
A_ID, A_PID, A_FLAGS = 16, 20, 28
S_ALBUM_NAME, S_ALBUM_ARTIST, S_ALBUM_ALBUM_ARTIST, S_ARTIST_NAME = 0x12C, 0x12D, 0x12E, 0x190

# Offsets inside a playlist record ("miph", 3500-byte header) and an item ("mtph").
P_ITEMS, P_MASTER, P_PID, P_SYNC_PID, P_KIND, P_ID = 16, 22, 440, 560, 568, 3392
I_ID, I_TRACK, I_KEY, I_PID = 16, 24, 32, 68

# mhoh types.
S_TITLE, S_URL, S_LOCATION = 0x02, 0x0B, 0x0D
S_PL_NAME, S_PL_RULES, S_PL_INFO = 0x64, 0x65, 0x66
STRING_TYPES = set(range(0x02, 0x40)) - {0x13, 0x36}  # these two hold blobs in a track
BLOB_TYPES_TRACK = {0x13, 0x36}


class FormatError(Exception):
    pass


def u32(b, o):
    return struct.unpack_from("<I", b, o)[0]


def put32(b, o, v):
    struct.pack_into("<I", b, o, v & 0xFFFFFFFF)


def pid_hex(b, o):
    return b[o:o + 8][::-1].hex().upper()


def pid_bytes(h):
    return bytes.fromhex(h)[::-1]


# ---------------------------------------------------------------- container

def aes_ecb(data, decrypt):
    if not data:
        return b""
    cmd = ["openssl", "enc", "-d" if decrypt else "-e", "-aes-128-ecb", "-nopad", "-K", KEY.hex()]
    out = subprocess.run(cmd, input=data, capture_output=True, check=True).stdout
    if len(out) != len(data):
        raise FormatError("openssl returned %d bytes for %d" % (len(out), len(data)))
    return out


def crypt_length(header, body_length, big_endian):
    size = struct.unpack_from(">I" if big_endian else "<I", header, H_CRYPT)[0]
    n = min(size, body_length)
    return n - n % 16


def open_container(raw):
    """Returns (header, inner, big_endian) for an .itl (hdfm) or .musicdb (hfma) file."""
    tag = raw[:4]
    if tag == b"hdfm":
        big = True
    elif tag == b"hfma":
        big = False
    else:
        raise FormatError("not an iTunes library (tag %r)" % tag)
    hl = struct.unpack_from(">I" if big else "<I", raw, 4)[0]
    header, body = raw[:hl], raw[hl:]
    if big:
        flen = struct.unpack_from(">I", header, H_FILE_LENGTH)[0]
        if flen != len(raw):
            raise FormatError("file length field %d, file has %d bytes" % (flen, len(raw)))
        n = crypt_length(header, len(body), True)
    else:
        n = min(u32(header, 84), len(body))
        n -= n % 16
    body = aes_ecb(body[:n], True) + body[n:]
    z = zlib.decompressobj()
    inner = z.decompress(body) + z.flush()
    if z.unused_data:
        raise FormatError("%d bytes after the compressed body" % len(z.unused_data))
    return header, inner, big


def seal_container(header, inner):
    """Compresses and encrypts [inner] like iTunes does, with the header's length fields updated."""
    header = bytearray(header)
    body = zlib.compress(inner, 1)
    n = crypt_length(header, len(body), True)
    body = aes_ecb(body[:n], False) + body[n:]
    struct.pack_into(">I", header, H_FILE_LENGTH, len(header) + len(body))
    return bytes(header) + body


# ---------------------------------------------------------------- inner format

class Section:
    def __init__(self, kind, data):
        self.kind, self.data = kind, data


def split_sections(inner):
    sections, o = [], 0
    while o < len(inner):
        if inner[o:o + 4] != b"msdh":
            raise FormatError("expected msdh at %d" % o)
        total = u32(inner, o + 8)
        if total <= 0 or o + total > len(inner):
            raise FormatError("bad section length at %d" % o)
        sections.append(Section(u32(inner, o + 12), inner[o:o + total]))
        o += total
    return sections


def read_mhoh(b, p):
    """Returns (type, value, raw bytes) of the mhoh at p. Strings decode to str, the rest to bytes."""
    if b[p:p + 4] != b"mhoh":
        raise FormatError("expected mhoh at %d" % p)
    total, kind = u32(b, p + 8), u32(b, p + 12)
    raw = b[p:p + total]
    return kind, raw


def mhoh_text(raw):
    enc, length = u32(raw, 24), u32(raw, 28)
    data = raw[40:40 + length]
    return data.decode("utf-16-le" if enc == 1 else "utf-8", "replace")


def make_text_mhoh(kind, text, string_id, enc=None):
    if enc is None:
        enc = 3 if text.isascii() else 1
    data = text.encode("utf-16-le" if enc == 1 else "utf-8")
    head = struct.pack("<4sIII IIII II", b"mhoh", 24, 40 + len(data), kind, string_id, 0, enc, len(data), 0, 0)
    return head + data


def make_blob_mhoh(kind, data):
    return struct.pack("<4sIII II", b"mhoh", 24, 24 + len(data), kind, 0, 0) + data


def iter_records(sec, record_tag, child_tags):
    """Yields (header bytes, [children raw]) for each record of a section."""
    b = sec.data
    p = u32(b, 4)                      # past msdh
    p += u32(b, p + 4)                 # past the list header
    cur = None
    while p < len(b):
        tag = b[p:p + 4]
        if tag == record_tag:
            if cur:
                yield cur
            cur = (b[p:p + u32(b, p + 4)], [])
            p += u32(b, p + 4)
        elif tag == b"mhoh":
            cur[1].append(b[p:p + u32(b, p + 8)])
            p += u32(b, p + 8)
        elif tag in child_tags:
            cur[1].append(b[p:p + u32(b, p + 4)])
            p += u32(b, p + 4)
        else:
            raise FormatError("unexpected %r at %d in section %d" % (tag, p, sec.kind))
    if cur:
        yield cur


def list_header(sec):
    b = sec.data
    p = u32(b, 4)
    return b[:p], b[p:p + u32(b, p + 4)]


def build_section(msdh, list_head, count, records):
    list_head = bytearray(list_head)
    put32(list_head, 8, count)
    body = b"".join(records)
    msdh = bytearray(msdh)
    put32(msdh, 8, len(msdh) + len(list_head) + len(body))
    return bytes(msdh) + bytes(list_head) + body


class Itl:
    def __init__(self, raw):
        self.header, self.inner, big = open_container(raw)
        if not big:
            raise FormatError("this is a Music database, not an .itl")
        self.sections = split_sections(self.inner)
        self.tracks = []      # dicts
        self.playlists = []   # dicts
        for sec in self.sections:
            if sec.kind == 1:
                for head, kids in iter_records(sec, b"mith", ()):
                    t = {"head": head, "kids": kids, "id": u32(head, T_ID), "pid": pid_hex(head, T_PID)}
                    for raw_kid in kids:
                        kind = u32(raw_kid, 12)
                        if kind in STRING_TYPES:
                            t[kind] = mhoh_text(raw_kid)
                    self.tracks.append(t)
            elif sec.kind == 2:
                for head, kids in iter_records(sec, b"miph", (b"mtph",)):
                    pl = {"head": head, "mhoh": [k for k in kids if k[:4] == b"mhoh"],
                          "items": [k for k in kids if k[:4] == b"mtph"],
                          "pid": pid_hex(head, P_PID), "master": head[P_MASTER] == 1,
                          "kind": struct.unpack_from("<H", head, P_KIND)[0], "id": u32(head, P_ID)}
                    pl["name"] = ""
                    for k in pl["mhoh"]:
                        if u32(k, 12) == S_PL_NAME:
                            pl["name"] = mhoh_text(k)
                    pl["smart"] = any(u32(k, 12) == S_PL_RULES for k in pl["mhoh"])
                    pl["track_ids"] = [u32(i, I_TRACK) for i in pl["items"]]
                    self.playlists.append(pl)

    def section(self, kind):
        return next(s for s in self.sections if s.kind == kind)

    def user_playlists(self):
        return [p for p in self.playlists if not p["master"] and p["kind"] == 0]

    def max_id(self):
        """Largest object id in use (tracks, items, playlists, albums, artists share one id space)."""
        best = 0
        for t in self.tracks:
            best = max(best, u32(t["head"], T_ID), u32(t["head"], T_ID2))
        for p in self.playlists:
            best = max(best, p["id"], *[u32(i, I_ID) for i in p["items"]] or [0])
        for kind, tag in ((9, b"miah"), (11, b"miih")):
            for s in self.sections:
                if s.kind == kind:
                    for head, _ in iter_records(s, tag, ()):
                        best = max(best, u32(head, 16))
        return best


# ---------------------------------------------------------------- Music library (Library.musicdb)

def read_musicdb(path):
    _, d, _ = open_container(open(path, "rb").read())
    tracks, playlists, o = [], [], 0
    while o + 16 <= len(d):
        if d[o:o + 4] != b"hsma":
            raise FormatError("expected hsma at %d in the Music database" % o)
        total, kind, start = u32(d, o + 8), u32(d, o + 12), o + u32(d, o + 4)
        if kind == 1:
            p = start + u32(d, start + 4)
            while p < o + total:
                if d[p:p + 4] != b"itma":
                    raise FormatError("expected itma at %d" % p)
                hl, tl = u32(d, p + 4), u32(d, p + 8)
                t = {"pid": pid_hex(d, p + 16), "strings": {}, "plays": 0,
                     "track": u32(d, p + 160), "tracks": u32(d, p + 116), "disc": u32(d, p + 84),
                     "discs": struct.unpack_from("<H", d, p + 90)[0], "year": u32(d, p + 168),
                     "loved": d[p + 62], "compilation": d[p + 64]}
                q = p + hl
                while q < p + tl:
                    size, bt = u32(d, q + 8), u32(d, q + 12)
                    if bt == 0x01:
                        b1 = q + 20
                        t["duration"] = u32(d, b1 + 156)
                        t["added"] = u32(d, b1 + 92)
                        t["samplerate"] = struct.unpack_from("<f", d, b1 + 60)[0]
                        t["filetype"] = u32(d, b1 + 64)
                        t["bitrate"] = u32(d, b1 + 88)
                    elif bt == 0x17:
                        t["plays"] = u32(d, q + 32)
                    elif bt in STRING_TYPES and size >= 36 and u32(d, q + 20) in (1, 2):
                        n = min(u32(d, q + 24), size - 36)
                        raw = d[q + 36:q + 36 + n]
                        t["strings"][bt] = raw.decode("utf-16-le" if u32(d, q + 20) == 1 else "utf-8", "replace")
                    q += size
                tracks.append(t)
                p += tl
        elif kind == 2:
            p = start + u32(d, start + 4)
            while p < o + total:
                if d[p:p + 4] != b"lpma":
                    raise FormatError("expected lpma at %d" % p)
                hl, tl = u32(d, p + 4), u32(d, p + 8)
                pl = {"pid": pid_hex(d, p + 30), "master": d[p + 44] != 0,
                      "kind": struct.unpack_from("<H", d, p + 78)[0], "name": "", "rules": None,
                      "info": None, "items": []}
                q = p + hl
                while q < p + tl:
                    size, bt = u32(d, q + 8), u32(d, q + 12)
                    if bt == 0xC8:
                        n = u32(d, q + 24)
                        pl["name"] = d[q + 36:q + 36 + n].decode("utf-16-le" if u32(d, q + 20) == 1 else "utf-8")
                    elif bt == 0xC9:
                        pl["rules"] = d[q + 20:q + size]
                    elif bt == 0xCA:
                        pl["info"] = d[q + 20:q + size]
                    elif bt == 0xCE:
                        pl["items"].append(pid_hex(d, q + 40))
                    q += size
                playlists.append(pl)
                p += tl
        o += total
    return tracks, playlists


def apply_order(playlists, text):
    """The order file of tools/playlist-order.applescript: '#<playlist id>' then its track ids."""
    orders, cur = {}, None
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("#"):
            cur = orders.setdefault(line[1:].upper(), [])
        elif cur is not None:
            cur.append(line.upper())
    used = []
    for pl in playlists:
        order = orders.get(pl["pid"])
        if order is not None and sorted(order) == sorted(pl["items"]):
            pl["items"] = order
            used.append(pl["name"])
        elif order is not None:
            print("order for %r does not fit the database (%d vs %d tracks): kept the database's"
                  % (pl["name"], len(order), len(pl["items"])), file=sys.stderr)
    return used


def user_musicdb_playlists(playlists):
    return [p for p in playlists if not p["master"] and p["kind"] == 0]


# ---------------------------------------------------------------- paths

def parse_path_maps(values):
    maps = []
    for v in values:
        if "=" not in v:
            raise SystemExit("--path-map wants MAC_PREFIX=WINDOWS_PREFIX, got %r" % v)
        mac, win = v.split("=", 1)
        maps.append((mac.rstrip("/"), win.rstrip("\\")))
    return sorted(maps, key=lambda m: -len(m[0]))


def mac_path(url):
    if not url.startswith("file://"):
        return None
    return urllib.parse.unquote(url[len("file://"):].removeprefix("localhost"))


def derive_path_map(itl, mdb_tracks):
    """Pairs files known to both libraries by their last two path parts and returns the most
    common (Mac prefix, Windows prefix)."""
    old = {}
    for t in itl.tracks:
        loc = t.get(S_LOCATION)
        if loc:
            parts = loc.split("\\")
            old.setdefault(tuple(x.lower() for x in parts[-2:]), parts)
    votes = collections.Counter()
    for t in mdb_tracks:
        p = mac_path(t["strings"].get(S_URL, ""))
        if not p:
            continue
        parts = p.split("/")
        w = old.get(tuple(x.lower() for x in parts[-2:]))
        if not w:
            continue
        i = 1
        while i < min(len(parts), len(w)) and parts[-i].lower() == w[-i].lower():
            i += 1
        # Keep the topmost shared folder in the prefix (/Users/me/Music = D:\\Music, not /Users/me = D:).
        if i > 3:
            i -= 1
        votes[("/".join(parts[:len(parts) - i + 1]), "\\".join(w[:len(w) - i + 1]))] += 1
    return votes


def to_windows(path, maps):
    for mac, win in maps:
        if path == mac or path.startswith(mac + "/"):
            return win + path[len(mac):].replace("/", "\\")
    return None


def windows_url(win):
    # As iTunes writes them: file://localhost/D:/Music/..., UTF-8 percent-encoded, these kept as is.
    return "file://localhost/" + urllib.parse.quote(win.replace("\\", "/"), safe="/:()$'&,@!+~=*")


# ---------------------------------------------------------------- writing

def neutral_track_headers(itl):
    """A track header per file type with only the bytes nearly all of the template's tracks share;
    anything that differs between tracks (gapless data, sound check, ...) is zero."""
    groups = collections.defaultdict(list)
    for t in itl.tracks:
        if u32(t["head"], 20) == 1:           # 1: an ordinary track, 2: a podcast episode
            groups[u32(t["head"], T_FILETYPE)].append(t["head"])
    everything = [h for g in groups.values() for h in g]
    if not everything:
        raise FormatError("the template has no ordinary tracks to copy a record from")

    def mode_of(heads):
        out = bytearray(len(heads[0]))
        for i in range(len(out)):
            value, n = collections.Counter(h[i] for h in heads).most_common(1)[0]
            out[i] = value if n >= 0.9 * len(heads) else 0
        return bytes(out)

    result = {ft: mode_of(h) for ft, h in groups.items() if len(h) >= 20}
    result[None] = mode_of(everything)
    return result


def album_key(album, artist, album_artist):
    # iTunes keeps "Unreleased" and "Unreleased " apart, so no trimming.
    return (album or "").lower(), (album_artist or artist or "").lower()


def artist_key(artist, album_artist):
    return (album_artist or artist or "").lower()


class Groups:
    """The album and artist lists. iTunes links every track to one entry of each (track header
    offsets 220 and 480). Entries of the template whose key is still used are kept as they are
    (their artwork and flags included); the others are dropped and new ones made."""

    def __init__(self, itl, ids):
        self.ids = ids
        self.old = {}
        for kind, tag in ((9, b"miah"), (11, b"miih")):
            entries = {}
            sec = next((x for x in itl.sections if x.kind == kind), None)
            if sec:
                for head, kids in iter_records(sec, tag, ()):
                    names = {u32(k, 12): mhoh_text(k) for k in kids if u32(k, 12) in
                             (S_ALBUM_NAME, S_ALBUM_ARTIST, S_ALBUM_ALBUM_ARTIST, S_ARTIST_NAME)}
                    if kind == 9:
                        key = album_key(names.get(S_ALBUM_NAME), names.get(S_ALBUM_ARTIST), names.get(S_ALBUM_ALBUM_ARTIST))
                    else:
                        key = artist_key(names.get(S_ARTIST_NAME), None)
                    entries.setdefault(key, head + b"".join(kids))
            self.old[kind] = entries
        self.used = {9: {}, 11: {}}
        self.string_ids = collections.defaultdict(dict)

    def _entry(self, kind, key, strings):
        used = self.used[kind]
        if key in used:
            return u32(used[key], A_ID)
        old = self.old[kind].get(key)
        if old is None:
            hl = 88 if kind == 9 else 100
            head = bytearray(hl)
            head[0:4] = b"miah" if kind == 9 else b"miih"
            put32(head, 4, hl)
            put32(head, A_FLAGS, 2)
            head[A_PID:A_PID + 8] = stable_pid("album", *key) if kind == 9 else stable_pid("artist", key)
            kids = []
            for t, text in strings:
                if text:
                    sid = self.string_ids[t].setdefault(text, len(self.string_ids[t]) + 1)
                    kids.append(make_text_mhoh(t, text, sid))
            put32(head, 12, len(kids))
            put32(head, 8, hl + sum(map(len, kids)))
            put32(head, A_ID, self.ids.take())
            old = bytes(head) + b"".join(kids)
        used[key] = old
        return u32(old, A_ID)

    def link(self, head, s):
        album, artist, album_artist = s.get(3, ""), s.get(4, ""), s.get(0x1B, "")
        put32(head, T_ALBUM_ID, self._entry(9, album_key(album, artist, album_artist), [
            (S_ALBUM_NAME, album), (S_ALBUM_ARTIST, artist), (S_ALBUM_ALBUM_ARTIST, album_artist)]))
        put32(head, T_ARTIST_ID, self._entry(11, artist_key(artist, album_artist), [
            (S_ARTIST_NAME, album_artist or artist)]))

    def records(self, kind):
        return list(self.used[kind].values())


class Ids:
    def __init__(self, start):
        self.next = start

    def take(self, n=1):
        v = self.next
        self.next += n
        return v


def stable_pid(*parts):
    return hashlib.sha1("|".join(parts).encode()).digest()[:8]


def build_track(t, neutral, ids, string_ids, maps, problems, groups):
    head = bytearray(neutral.get(t.get("filetype"), neutral[None]))
    tid = ids.take(2)
    put32(head, T_ID, tid)
    put32(head, T_ID2, tid + 1)
    groups.link(head, t["strings"])
    path = mac_path(t["strings"].get(S_URL, ""))
    size, modified = 0, 0
    if path and os.path.exists(path):
        st = os.stat(path)
        size, modified = st.st_size, int(st.st_mtime) + MAC_EPOCH_OFFSET
    elif path:
        problems["track file missing on this Mac (size left 0)"] += 1
    put32(head, T_MODIFIED, modified)
    put32(head, T_SIZE, size)
    put32(head, T_SIZE2, size)
    put32(head, T_DURATION, t.get("duration", 0))
    put32(head, T_TRACK, t["track"])
    put32(head, T_TRACKS, t["tracks"])
    put32(head, T_YEAR, t["year"])
    put32(head, T_BITRATE, t.get("bitrate", 0))
    put32(head, T_PLAYS, t["plays"])
    put32(head, T_PLAYS2, t["plays"])
    head[T_COMPILATION] = 1 if t["compilation"] == 1 else 0
    struct.pack_into("<HH", head, T_DISC, t["disc"] & 0xFFFF, t["discs"] & 0xFFFF)
    put32(head, T_ADDED, t.get("added", 0))
    head[T_PID:T_PID + 8] = pid_bytes(t["pid"])
    if t.get("filetype"):
        put32(head, T_FILETYPE, t["filetype"])
    if t.get("samplerate"):
        struct.pack_into("<f", head, T_SAMPLERATE, t["samplerate"])
    head[T_LOVED] = t["loved"] if t["loved"] in (0, 2, 3) else 0

    strings = dict(t["strings"])
    url = strings.pop(S_URL, "")
    if path:
        win = to_windows(path, maps)
        if win is None:
            problems["track outside every --path-map (Mac path kept)"] += 1
            strings[S_URL] = "file://localhost" + urllib.parse.quote(path, safe="/:()$'&,@!+~=*")
        else:
            strings[S_LOCATION] = win
            strings[S_URL] = windows_url(win)
    elif url:
        strings[S_URL] = url
    kids = []
    for kind in sorted(strings):
        text = strings[kind]
        if not text:
            continue
        sid = string_ids[kind].setdefault(text, len(string_ids[kind]) + 1)
        enc = 2 if kind in (S_URL, 0x1D) else None
        kids.append(make_text_mhoh(kind, text, sid, enc))
    put32(head, 12, len(kids))
    put32(head, 8, len(head) + sum(len(k) for k in kids))
    return tid, bytes(head) + b"".join(kids)


def info_to_itl(info):
    """Smart playlist settings: Music keeps the 32-bit fields at 4, 8 and 16 little-endian,
    iTunes big-endian; the bytes at 0-3 and 12-15 are single flags in both."""
    b = bytearray(info)
    for o in (4, 8, 16):
        b[o:o + 4] = b[o:o + 4][::-1]
    if any(b[20:]):
        raise FormatError("smart playlist settings with data past byte 20; not understood")
    return bytes(b)


def build_items(track_ids, item_template, ids, key_start, playlist_pid, track_pids):
    out = []
    for n, tid in enumerate(track_ids):
        it = bytearray(item_template)
        put32(it, I_ID, ids.take())
        put32(it, I_TRACK, tid)
        put32(it, I_KEY, key_start + n)
        it[I_PID:I_PID + 8] = stable_pid(playlist_pid, track_pids[tid], str(n))
        out.append(bytes(it))
    return out


def build_playlist(head, mhohs, items):
    head = bytearray(head)
    put32(head, 12, len(mhohs))
    put32(head, P_ITEMS, len(items))
    put32(head, 8, len(head) + sum(map(len, mhohs)) + sum(map(len, items)))
    return bytes(head) + b"".join(mhohs) + b"".join(items)


def write(args):
    itl = Itl(open(args.template, "rb").read())
    mdb_tracks, mdb_playlists = read_musicdb(args.musicdb)
    ordered = apply_order(mdb_playlists, open(args.order, encoding="utf-8").read()) if args.order else []

    if args.path_map:
        maps = parse_path_maps(args.path_map)
    else:
        votes = derive_path_map(itl, mdb_tracks)
        if not votes:
            raise SystemExit("could not derive a path mapping; pass --path-map MAC=WINDOWS")
        (mac, win), n = votes.most_common(1)[0]
        print("path map (derived from %d files found in both libraries): %s = %s" % (n, mac, win))
        maps = [(mac, win)]

    neutral = neutral_track_headers(itl)
    ids = Ids(itl.max_id() + 100)
    groups = Groups(itl, ids)
    string_ids = collections.defaultdict(dict)
    problems = collections.Counter()
    pid_to_id, records, track_pids = {}, [], {}
    for t in mdb_tracks:
        tid, rec = build_track(t, neutral, ids, string_ids, maps, problems, groups)
        pid_to_id[t["pid"]] = tid
        track_pids[tid] = t["pid"]
        records.append(rec)

    # Playlists: built-in ones from the template (members from Music's playlist of the same kind),
    # user playlists from Music.
    item_template = next((i for p in itl.playlists for i in p["items"] if u32(i, 20) == 0), None)
    if item_template is None:
        item_template = struct.pack("<4sII", b"mtph", 84, 84) + bytes(72)
    by_kind = {}
    for p in mdb_playlists:
        if p["master"]:
            by_kind["master"] = p
        elif p["kind"]:
            by_kind.setdefault(p["kind"], p)
    key = Ids(ids.next + 10 * len(mdb_tracks))  # item order keys, above every id we hand out
    out_playlists = []

    def members(pl):
        missing = [x for x in pl["items"] if x not in pid_to_id]
        if missing:
            problems["playlist item without a track (dropped)"] += len(missing)
        return [pid_to_id[x] for x in pl["items"] if x in pid_to_id]

    for p in itl.playlists:
        if not p["master"] and p["kind"] == 0:
            continue
        source = by_kind.get("master" if p["master"] else p["kind"])
        tids = members(source) if source else []
        items = build_items(tids, item_template, ids, key.take(len(tids)), p["pid"], track_pids)
        out_playlists.append(build_playlist(p["head"], p["mhoh"], items))

    old_user = itl.user_playlists()
    smart_templates = [p for p in old_user if p["smart"]]
    plain_templates = [p for p in old_user if not p["smart"]]
    report = []
    for p in user_musicdb_playlists(mdb_playlists):
        smart = p["rules"] is not None and p["info"] is not None and not args.flatten_smart
        same_name = [o for o in old_user if o["name"].strip().lower() == p["name"].strip().lower()]
        same_rules = [o for o in old_user if p["rules"] and any(
            u32(k, 12) == S_PL_RULES and k[24:] == p["rules"] for k in o["mhoh"])]
        pool = same_name or same_rules or (smart_templates if smart else plain_templates) or old_user \
            or [x for x in itl.playlists if not x["master"]]
        tpl = pool[0]
        head = bytearray(tpl["head"])
        head[P_PID:P_PID + 8] = pid_bytes(p["pid"])
        head[P_SYNC_PID:P_SYNC_PID + 8] = pid_bytes(p["pid"])
        put32(head, P_ID, ids.take())
        struct.pack_into("<H", head, P_KIND, 0)
        head[P_MASTER] = 0
        name_id = next((u32(k, 16) for k in tpl["mhoh"] if u32(k, 12) == S_PL_NAME), 1)
        mhohs = []
        for k in tpl["mhoh"]:
            kind = u32(k, 12)
            if kind == S_PL_NAME:
                mhohs.append(make_text_mhoh(S_PL_NAME, p["name"], name_id))
            elif kind in (S_PL_RULES, S_PL_INFO):
                continue
            else:
                mhohs.append(k)          # view settings and columns of the template playlist
        if not any(u32(k, 12) == S_PL_NAME for k in mhohs):
            mhohs.insert(0, make_text_mhoh(S_PL_NAME, p["name"], name_id))
        if smart:
            at = 1
            mhohs.insert(at, make_blob_mhoh(S_PL_INFO, info_to_itl(p["info"])))
            mhohs.insert(at + 1, make_blob_mhoh(S_PL_RULES, p["rules"]))
        tids = members(p)
        items = build_items(tids, item_template, ids, key.take(len(tids)), p["pid"], track_pids)
        out_playlists.append(build_playlist(bytes(head), mhohs, items))
        report.append("%-22s %s %5d tracks  %s%s  (settings copied from old %r)" % (
            p["name"], p["pid"], len(tids), "smart, rules kept" if smart else "regular",
            ", exact order" if p["name"] in ordered else "", tpl["name"]))

    # Assemble.
    parts = []
    for sec in itl.sections:
        if sec.kind == 1:
            msdh, lh = list_header(sec)
            parts.append(build_section(msdh, lh, len(records), records))
        elif sec.kind in (9, 11):
            msdh, lh = list_header(sec)
            entries = groups.records(sec.kind)
            parts.append(build_section(msdh, lh, len(entries), entries))
        elif sec.kind == 2:
            msdh, lh = list_header(sec)
            parts.append(build_section(msdh, lh, len(out_playlists), out_playlists))
        else:
            parts.append(sec.data)
    inner = bytearray(b"".join(parts))
    m = inner.find(b"mfdh")
    if m < 0:
        raise FormatError("no inner file header")
    total = len(itl.header) + len(inner)
    put32(inner, m + H_FILE_LENGTH, total)
    put32(inner, m + H_TRACKS, len(records))
    put32(inner, m + H_PLAYLISTS, len(out_playlists))
    put32(inner, m + H_ALBUMS, len(groups.used[9]))
    put32(inner, m + H_ARTISTS, len(groups.used[11]))
    header = bytearray(itl.header)
    struct.pack_into(">I", header, H_TRACKS, len(records))
    struct.pack_into(">I", header, H_PLAYLISTS, len(out_playlists))
    struct.pack_into(">I", header, H_ALBUMS, len(groups.used[9]))
    struct.pack_into(">I", header, H_ARTISTS, len(groups.used[11]))
    inner = bytes(inner)
    sealed = seal_container(bytes(header), inner)

    # Self-check before anything is written: it must open to exactly what was serialised.
    h2, inner2, _ = open_container(sealed)
    if inner2 != inner or h2[:H_FILE_LENGTH] != header[:H_FILE_LENGTH]:
        raise FormatError("the written file does not decrypt to what was serialised")
    if os.path.abspath(args.out) in (os.path.abspath(args.template), os.path.abspath(args.musicdb)):
        raise SystemExit("--out must be a new file")
    with open(args.out, "wb") as f:
        f.write(sealed)
    if args.dump_inner:
        with open(args.dump_inner, "wb") as f:
            f.write(inner)
    print("wrote %s: %d bytes, %d tracks, %d playlists, %d albums, %d artists"
          % (args.out, len(sealed), len(records), len(out_playlists), len(groups.used[9]), len(groups.used[11])))
    print("  it decrypts and inflates byte for byte to the serialised body")
    kept = sum(1 for k in (9, 11) for key in groups.used[k] if key in groups.old[k])
    print("  album/artist entries kept from the template: %d" % kept)
    for line in report:
        print("  " + line)
    for what, n in problems.items():
        print("  note: %d x %s" % (n, what))


# ---------------------------------------------------------------- read / verify

def cmd_read(args):
    itl = Itl(open(args.file, "rb").read())
    h = itl.header
    print("%s: iTunes %s, %d bytes, library %s" % (
        args.file, h[0x11:0x11 + h[0x10]].decode("ascii", "replace"), len(open(args.file, "rb").read()),
        h[0x34:0x3C].hex().upper()))
    print("header counts: %d tracks, %d playlists" % struct.unpack_from(">II", h, H_TRACKS))
    print("tracks: %d" % len(itl.tracks))
    for kind, tag, what in ((9, b"miah", "albums"), (11, b"miih", "artists")):
        sec = next((x for x in itl.sections if x.kind == kind), None)
        if sec:
            print("%s: %d" % (what, sum(1 for _ in iter_records(sec, tag, ()))))
    by_id = {t["id"]: t for t in itl.tracks}
    print("playlists: %d" % len(itl.playlists))
    for p in itl.playlists:
        kind = "master" if p["master"] else ("built-in %d" % p["kind"] if p["kind"] else "user")
        print("  %-36r %s %-12s %-6s %5d items" % (p["name"], p["pid"], kind, "smart" if p["smart"] else "", len(p["items"])))
        if args.items and not p["master"] and p["kind"] == 0:
            for tid in p["track_ids"]:
                t = by_id.get(tid, {})
                print("      %s  %s - %s" % (t.get("pid", "?"), t.get(4, ""), t.get(S_TITLE, "")))
    for t in itl.tracks[:args.tracks]:
        print("  track %d %s: %r / %r / %r\n      %s" % (
            t["id"], t["pid"], t.get(S_TITLE, ""), t.get(4, ""), t.get(3, ""), t.get(S_LOCATION) or t.get(S_URL, "")))


def cmd_verify(args):
    itl = Itl(open(args.file, "rb").read())
    tracks, playlists = read_musicdb(args.musicdb)
    if args.order:
        apply_order(playlists, open(args.order, encoding="utf-8").read())
    ok = True

    def check(cond, msg):
        nonlocal ok
        print(("ok    " if cond else "FAIL  ") + msg)
        ok &= bool(cond)

    check(len(itl.tracks) == len(tracks), "tracks: itl %d, Music %d" % (len(itl.tracks), len(tracks)))
    check(struct.unpack_from(">I", itl.header, H_TRACKS)[0] == len(itl.tracks), "header track count")
    check(struct.unpack_from(">I", itl.header, H_PLAYLISTS)[0] == len(itl.playlists), "header playlist count")
    pid_of = {t["id"]: t["pid"] for t in itl.tracks}
    by_pid = {t["pid"]: t for t in itl.tracks}
    check(set(by_pid) == {t["pid"] for t in tracks}, "same track persistent ids")
    bad = [t["pid"] for t in tracks if by_pid.get(t["pid"], {}).get(S_TITLE, "") != t["strings"].get(S_TITLE, "")]
    check(not bad, "titles match (%d differ)" % len(bad))
    heads = {t["pid"]: t["head"] for t in itl.tracks}
    fields = [("duration", T_DURATION), ("year", T_YEAR), ("track", T_TRACK), ("plays", T_PLAYS), ("added", T_ADDED)]
    for name, off in fields:
        bad = [t for t in tracks if t["pid"] in heads and u32(heads[t["pid"]], off) != t.get(name, 0)]
        check(not bad, "%s matches (%d differ)" % (name, len(bad)))
    bad = [t for t in tracks if t["pid"] in heads and heads[t["pid"]][T_LOVED] != (t["loved"] if t["loved"] in (0, 2, 3) else 0)]
    check(not bad, "loved flag matches (%d differ)" % len(bad))
    for kind, tag, off, what in ((9, b"miah", T_ALBUM_ID, "album"), (11, b"miih", T_ARTIST_ID, "artist")):
        sec = next((x for x in itl.sections if x.kind == kind), None)
        known = {u32(h, A_ID) for h, _ in iter_records(sec, tag, ())} if sec else set()
        bad = [t for t in itl.tracks if u32(t["head"], off) not in known]
        check(not bad, "every track links to an existing %s entry (%d do not)" % (what, len(bad)))
    master = next(p for p in itl.playlists if p["master"])
    mdb_master = next(p for p in playlists if p["master"])
    check([pid_of[i] for i in master["track_ids"]] == mdb_master["items"],
          "master playlist: %d items in Music's order" % len(master["items"]))
    mine = {p["pid"]: p for p in itl.user_playlists()}
    theirs = user_musicdb_playlists(playlists)
    check(len(mine) == len(theirs), "user playlists: itl %d, Music %d" % (len(mine), len(theirs)))
    for p in theirs:
        q = mine.get(p["pid"])
        got = [pid_of.get(i) for i in q["track_ids"]] if q else None
        check(q is not None and q["name"] == p["name"] and got == p["items"],
              "playlist %r: %d items, same order%s" % (p["name"], len(p["items"]), ", smart" if q and q["smart"] else ""))
    sys.exit(0 if ok else 1)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("read", help="print a summary of an .itl")
    r.add_argument("file")
    r.add_argument("--tracks", type=int, default=5, help="how many tracks to show")
    r.add_argument("--items", action="store_true", help="list the tracks of user playlists")
    w = sub.add_parser("write", help="write a new .itl from a template and the Music library")
    w.add_argument("--template", required=True, help="the old iTunes Library.itl (read only)")
    w.add_argument("--musicdb", required=True, help="a copy of Library.musicdb")
    w.add_argument("--order", help="playlist-order.txt from tools/playlist-order.applescript")
    w.add_argument("--path-map", action="append", default=[], metavar="MAC=WINDOWS",
                   help=r"e.g. /Users/me/Music=D:\Music (repeatable; derived from the template if left out)")
    w.add_argument("--flatten-smart", action="store_true",
                   help="write smart playlists as regular ones holding their current tracks")
    w.add_argument("--dump-inner", help="also save the decrypted, uncompressed body here")
    w.add_argument("--out", required=True)
    v = sub.add_parser("verify", help="compare an .itl with the Music library")
    v.add_argument("file")
    v.add_argument("--musicdb", required=True)
    v.add_argument("--order")
    a = ap.parse_args()
    try:
        {"read": cmd_read, "write": write, "verify": cmd_verify}[a.cmd](a)
    except FormatError as e:
        raise SystemExit("error: %s" % e)


if __name__ == "__main__":
    main()
