// Lists the songs of Music on the Mac whose files the phone does not have yet, so that
// tools/sync-library.sh copies them over into the same folders.
//
//   osascript -l JavaScript tools/missing-on-phone.js <phone listing> [exceptions file]
//
// <phone listing>: every file under /sdcard/Music on the phone, one per line, as `find` prints it.
// Only songs whose files lie in ~/Music are considered: ~/Music/<path> on the Mac is
// /sdcard/Music/<path> on the phone. Paths are compared regardless of Unicode form, letter case and
// spaces around names, since macOS and Android often spell the same name differently.
//
// [exceptions file]: folders (or files) under Music/ that are never copied, one per line; lines
// starting with # are notes. For an album the phone keeps in another format, for example.
// Prints one path under Music/ per line, spelled as the file is named on the Mac.
ObjC.import('Foundation')

const readText = path => {
    const s = path ? $.NSString.stringWithContentsOfFileEncodingError(path, $.NSUTF8StringEncoding, null) : null
    return !s || s.isNil() ? '' : s.js
}
const key = p => p.normalize('NFC').split('/').map(s => s.trim()).join('/').toLowerCase()

function run(argv) {
    const phoneRoot = '/sdcard/music/'
    const onPhone = new Set()
    readText(argv[0]).split('\n').forEach(line => {
        const k = key(line.replace(/\r$/, ''))
        if (k.startsWith(phoneRoot)) onPhone.add(k.slice(phoneRoot.length))
    })
    const skipped = readText(argv[1]).split('\n')
        .map(line => line.replace(/\r$/, '').trim())
        .filter(line => line && !line.startsWith('#'))
        .map(line => key(line).replace(/^\/+|\/+$/g, ''))
    const isSkipped = k => skipped.some(s => k === s || k.startsWith(s + '/'))

    const root = $.NSHomeDirectory().js + '/Music/'
    const fm = $.NSFileManager.defaultManager
    let locations = []
    try { locations = Application('Music').libraryPlaylists[0].tracks.location() } catch (e) {}
    const seen = new Set()
    const out = []
    locations.forEach(loc => {
        if (!loc) return
        const path = loc.toString()
        if (!path.startsWith(root)) return
        const rel = path.slice(root.length)
        const k = key(rel)
        if (onPhone.has(k) || seen.has(k) || isSkipped(k)) return
        seen.add(k)
        if (fm.fileExistsAtPath(path)) out.push(rel)
    })
    return out.join('\n')
}
