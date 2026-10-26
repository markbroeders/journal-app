package nl.markbroeders.journal.data

import java.time.LocalDate

/** One day in the timeline: day-level metadata plus its entries, newest first. */
data class TimelineDay(
    val date: LocalDate,
    val starred: Boolean,
    val mood: Int?,
    /** Frontmatter tags (inline `#tags` are on the entries). */
    val tags: List<String>,
    val entries: List<TimelineEntry>,
)

data class TimelineEntry(
    val date: LocalDate,
    val key: EntryKey,
    /** First line of the text, shown as the entry's title (empty if there is no text). */
    val title: String,
    /** The text after the title as one shortened line of prose, for list previews. */
    val preview: String,
    /** Plain text of the whole body, paragraphs separated by one blank line. */
    val text: String,
    val location: String?,
    val weather: String?,
    val mood: Int?,
    val tags: List<String>,
    val mediaCount: Int,
)

object Timeline {

    const val TITLE_LENGTH = 120
    const val PREVIEW_LENGTH = 280

    /**
     * Builds the timeline day for a note stored at [date] (the date from its path, which is
     * what identifies the file). Returns null if the note has no entries. Timed entries come
     * newest first; the untimed entry belongs to the start of the day, so it comes last.
     */
    fun day(note: DailyNote, date: LocalDate): TimelineDay? {
        val timed = note.timedEntries.sortedWith(
            compareByDescending<Entry> { it.time }.thenByDescending { it.key.ordinal },
        )
        val entries = (timed + listOfNotNull(note.untimed)).map { entry(it, date) }
        if (entries.isEmpty()) return null
        return TimelineDay(
            date = date,
            starred = note.starred,
            mood = note.mood,
            tags = note.frontmatter?.tags.orEmpty(),
            entries = entries,
        )
    }

    private fun entry(entry: Entry, date: LocalDate): TimelineEntry {
        val text = PlainText.from(entry.body)
        val lines = text.lines().filter { it.isNotBlank() }
        return TimelineEntry(
            date = date,
            key = entry.key,
            title = PlainText.shorten(lines.firstOrNull().orEmpty(), TITLE_LENGTH),
            preview = PlainText.shorten(lines.drop(1).joinToString(" "), PREVIEW_LENGTH),
            text = text,
            location = entry.location?.takeIf { it.isNotBlank() },
            weather = entry.weather?.takeIf { it.isNotBlank() },
            mood = entry.mood,
            tags = entry.tags,
            mediaCount = entry.embeds.count(Markdown::isMedia),
        )
    }
}

/** Turns Markdown into readable plain text for previews. */
object PlainText {

    private val COMMENT = Regex("""%%[\s\S]*?%%|<!--[\s\S]*?-->""")
    private val WIKI_EMBED = Regex("""!\[\[[^\]]*]]""")
    private val MD_EMBED = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val WIKI_LINK = Regex("""\[\[([^\]|#^]*)(?:[#^][^\]|]*)?(?:\|([^\]]*))?]]""")
    private val MD_LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val QUOTE = Regex("""^(>\s?)+""")
    private val TASK = Regex("""^(\s*)[-*+]\s+\[[ xX]]\s+""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val FENCE = Regex("""^\s*(`{3,}|~{3,}).*$""")
    private val STRONG = Regex("""(\*\*|__|~~|==)(.+?)\1""")
    private val EMPHASIS = Regex("""(?<![\w*])([*_])(?=\S)(.+?)(?<=\S)\1(?![\w*])""")
    private val CODE = Regex("""`+([^`]*)`+""")
    private val SPACES = Regex("""[ \t]{2,}""")
    private val BLANK_LINES = Regex("""\n{3,}""")

    fun from(markdown: String): String {
        val text = markdown.replace(COMMENT, "")
        val lines = text.lines().mapNotNull { raw ->
            if (FENCE.matches(raw) || RULE.matches(raw)) return@mapNotNull null
            var line = raw
                .replace(WIKI_EMBED, "")
                .replace(MD_EMBED, "")
                .replace(WIKI_LINK) { m -> m.groupValues[2].ifEmpty { m.groupValues[1] } }
                .replace(MD_LINK, "$1")
                .replace(CODE, "$1")
            line = line.replace(QUOTE, "").replace(HEADING, "").replace(TASK, "$1").replace(BULLET, "$1• ")
            line = line.replace(STRONG, "$2").replace(EMPHASIS, "$2")
            // Lines emptied by removing syntax (e.g. an embed) are dropped; blank lines stay as breaks.
            line.replace(SPACES, " ").trimEnd().takeIf { it.isNotEmpty() || raw.isBlank() }
        }
        // Keep paragraph breaks, but at most one blank line (removed embeds leave empty lines).
        return lines.joinToString("\n").replace(BLANK_LINES, "\n\n").trim('\n')
    }

    /** Cuts [text] to at most [max] characters at a word boundary, adding an ellipsis. */
    fun shorten(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = text.substring(0, max)
        val space = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
        return (if (space > max / 2) cut.substring(0, space) else cut).trimEnd() + "…"
    }
}
