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
    /** Targets of the media embeds (`![[photo.jpg]]`, `![](a/b.png)`), in order. */
    val media: List<String>,
    /**
     * Where each of [media] sits: the offset in [text] it comes before. Offsets fall at the end
     * of a paragraph (or at 0), so the text can be split there; see [blocks].
     */
    val mediaOffsets: List<Int>,
    /** The section's exact source text, to detect changes before editing or deleting it. */
    val raw: String,
) {
    val mediaCount: Int get() = media.size

    /** The text and media in reading order, e.g. a paragraph, a photo, more text. */
    fun blocks(): List<EntryBlock> {
        val blocks = mutableListOf<EntryBlock>()
        var start = 0
        fun addText(end: Int) {
            val part = text.substring(start, end.coerceIn(start, text.length)).trim('\n')
            if (part.isNotEmpty()) blocks += EntryBlock.Text(part)
        }
        media.forEachIndexed { i, target ->
            val offset = mediaOffsets.getOrNull(i) ?: text.length
            addText(offset)
            start = maxOf(start, offset.coerceAtMost(text.length))
            blocks += EntryBlock.Media(i, target)
        }
        addText(text.length)
        return blocks
    }
}

sealed interface EntryBlock {
    data class Text(val text: String) : EntryBlock

    /** The [index]th of [TimelineEntry.media]. */
    data class Media(val index: Int, val target: String) : EntryBlock
}

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
        // Cut the body at each media embed, so the text around it can be shown before and after.
        val body = entry.body
        val media = Markdown.embedSpans(body).filter { Markdown.isMedia(it.target) }
        val text = StringBuilder()
        val offsets = mutableListOf<Int>()
        var start = 0
        fun addText(end: Int) {
            // After an embed the rest of its line is a new paragraph: drop the space before it.
            val part = PlainText.from(body.substring(start, end).let { if (start > 0) it.trimStart(' ', '\t') else it })
            if (part.isEmpty()) return
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(part)
        }
        for (embed in media) {
            addText(embed.range.first)
            offsets += text.length
            start = embed.range.last + 1
        }
        addText(body.length)
        return entry(entry, date, text.toString(), media.map { it.target }, offsets)
    }

    private fun entry(
        entry: Entry,
        date: LocalDate,
        text: String,
        media: List<String>,
        mediaOffsets: List<Int>,
    ): TimelineEntry {
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
            media = media,
            mediaOffsets = mediaOffsets,
            raw = entry.raw,
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
