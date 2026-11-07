package dev.kreal.muzon.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The rules of a smart playlist as Music stores them (an "SLst" block, big-endian), evaluated against
 * a track. Only what a library like this one uses is understood: text fields, plays, year, the
 * favourite mark, media kind and nested rule groups. Anything else makes [evaluate] give up, and the
 * playlist then keeps exactly the tracks the Mac listed.
 */
object SmartRules {
    class Unsupported(message: String) : Exception(message)

    private class Rule(val field: Int, val logic: Int, val data: ByteArray)
    private class Group(val any: Boolean, val rules: List<Rule>)

    private fun parse(b: ByteArray): Group {
        if (b.size < 136 || String(b, 0, 4, Charsets.ISO_8859_1) != "SLst") throw Unsupported("not a rule block")
        val buf = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
        val count = buf.getInt(8)
        val any = buf.getInt(12) == 1
        var i = 136
        val rules = ArrayList<Rule>(count)
        repeat(count) {
            if (i + 56 > b.size) throw Unsupported("truncated")
            val field = buf.getInt(i)
            val logic = buf.getInt(i + 4)
            val length = buf.getInt(i + 52)
            if (length < 0 || i + 56 + length > b.size) throw Unsupported("bad length")
            rules += Rule(field, logic, b.copyOfRange(i + 56, i + 56 + length))
            i += 56 + length
        }
        return Group(any, rules)
    }

    /** True when [track] belongs; throws [Unsupported] for a rule this reader does not know. */
    fun evaluate(rules: ByteArray, track: Track): Boolean = evaluate(parse(rules), track)

    private fun evaluate(group: Group, track: Track): Boolean {
        val results = group.rules.map { rule(it, track) }
        return if (group.any) results.any { it } else results.all { it }
    }

    private fun rule(rule: Rule, track: Track): Boolean {
        val negative = (rule.logic ushr 24) and 2 != 0
        val op = rule.logic and 0xFFFFFF
        val result = when (rule.field) {
            0 -> evaluate(parse(rule.data), track)
            2, 3, 4, 8, 0x47, 0x12, 0x0E -> {
                val value = String(rule.data, Charsets.UTF_16BE).lowercase()
                val text = when (rule.field) {
                    2 -> track.title
                    3 -> track.album
                    4 -> track.artist
                    8 -> track.genre
                    0x47 -> track.albumArtist
                    else -> ""
                }.lowercase()
                when (op) {
                    1 -> text == value
                    2 -> text.contains(value)
                    4 -> text.startsWith(value)
                    8 -> text.endsWith(value)
                    else -> throw Unsupported("text operator $op")
                }
            }
            else -> {
                if (rule.data.size < 8) throw Unsupported("short number")
                val a = ByteBuffer.wrap(rule.data).order(ByteOrder.BIG_ENDIAN).getLong(0)
                val value: Long = when (rule.field) {
                    0x9A -> if (track.favorite) 2 else 0
                    0x16 -> track.playCount.toLong()
                    0x07 -> track.year.toLong()
                    0x3C -> 1 // music
                    else -> throw Unsupported("field ${rule.field}")
                }
                when (op) {
                    1 -> value == a
                    0x10 -> value > a
                    0x40 -> value < a
                    else -> throw Unsupported("number operator $op")
                }
            }
        }
        return if (negative) !result else result
    }
}
