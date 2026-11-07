package dev.kreal.muzon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LibraryCheckTest {
    @Test
    fun resolvesAcrossUnicodeFormsSpacesAndFormats() {
        val resolver = PathResolver(
            listOf(
                PathResolver.Entry("ЛСП/Tragic City/10. Ещё один день.mp3", "Ещё один день", "a"),
                PathResolver.Entry("Lil Peep/Everebody's Everything/01 x.mp3", "x", "b"),
                PathResolver.Entry("Juice WRLD/Juice WRLD [FLAC]/WRLD ON DRUGS [2018]/01 Jet Lag.flac", "Jet Lag", "c"),
                PathResolver.Entry("Juice WRLD/Unreleased/Relocate.mp3", "Relocate", "d"),
                PathResolver.Entry("Juice WRLD/Juice WRLD [FLAC]/Fighting Demons [2021]/12 - Relocate.flac", "Relocate", "e"),
            ),
        )
        val decomposed = java.text.Normalizer.normalize("Ещё", java.text.Normalizer.Form.NFD)
        assertEquals("a", resolver.resolve("/Users/x/Music/ЛСП/Tragic City/10. $decomposed один день.mp3", "t"))
        assertEquals("b", resolver.resolve("/Users/x/Music/Lil Peep/Everebody's Everything /01 x.mp3", "x"))
        assertEquals(
            "c",
            resolver.resolve(
                "/Users/x/Music/Juice WRLD/Juice WRLD [MP3]/(2018) WRLD ON DRUGS/01 - Jet Lag (with Future) [feat. Young Scooter].mp3",
                "Jet Lag",
            ),
        )
        assertEquals(
            "e",
            resolver.resolve("/Users/x/Music/Juice WRLD/Juice WRLD [MP3]/Fighting Demons/12 - Relocate.mp3", "Relocate"),
        )
        assertNull(resolver.resolve("/Users/x/Music/Nobody/Nothing.mp3", "Nothing"))
    }

    @Test
    fun orderFileAppliesOnlyWhenItFits() {
        val base = listOf(Playlist("AA", "One", listOf("1", "2", "3")), Playlist("BB", "Two", listOf("4", "5")))
        val out = MusicDb.applyOrder(base, "#AA\n3\n1\n2\n#BB\n4\n")
        assertEquals(listOf("3", "1", "2"), out[0].trackIds)
        assertEquals(listOf("4", "5"), out[1].trackIds)
    }

    /**
     * Checks against a real library when run with -Dmuzon.check=<dir>. The dir holds Library.musicdb,
     * music-order.txt (id lines per playlist, as read from the Mac app) and phone-files.txt (relative
     * paths of the audio files on the device). Nothing personal is committed.
     */
    @Test
    fun matchesTheRealLibrary() {
        val dir = System.getenv("MUZON_CHECK")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val parsed = MusicDb.parse(File(dir, "Library.musicdb").readBytes())
        println("tracks ${parsed.tracks.size}, playlists ${parsed.playlists.map { it.name to it.trackIds.size }}")
        assertEquals(3534, parsed.tracks.size)

        val order = File(dir, "music-order.txt").readText()
        val playlists = MusicDb.applyOrder(parsed.playlists, order)
        val expected = readOrder(order)
        for (p in playlists) {
            val want = assertNotNull_(expected[p.id], p.name)
            assertEquals(p.name, want.size, p.trackIds.size)
            assertEquals(p.name, want, p.trackIds)
        }

        val library = Library(LibrarySource.MusicDatabase, parsed.tracks, playlists)
        println("artists ${library.artists.size}: ${library.artists.map { it.name }}")
        println("albums ${library.albums.size}, genres ${library.genres.size}")
        val first = parsed.tracks.first()
        println("sample $first")

        val files = File(dir, "phone-files.txt").readLines().filter { it.isNotBlank() }
        val resolver = PathResolver(files.map { PathResolver.Entry(it, "", it) })
        val missing = parsed.tracks.filter { resolver.resolve(it.sourcePath, it.title) == null }
        println("unresolved ${missing.size}")
        missing.forEach { println("  ${it.sourcePath}") }
        val sum = parsed.tracks.groupBy { resolver.resolve(it.sourcePath, it.title) }.filter { it.key != null && it.value.size > 1 }
        println("files claimed twice: ${sum.size}")
        sum.entries.take(20).forEach { println("  ${it.key} <- ${it.value.map { t -> t.sourcePath }}") }
    }

    private fun <T : Any> assertNotNull_(value: T?, name: String): T {
        assertNotNull(name, value)
        return value!!
    }

    private fun readOrder(text: String): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>()
        var cur: MutableList<String>? = null
        text.lineSequence().forEach { l ->
            if (l.startsWith("#")) cur = ArrayList<String>().also { out[l.drop(1).trim()] = it } else if (l.isNotBlank()) cur?.add(l.trim())
        }
        return out
    }
}
