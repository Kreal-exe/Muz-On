package dev.kreal.muzon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun track(title: String, artist: String = "Juice WRLD", album: String = "") =
        Track(title, title, artist, "", album, "", 0, 0, 0, 0, 0, "")

    private fun finds(query: String, t: Track) = Search.matches(Search.haystack(t), Search.words(query))

    @Test fun wordsInAnyOrderIgnoringBrackets() {
        val t = track("Time (Session Edit)")
        assertTrue(finds("time session", t))
        assertTrue(finds("session time", t))
        assertTrue(finds("TIME (session", t))
        assertTrue(finds("sess juice", t))
        assertFalse(finds("time remix", t))
    }

    @Test fun accentsAndYo() {
        assertTrue(finds("beyonce", track("Halo", "Beyoncé")))
        assertTrue(finds("елка", track("Ёлка")))
        assertTrue(finds("ёлка", track("Елка")))
    }

    @Test fun wrongKeyboardLayout() {
        val songs = listOf(track("Lavish"), track("Know Us"))
        assertEquals(listOf("Lavish"), Search.filter(songs, "дфмшыр") { Search.haystack(it) }.map { it.title })
    }

    @Test fun titleStartFirst() {
        val words = Search.words("time")
        assertTrue(Search.rank("Time (Session Edit)", words) < Search.rank("Out of Time", words))
    }
}
