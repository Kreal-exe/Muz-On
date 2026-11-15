package dev.kreal.muzon.data

import java.text.Normalizer

/**
 * Forgiving search, the way Music on the Mac finds things: every word typed must appear somewhere in
 * the title, artist, album or genre, in any order. Case, accents, ё/е, brackets and punctuation do not
 * matter, so "time session" finds "Time (Session Edit)" and "beyonce" finds "Beyoncé". A query typed
 * in the wrong keyboard layout ("ешьу" for "time") is tried again in the other one.
 */
object Search {
    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lower case, no accents, ё as е, every run of punctuation and spaces as one space. */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val lower = text.lowercase().replace('ё', 'е').replace('й', 'и')
        val plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
        return plain.replace(separators, " ").trim()
    }

    /** The words of a query, already normalized. */
    fun words(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    /** True when every word appears in [haystack] (a [normalize]d text). */
    fun matches(haystack: String, words: List<String>): Boolean = words.all { haystack.contains(it) }

    /** Normalized texts by their raw text: a heart or a playlist change rebuilds the library, not these. */
    private val prepared = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun haystack(track: Track): String {
        val raw = track.title + "\u0001" + track.artist + "\u0001" + track.albumArtist + "\u0001" + track.album + "\u0001" + track.genre
        return prepared.getOrPut(raw) { normalize(raw) }
    }

    /**
     * Order of the results: a title that starts with the query first, then a word of the title that
     * does, then everything else in the list's own order.
     */
    fun rank(title: String, words: List<String>): Int {
        val t = normalize(title)
        val joined = words.joinToString(" ")
        return when {
            t.startsWith(joined) -> 0
            t.split(' ').any { w -> w.startsWith(words.first()) } && words.all { t.contains(it) } -> 1
            words.all { t.contains(it) } -> 2
            else -> 3
        }
    }

    private const val LATIN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val CYRILLIC = "йцукенгшщзхъфывапролджэячсмитьбюё"

    /** The same keys pressed in the other layout, Russian and English. */
    fun otherLayout(query: String): String? {
        val out = StringBuilder(query.length)
        var changed = false
        for (c in query.lowercase()) {
            val i = LATIN.indexOf(c)
            val j = CYRILLIC.indexOf(c)
            when {
                i >= 0 -> { out.append(CYRILLIC[i]); changed = true }
                j >= 0 -> { out.append(LATIN[j]); changed = true }
                else -> out.append(c)
            }
        }
        return if (changed) out.toString() else null
    }

    /** Filters [items] by the query, trying the other keyboard layout when nothing matches. */
    fun <T> filter(items: List<T>, query: String, text: (T) -> String): List<T> {
        val words = words(query)
        if (words.isEmpty()) return items
        val found = items.filter { matches(text(it), words) }
        if (found.isNotEmpty()) return found
        val other = otherLayout(query)?.let(::words) ?: return found
        return items.filter { matches(text(it), other) }
    }

    /** Tracks of [tracks] matching the query, best matches first, using the library's prepared texts. */
    fun tracks(library: Library, tracks: List<Track>, query: String): List<Track> {
        var words = words(query)
        if (words.isEmpty()) return tracks
        var found = tracks.filter { matches(library.searchText(it), words) }
        if (found.isEmpty()) {
            otherLayout(query)?.let(::words)?.let { other ->
                found = tracks.filter { matches(library.searchText(it), other) }
                words = other
            }
        }
        val w = words
        return found.sortedBy { rank(it.title, w) }
    }
}
