package nl.markbroeders.journal.data

/** Small Markdown helpers shared by the parser and writer. */
internal object Markdown {

    /** Splits into lines, each keeping its trailing `\n` (the last one may lack it). */
    fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val nl = text.indexOf('\n', start)
            val end = if (nl < 0) text.length else nl + 1
            lines += text.substring(start, end)
            start = end
        }
        return lines
    }

    /**
     * Tracks fenced code blocks (``` or ~~~) line by line, CommonMark style: a fence closes
     * on a line of the same character, at least as long, with nothing else on it.
     */
    class FenceTracker {
        private var fenceChar: Char? = null
        private var fenceLength = 0

        val inFence: Boolean get() = fenceChar != null

        /** Feeds one line; returns true if the line is part of a fence (including its delimiters). */
        fun accept(line: String): Boolean {
            val content = line.removeSuffix("\n")
            val m = FENCE.matchEntire(content)
            val open = fenceChar
            if (open == null) {
                if (m != null) {
                    val run = m.groupValues[1]
                    // Backtick fences cannot have backticks in the info string.
                    if (run[0] == '`' && m.groupValues[2].contains('`')) return false
                    fenceChar = run[0]
                    fenceLength = run.length
                    return true
                }
                return false
            }
            if (m != null && m.groupValues[1][0] == open && m.groupValues[1].length >= fenceLength &&
                m.groupValues[2].isBlank()
            ) {
                fenceChar = null
            }
            return true
        }

        private companion object {
            val FENCE = Regex(""" {0,3}(`{3,}|~{3,})(.*)""")
        }
    }

    private val INLINE_CODE = Regex("""(`+)[\s\S]*?\1""")

    // Obsidian tags: letters, digits, _, -, / ; must contain at least one non-digit.
    private val TAG = Regex("""(?<![^\s(])#([\p{L}\p{N}_/\-]+)""")

    fun inlineTags(text: String): List<String> {
        val fence = FenceTracker()
        val tags = mutableListOf<String>()
        for (line in splitLines(text)) {
            if (fence.accept(line)) continue
            val stripped = line.replace(INLINE_CODE, " ")
            for (m in TAG.findAll(stripped)) {
                val tag = m.groupValues[1].trimEnd('/', '-')
                if (tag.isNotEmpty() && tag.any { !it.isDigit() }) tags += tag
            }
        }
        return distinctTags(tags)
    }

    fun distinctTags(tags: List<String>): List<String> {
        val seen = HashSet<String>()
        return tags.filter { seen.add(it.lowercase()) }
    }

    private val WIKI_EMBED = Regex("""!\[\[([^\]|#^]+)[^\]]*]]""")
    private val MD_EMBED = Regex("""!\[[^\]]*]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")

    fun embeds(text: String): List<String> {
        val fence = FenceTracker()
        val result = mutableListOf<String>()
        for (line in splitLines(text)) {
            if (fence.accept(line)) continue
            val stripped = line.replace(INLINE_CODE, " ")
            val found = WIKI_EMBED.findAll(stripped).map { it.range.first to it.groupValues[1].trim() } +
                MD_EMBED.findAll(stripped).map { it.range.first to it.groupValues[1] }
            result += found.sortedBy { it.first }.map { it.second }
        }
        return result
    }

    private val MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "svg",
        "mp4", "mov", "webm", "mkv", "m4v",
    )

    fun isMedia(target: String): Boolean =
        target.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS
}
