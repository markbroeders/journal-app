package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Parses a daily note into frontmatter, an untimed preamble and `## HH:mm` entries,
 * keeping the exact source text of every part so [DailyNote.serialize] round-trips.
 */
object DailyNoteParser {

    private val TIME_HEADING = Regex("""## ([01]\d|2[0-3]):([0-5]\d)[ \t]*""")
    internal val FIELD_LINE = Regex("""([\p{L}\p{N}_][\p{L}\p{N}_\- ]*?)::(?:[ \t]*(.*))?""")
    private val FILE_DATE = Regex("""(\d{4}-\d{2}-\d{2})""")

    fun parse(text: String, fallbackDate: LocalDate? = null): DailyNote {
        val lines = Markdown.splitLines(normalizeLineEndings(text))

        val fmEnd = frontmatterEnd(lines)
        val frontmatter = if (fmEnd > 0) Frontmatter(lines.subList(0, fmEnd).joinToString("")) else null

        val fence = Markdown.FenceTracker()
        val headingLines = (fmEnd until lines.size).filter { i ->
            !fence.accept(lines[i]) && TIME_HEADING.matches(lines[i].removeSuffix("\n"))
        }

        val firstHeading = headingLines.firstOrNull() ?: lines.size
        val preamble = lines.subList(fmEnd, firstHeading).joinToString("")

        val seen = HashMap<LocalTime, Int>()
        val entries = headingLines.mapIndexed { n, start ->
            val end = headingLines.getOrNull(n + 1) ?: lines.size
            val section = lines.subList(start, end)
            val m = TIME_HEADING.matchEntire(section[0].removeSuffix("\n"))!!
            val time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            val ordinal = seen.merge(time, 1, Int::plus)!! - 1
            timedEntry(EntryKey(time, ordinal), section)
        }

        return DailyNote(
            fallbackDate = fallbackDate,
            frontmatter = frontmatter,
            preambleRaw = preamble,
            untimed = if (preamble.isBlank()) null else untimedEntry(preamble),
            timedEntries = entries,
        )
    }

    /** Parses using the `YYYY-MM-DD` in [fileName] as the fallback date. */
    fun parse(text: String, fileName: String): DailyNote = parse(text, dateFromFileName(fileName))

    fun dateFromFileName(fileName: String): LocalDate? =
        FILE_DATE.find(fileName.substringAfterLast('/'))?.let {
            try {
                LocalDate.parse(it.value)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    fun normalizeLineEndings(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    /** Index of the first line after the frontmatter block, or 0 if there is none. */
    private fun frontmatterEnd(lines: List<String>): Int {
        if (lines.isEmpty() || lines[0].removeSuffix("\n").trimEnd() != "---") return 0
        for (i in 1 until lines.size) {
            val l = lines[i].removeSuffix("\n").trimEnd()
            if (l == "---" || l == "...") return i + 1
        }
        return 0
    }

    private fun timedEntry(key: EntryKey, section: List<String>): Entry {
        var i = 1
        val fields = mutableListOf<InlineField>()
        while (i < section.size) {
            val m = FIELD_LINE.matchEntire(section[i].removeSuffix("\n")) ?: break
            fields += InlineField(m.groupValues[1].trim(), m.groupValues[2].trim())
            i++
        }
        val (gap, core, tail) = splitBody(section.subList(i, section.size))
        return Entry(
            key = key,
            fields = fields,
            raw = section.joinToString(""),
            heading = section[0],
            fieldBlock = section.subList(1, i).joinToString(""),
            gap = gap,
            core = core,
            tail = tail,
        )
    }

    private fun untimedEntry(preamble: String): Entry {
        val (gap, core, tail) = splitBody(Markdown.splitLines(preamble))
        return Entry(EntryKey.UNTIMED, emptyList(), preamble, "", "", gap, core, tail)
    }

    /**
     * Splits body lines into leading blank lines, the core text (first to last non-blank
     * line, without its final newline) and the rest.
     */
    internal fun splitBody(lines: List<String>): Triple<String, String, String> {
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first < 0) return Triple(lines.joinToString(""), "", "")
        val last = lines.indexOfLast { it.isNotBlank() }
        val coreLines = lines.subList(first, last + 1).joinToString("")
        val core = coreLines.removeSuffix("\n")
        val tail = (if (coreLines.endsWith("\n")) "\n" else "") + lines.subList(last + 1, lines.size).joinToString("")
        return Triple(lines.subList(0, first).joinToString(""), core, tail)
    }
}
