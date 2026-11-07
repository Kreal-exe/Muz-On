package dev.kreal.muzon.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class Lyrics(val timed: List<LyricLine>, val plain: List<String>) {
    val isEmpty: Boolean get() = timed.isEmpty() && plain.isEmpty()
}

object LyricsReader {
    private val STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val META = Regex("""^\[[a-zA-Z]+:.*]$""")
    private val cache = object : LinkedHashMap<String, Lyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>) = size > 12
    }

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    suspend fun read(track: Track): Lyrics = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[track.id] }?.let { return@withContext it }
        val path = track.filePath ?: return@withContext Lyrics(emptyList(), emptyList())
        val blocks = runCatching {
            AudioFileIO.read(File(path)).tag?.getAll(FieldKey.LYRICS).orEmpty()
        }.getOrDefault(emptyList())
        parse(blocks).also { synchronized(cache) { cache[track.id] = it } }
    }

    fun parse(blocks: List<String>): Lyrics {
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (block in blocks) {
            for (raw in block.lineSequence()) {
                val line = raw.trim()
                val stamps = STAMP.findAll(line).toList()
                if (stamps.isNotEmpty()) {
                    val text = line.replace(STAMP, "").trim()
                    for (s in stamps) timed += LyricLine(toMillis(s), text)
                } else if (line.isNotEmpty() && !META.matches(line)) {
                    plain += line
                }
            }
        }
        timed.sortBy { it.timeMs }
        // Blank timed lines only separate verses: keep the gap out of the list.
        return Lyrics(timed.filter { it.text.isNotEmpty() }, plain)
    }

    private fun toMillis(match: MatchResult): Long {
        val (m, s, f) = match.destructured
        val fraction = if (f.isEmpty()) 0 else f.padEnd(3, '0').take(3).toInt()
        return m.toLong() * 60_000 + s.toLong() * 1000 + fraction
    }
}
