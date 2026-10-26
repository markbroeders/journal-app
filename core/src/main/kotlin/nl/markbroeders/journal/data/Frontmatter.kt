package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A leniently parsed top-level frontmatter value. Nested maps come through as an empty [Scalar]. */
sealed interface FrontmatterValue {
    val isEmpty: Boolean

    data class Scalar(val text: String) : FrontmatterValue {
        override val isEmpty: Boolean get() = text.isEmpty()
    }

    data class Sequence(val items: List<String>) : FrontmatterValue {
        override val isEmpty: Boolean get() = items.isEmpty()
    }
}

/**
 * YAML frontmatter, kept as raw lines so that untouched keys (known or not), comments and
 * ordering survive edits byte for byte. Only top-level `key: value` pairs are interpreted.
 */
class Frontmatter internal constructor(
    /** The whole block, from the opening `---` through the closing delimiter line. */
    val raw: String,
) {
    private val lines: List<String> = Markdown.splitLines(raw)

    private class KeyBlock(
        val key: String,
        val start: Int,
        val end: Int,
        val value: FrontmatterValue,
        val isBlockSequence: Boolean,
        val itemPrefix: String,
    )

    private val blocks: List<KeyBlock> = parseBlocks()

    val keys: List<String> get() = blocks.map { it.key }

    operator fun get(key: String): FrontmatterValue? = blocks.firstOrNull { it.key == key }?.value

    val date: LocalDate?
        get() = (get("date") as? FrontmatterValue.Scalar)?.text?.trim()?.take(10)?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /** `tags` as a list; a single string is split on commas and whitespace. A leading `#` is dropped. */
    val tags: List<String>
        get() = when (val v = get("tags")) {
            is FrontmatterValue.Sequence -> v.items
            is FrontmatterValue.Scalar -> v.text.split(',', ' ', '\t')
            null -> emptyList()
        }.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }

    val mood: Int?
        get() = (get("mood") as? FrontmatterValue.Scalar)?.text?.trim()?.toIntOrNull()?.takeIf { it in 1..5 }

    val starred: Boolean?
        get() = when ((get("starred") as? FrontmatterValue.Scalar)?.text?.trim()?.lowercase()) {
            "true", "yes", "on", "y" -> true
            "false", "no", "off", "n" -> false
            else -> null
        }

    /**
     * Returns the raw block with [key] set to [value] (or removed when null). An existing key
     * is replaced in place; a sequence keeps its block or flow style. A new key is appended.
     */
    internal fun withValue(key: String, value: FrontmatterValue?): String {
        val existing = blocks.firstOrNull { it.key == key }
        val rendered = value?.let { render(key, it, existing) }.orEmpty()
        return if (existing != null) {
            lines.subList(0, existing.start).joinToString("") + rendered +
                lines.subList(existing.end, lines.size).joinToString("")
        } else {
            if (value == null) return raw
            // Insert before the closing delimiter (the last line).
            lines.subList(0, lines.size - 1).joinToString("") + rendered + lines.last()
        }
    }

    private fun render(key: String, value: FrontmatterValue, existing: KeyBlock?): String = when (value) {
        is FrontmatterValue.Scalar -> "$key: ${yamlScalar(value.text)}\n"
        is FrontmatterValue.Sequence ->
            if (existing?.isBlockSequence == true && value.items.isNotEmpty()) {
                "$key:\n" + value.items.joinToString("") { "${existing.itemPrefix}${yamlScalar(it)}\n" }
            } else {
                "$key: [${value.items.joinToString(", ") { yamlScalar(it) }}]\n"
            }
    }

    private fun parseBlocks(): List<KeyBlock> {
        val result = mutableListOf<KeyBlock>()
        // Skip the opening and closing delimiter lines.
        var i = 1
        val last = lines.size - 1
        while (i < last) {
            val line = lines[i].removeSuffix("\n")
            val m = KEY_LINE.matchEntire(line)
            if (m == null) {
                i++
                continue
            }
            var j = i + 1
            while (j < last && isContinuation(lines[j].removeSuffix("\n"))) j++
            val inline = m.groupValues[2]
            val continuation = lines.subList(i + 1, j).map { it.removeSuffix("\n") }
            val items = continuation.mapNotNull { SEQ_ITEM.matchEntire(it) }
            val isBlockSeq = inline.isBlank() && items.isNotEmpty() && items.size == continuation.size
            val value = when {
                isBlockSeq -> FrontmatterValue.Sequence(items.map { unquote(it.groupValues[2]) })
                inline.trimStart().startsWith("[") -> FrontmatterValue.Sequence(parseFlowSequence(inline.trim()))
                else -> FrontmatterValue.Scalar(unquote(inline))
            }
            val prefix = items.firstOrNull()?.groupValues?.get(1) ?: "  - "
            result += KeyBlock(m.groupValues[1].trim(), i, j, value, isBlockSeq, prefix)
            i = j
        }
        return result
    }

    private fun isContinuation(line: String): Boolean =
        line.isNotBlank() && (line[0] == ' ' || line[0] == '\t' || line.startsWith("- ") || line == "-")

    companion object {
        private val KEY_LINE = Regex("""([^\s#\-:'"][^:]*?):(?:[ \t]+(.*?))?[ \t]*""")
        private val SEQ_ITEM = Regex("""(\s*-[ \t]+)(.*)""")
        private val COMMENT = Regex("""\s+#.*$""")

        /** Strips quotes (or a trailing comment, for plain scalars). */
        internal fun unquote(value: String): String {
            val v = value.trim()
            if (v.length >= 2 && v.startsWith('"')) {
                val close = findClosingQuote(v, '"')
                if (close > 0) return v.substring(1, close).replace("\\\"", "\"").replace("\\\\", "\\")
            }
            if (v.length >= 2 && v.startsWith('\'')) {
                val close = findClosingQuote(v, '\'')
                if (close > 0) return v.substring(1, close).replace("''", "'")
            }
            return v.replace(COMMENT, "").trim()
        }

        private fun findClosingQuote(v: String, quote: Char): Int {
            var i = 1
            while (i < v.length) {
                val c = v[i]
                if (quote == '"' && c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) {
                    if (quote == '\'' && i + 1 < v.length && v[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    return i
                }
                i++
            }
            return -1
        }

        /** Parses `[a, "b, c", 'd']`, respecting quotes. Unbalanced input is handled leniently. */
        internal fun parseFlowSequence(text: String): List<String> {
            val inner = text.removePrefix("[").substringBeforeLast("]")
            val items = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            for (c in inner) {
                when {
                    quote != null -> {
                        current.append(c)
                        if (c == quote) quote = null
                    }
                    c == '"' || c == '\'' -> {
                        quote = c
                        current.append(c)
                    }
                    c == ',' -> {
                        items += current.toString()
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            items += current.toString()
            return items.map { unquote(it) }.filter { it.isNotEmpty() }
        }

        private val PLAIN_SAFE = Regex("""[\p{L}\p{N}_./+\-][\p{L}\p{N}_./+\- °()]*""")

        internal fun yamlScalar(text: String): String {
            val needsQuotes = text.isEmpty() || !PLAIN_SAFE.matches(text) || text != text.trim() ||
                text.startsWith("-")
            return if (needsQuotes) "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else text
        }
    }
}
