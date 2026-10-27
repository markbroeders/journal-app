package nl.markbroeders.journal.data

/**
 * A search as typed in the search field: words (prefix matches), `"quoted phrases"` and
 * `#tags`. All parts must match. Tags match an entry's inline tags or its day's frontmatter
 * tags, case-insensitively, including nested tags (`#family` finds `#family/kids`), like
 * Obsidian's tag search.
 */
data class SearchQuery(
    /** Each term is one word or phrase, as a list of its lowercase words. */
    val terms: List<Term>,
    val tags: List<String>,
) {
    data class Term(val words: List<String>, val prefix: Boolean)

    val isEmpty: Boolean get() = terms.isEmpty() && tags.isEmpty()

    /**
     * The FTS `MATCH` expression for [terms], or null if there are none. Words are split the way
     * the tokenizer splits text, so `don't` becomes the phrase `"don t*"`; nothing the user types
     * can turn into an FTS operator.
     */
    fun matchExpression(): String? {
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { term ->
            val star = if (term.prefix) "*" else ""
            if (term.words.size == 1) "${term.words[0]}$star" else "\"${term.words.joinToString(" ")}$star\""
        }
    }

    /**
     * `LIKE` patterns (escape character `\`) for [tag], to match against a newline-joined tag
     * list wrapped in newlines: the tag itself, or a tag nested under it.
     */
    fun tagPatterns(tag: String): List<String> {
        val escaped = tag.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return listOf("%\n$escaped\n%", "%\n$escaped/%")
    }

    companion object {

        // Same characters as an Obsidian tag (see Markdown.inlineTags).
        private val TAG = Regex("""#([\p{L}\p{N}_/\-]+)""")
        private val WORD_PIECE = Regex("""[\p{L}\p{N}]+""")

        fun parse(input: String): SearchQuery {
            val terms = mutableListOf<Term>()
            val tags = mutableListOf<String>()
            var i = 0
            while (i < input.length) {
                val c = input[i]
                when {
                    c.isWhitespace() -> i++
                    c == '"' -> {
                        val close = input.indexOf('"', i + 1)
                        val end = if (close < 0) input.length else close
                        // An unclosed quote is still being typed: treat its last word as a prefix.
                        words(input.substring(i + 1, end))?.let { terms += Term(it, prefix = close < 0) }
                        i = end + 1
                    }
                    else -> {
                        var end = i
                        while (end < input.length && !input[end].isWhitespace() && input[end] != '"') end++
                        val token = input.substring(i, end)
                        val tag = TAG.matchEntire(token)?.groupValues?.get(1)?.trimEnd('/', '-')
                        if (tag != null && tag.isNotEmpty() && tag.any { !it.isDigit() }) {
                            tags += tag
                        } else {
                            words(token)?.let { terms += Term(it, prefix = true) }
                        }
                        i = end
                    }
                }
            }
            return SearchQuery(terms, Markdown.distinctTags(tags))
        }

        private fun words(text: String): List<String>? =
            WORD_PIECE.findAll(text.lowercase()).map { it.value }.toList().takeIf { it.isNotEmpty() }
    }
}
