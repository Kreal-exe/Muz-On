// Applies the changes made on the phone (library-changes.json, written by Muz On) in Music on the
// Mac: hearts, playlists made on the phone, and changes to regular playlists (name, songs, order,
// deletion). Smart playlists are left to their rules.
//
//   osascript -l JavaScript tools/apply-changes.js <library-changes.json> <state file>
//
// Prints what was applied, one line each, for the phone (muzon-applied.txt):
//   "<own id> <Music persistent id> <rev>"  a playlist made on the phone now exists in Music
//   "<Music persistent id> <rev>"           a playlist was changed or deleted
// <rev> is the revision the phone sent, so it knows which state of its change arrived.
// The state file remembers which Music playlist each phone playlist became, so running this twice
// never makes a playlist twice.
ObjC.import('Foundation')

function readText(path) {
    const s = $.NSString.stringWithContentsOfFileEncodingError(path, $.NSUTF8StringEncoding, null)
    return s.isNil() ? null : s.js
}

function writeText(path, text) {
    $.NSFileManager.defaultManager.createDirectoryAtPathWithIntermediateDirectoriesAttributesError(
        $(path).stringByDeletingLastPathComponent, true, $(), null)
    $(text).writeToFileAtomicallyEncodingError(path, true, $.NSUTF8StringEncoding, null)
}

function log(text) {
    $.NSFileHandle.fileHandleWithStandardError.writeData($(text + '\n').dataUsingEncoding($.NSUTF8StringEncoding))
}

function run(argv) {
    const changes = JSON.parse(readText(argv[0]) || '{}')
    const statePath = argv[1]
    const made = {}
    ;(readText(statePath) || '').split('\n').forEach(line => {
        const [own, pid] = line.trim().split(' ')
        if (own && pid) made[own] = pid
    })

    const Music = Application('Music')
    const library = Music.libraryPlaylists[0]
    // One call for every id; tracks are then reached by their place in the library.
    const ids = library.tracks.persistentID()
    const at = {}
    ids.forEach((id, i) => { at[id.toUpperCase()] = i })
    // A song the phone knew only as a file comes as its path under Music/, which is the same on the
    // Mac (~/Music/...). Names are compared in one Unicode form and case: macOS often spells a name
    // differently from the phone, byte for byte.
    const key = p => p.normalize('NFC').toLowerCase()
    let byPath = null
    const idOfPath = rel => {
        if (!byPath) {
            byPath = {}
            const root = key($.NSHomeDirectory().js + '/Music/')
            let locations = []
            try { locations = library.tracks.location() } catch (e) {}
            locations.forEach((loc, i) => {
                const k = loc ? key(loc.toString()) : ''
                if (k.startsWith(root)) byPath[k.slice(root.length)] = ids[i].toUpperCase()
            })
        }
        return byPath[key(rel)] || null
    }
    // "path:<under Music/>" in a list of songs becomes the Music id of that file.
    const resolve = id => (id.startsWith('path:') ? idOfPath(id.slice(5)) || id : id)
    const track = id => (id.toUpperCase() in at ? library.tracks[at[id.toUpperCase()]] : null)
    const out = []

    ;(changes.favourites || []).forEach(({ id, path, on }) => {
        const t = track(id) || (path ? track(idOfPath(path) || '') : null)
        if (!t) return log('no track ' + (path || id))
        try { t.favorited = on } catch (e) { t.loved = on }
        log((on ? 'heart  ' : 'unheart ') + t.name())
    })

    const playlistById = pid => {
        const found = Music.userPlaylists.whose({ persistentID: pid })
        return found.length > 0 ? found[0] : null
    }

    // Brings the playlist's songs to [wanted], touching as little as it can: songs only added at the
    // end are added, songs only taken out are taken out; anything else rebuilds the list in order.
    const setTracks = (playlist, wanted) => {
        wanted = wanted.map(id => resolve(id).toUpperCase()).filter(id => id in at)
        // An empty playlist answers with an error rather than an empty list.
        let now = []
        try { now = playlist.tracks.persistentID().map(id => id.toUpperCase()) } catch (e) {}
        if (now.join() === wanted.join()) return
        const isPrefix = now.every((id, i) => wanted[i] === id)
        const wantedSet = new Set(wanted)
        const kept = now.filter(id => wantedSet.has(id))
        if (isPrefix) {
            wanted.slice(now.length).forEach(id => Music.duplicate(track(id), { to: playlist }))
        } else if (kept.join() === wanted.join()) {
            // Remove from the end so the positions of the rest stay valid.
            for (let i = now.length - 1; i >= 0; i--) {
                if (!wantedSet.has(now[i])) Music.delete(playlist.tracks[i])
            }
        } else {
            if (now.length > 0) Music.delete(playlist.tracks)
            wanted.forEach(id => Music.duplicate(track(id), { to: playlist }))
        }
    }

    ;(changes.playlists || []).forEach(change => {
        try {
            if (change.new) {
                let playlist = made[change.id] ? playlistById(made[change.id]) : null
                if (!playlist) {
                    playlist = Music.make({ new: 'userPlaylist', withProperties: { name: change.name } })
                    made[change.id] = playlist.persistentID()
                    writeText(statePath, Object.entries(made).map(([k, v]) => k + ' ' + v).join('\n') + '\n')
                    log('new playlist ' + change.name)
                } else if (playlist.name() !== change.name) {
                    playlist.name = change.name
                }
                setTracks(playlist, change.tracks || [])
                out.push(change.id + ' ' + made[change.id] + ' ' + change.rev)
                return
            }
            const playlist = playlistById(change.id)
            if (!playlist) return log('no playlist ' + change.id)
            if (playlist.smart()) return log('smart, left to its rules: ' + playlist.name())
            if (change.deleted) {
                log('deleted ' + playlist.name())
                Music.delete(playlist)
            } else {
                if (change.name && playlist.name() !== change.name) playlist.name = change.name
                if (change.tracks) setTracks(playlist, change.tracks)
                log('changed ' + playlist.name())
            }
            out.push(change.id + ' ' + change.rev)
        } catch (e) {
            log('cannot apply ' + JSON.stringify(change).slice(0, 120) + ': ' + e)
        }
    })
    return out.join('\n')
}
