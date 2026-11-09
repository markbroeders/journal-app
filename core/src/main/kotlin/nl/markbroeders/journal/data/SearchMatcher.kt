package nl.markbroeders.journal.data

import java.text.Normalizer

/**
 * Matches a [SearchQuery] against text in memory, the way the Android index's FTS table does
 * (unicode61): words are runs of letters and digits, compared case- and accent-insensitively;
 * a term matches consecutive words, its last word as a prefix if the term is one. Every term and
 * every tag must match. Tags match like [SearchQuery] describes: the tag itself or one nested
 * under it, case-insensitively.
 */
class SearchMatcher(val query: SearchQuery) {

    private val terms = query.terms.map { t -> t.copy(words = t.words.map(::fold)) }

    val isEmpty: Boolean get() = query.isEmpty

    /** True if [text] contains every term and [tags] hold every tag of the query. */
    fun matches(text: String, tags: List<String>): Boolean {
        if (query.tags.any { tag -> tags.none { matchesTag(it, tag) } }) return false
        if (terms.isEmpty()) return true
        val words = words(text)
        return terms.all { term -> occurrences(words, term).any() }
    }

    /** The character ranges in [text] where a term matches, in order, for highlighting. */
    fun highlights(text: String): List<IntRange> {
        if (terms.isEmpty()) return emptyList()
        val words = words(text)
        return terms
            .flatMap { term -> occurrences(words, term).map { i -> words[i].range.first..words[i + term.words.size - 1].range.last } }
            .sortedBy { it.first }
    }

    private fun occurrences(words: List<Word>, term: SearchQuery.Term): Sequence<Int> {
        val n = term.words.size
        return (0..words.size - n).asSequence().filter { start ->
            term.words.indices.all { j ->
                val word = words[start + j].folded
                if (term.prefix && j == n - 1) word.startsWith(term.words[j]) else word == term.words[j]
            }
        }
    }

    private class Word(val folded: String, val range: IntRange)

    companion object {
        // Combining marks belong to the word they decorate (a decomposed "é").
        private val WORD = Regex("""[\p{L}\p{N}][\p{L}\p{N}\p{M}]*""")
        private val MARKS = Regex("""\p{M}+""")

        private fun words(text: String): List<Word> =
            WORD.findAll(text).map { Word(fold(it.value), it.range) }.toList()

        /** Lowercase without accents: `Café` → `cafe`. */
        fun fold(word: String): String =
            Normalizer.normalize(word, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

        private fun matchesTag(tag: String, wanted: String): Boolean =
            tag.equals(wanted, ignoreCase = true) || tag.startsWith("$wanted/", ignoreCase = true)
    }
}
