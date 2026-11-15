// Lists the songs of Music on the Mac whose files the phone does not have yet, so that
// tools/sync-library.sh copies them over into the same folders.
//
//   osascript -l JavaScript tools/missing-on-phone.js <phone listing>
//
// <phone listing>: every file under /sdcard/Music on the phone, one per line, as `find` prints it.
// Only songs whose files lie in ~/Music are considered: ~/Music/<path> on the Mac is
// /sdcard/Music/<path> on the phone. Names are compared in one Unicode form and case, since macOS
// and Android often spell the same name differently, byte for byte.
// Prints one path under Music/ per line, spelled as the file is named on the Mac.
ObjC.import('Foundation')

function run(argv) {
    const listing = $.NSString.stringWithContentsOfFileEncodingError(argv[0], $.NSUTF8StringEncoding, null)
    const key = p => p.normalize('NFC').toLowerCase()
    const phoneRoot = '/sdcard/music/'
    const onPhone = new Set()
    ;(listing.isNil() ? '' : listing.js).split('\n').forEach(line => {
        const k = key(line.replace(/\r$/, ''))
        if (k.startsWith(phoneRoot)) onPhone.add(k.slice(phoneRoot.length))
    })

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
        if (onPhone.has(k) || seen.has(k)) return
        seen.add(k)
        if (fm.fileExistsAtPath(path)) out.push(rel)
    })
    return out.join('\n')
}
