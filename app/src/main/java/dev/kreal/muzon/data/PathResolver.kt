package dev.kreal.muzon.data

import java.text.Normalizer

/**
 * Finds the file on this device for a path recorded on the Mac. Paths are compared without the
 * differences that do not matter: Unicode form (the Mac keeps й and ё decomposed) and spaces around a
 * name. A library may keep another copy of an album in another format and folder, so when the exact
 * path is absent the search widens: the same name in another format, then the same track number and
 * title inside the same top-level folder.
 */
class PathResolver<F>(files: List<Entry<F>>) {
    class Entry<F>(val relativePath: String, val title: String, val file: F)

    private val exact = HashMap<String, F>(files.size * 2)
    private val folded = HashMap<String, F>(files.size * 2)
    private val noExtension = HashMap<String, F>(files.size * 2)
    private val byTopFolder = HashMap<String, MutableList<Candidate<F>>>()

    private class Candidate<F>(val number: Int?, val name: String, val nameWords: Set<String>, val title: String, val folder: String, val file: F)

    init {
        for (entry in files) {
            val path = normalize(entry.relativePath)
            exact[path] = entry.file
            folded[path.lowercase()] = entry.file
            noExtension[stripExtension(path).lowercase()] = entry.file
            val leaf = stripExtension(path.substringAfterLast('/'))
            byTopFolder.getOrPut(path.substringBefore('/').lowercase()) { ArrayList() } += Candidate(
                number = leadingNumber(leaf),
                name = cleanName(dropNumber(leaf)),
                nameWords = words(dropNumber(leaf)),
                title = cleanName(entry.title),
                folder = path.substringBeforeLast('/', "").substringAfterLast('/'),
                file = entry.file,
            )
        }
    }

    /** [sourcePath] is the full Mac path, [title] the track's title in the library. */
    fun resolve(sourcePath: String, title: String): F? {
        val components = normalize(sourcePath).trim('/').split('/')
        // Leading folders differ between machines: try each suffix, longest first.
        for (start in 0 until components.size) {
            val path = components.subList(start, components.size).joinToString("/")
            exact[path]?.let { return it }
            folded[path.lowercase()]?.let { return it }
            noExtension[stripExtension(path).lowercase()]?.let { return it }
        }
        for (start in 0 until components.size - 1) {
            val top = components[start].lowercase()
            val candidates = byTopFolder[top] ?: continue
            findTwin(candidates, components[components.size - 2], components.last(), title)?.let { return it }
        }
        return null
    }

    /**
     * A copy in another format may be another edition: its folder is named differently, its track
     * numbers can shift and its titles can drop a "(with ...)". The name must match; the number, the
     * words of the album folder and the bracket text then pick between editions.
     */
    private fun findTwin(candidates: List<Candidate<F>>, sourceFolder: String, fileName: String, title: String): F? {
        val leaf = stripExtension(fileName)
        val number = leadingNumber(leaf)
        val sourceWords = words(dropNumber(leaf))
        val wanted = setOf(cleanName(dropNumber(leaf)), cleanName(title)).filter { it.isNotEmpty() }
        val folderWords = words(sourceFolder)
        var best: Candidate<F>? = null
        var bestScore = 0
        for (candidate in candidates) {
            val sameNumber = number != null && candidate.number == number
            val named = candidate.name in wanted || candidate.title in wanted ||
                (sameNumber && wanted.any { it.startsWith(candidate.name) || candidate.name.startsWith(it) })
            if (!named) continue
            var score = 1
            if (sameNumber) score += 4
            // Bracket text tells editions apart: "(Remix)" is another recording, "(with Future)" is not.
            score += 2 * (candidate.nameWords intersect sourceWords).size - (candidate.nameWords - sourceWords).size
            score += (words(candidate.folder) intersect folderWords).size
            if (best == null || score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best?.file
    }

    companion object {
        private val BRACKETS = Regex("""\([^)]*\)|\[[^]]*]""")
        private val NUMBER = Regex("""^\s*(\d{1,3})(?:\s*[-.]\s*|\s+)""")
        private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")

        fun normalize(path: String): String =
            Normalizer.normalize(path, Normalizer.Form.NFC).split('/').joinToString("/") { it.trim() }

        private fun stripExtension(path: String): String {
            val dot = path.lastIndexOf('.')
            return if (dot > path.lastIndexOf('/') + 1) path.substring(0, dot) else path
        }

        private fun leadingNumber(name: String) = NUMBER.find(name)?.groupValues?.get(1)?.toIntOrNull()
        private fun dropNumber(name: String) = name.replace(NUMBER, "")

        /** Letters and digits only, so `7 A.M.`, `7 Am` and `The Bee's` / `The Bees` compare equal. */
        private fun compact(name: String): String = name.lowercase().replace(NOT_WORD, "")
        private fun cleanName(name: String): String = compact(name.replace(BRACKETS, " "))
        private fun words(name: String): Set<String> =
            name.lowercase().split(NOT_WORD).filter { it.length > 2 && it.toIntOrNull() == null }.toSet()
    }
}
