package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.LocalTime

/** A Dataview inline field (`key:: value`) directly under an entry heading. */
data class InlineField(val key: String, val value: String)

/**
 * Identifies an entry within a day. [time] is null for the untimed entry (text before the
 * first `## HH:mm` heading); [ordinal] tells apart entries that share the same minute.
 */
data class EntryKey(val time: LocalTime?, val ordinal: Int = 0) {
    val isUntimed: Boolean get() = time == null

    companion object {
        val UNTIMED = EntryKey(null, 0)
    }
}

/**
 * One entry of a daily note. [raw] is the exact source text of the section, which is split
 * into [heading] + [fieldBlock] + [gap] + [core] + [tail] (concatenation equals [raw]).
 */
class Entry internal constructor(
    val key: EntryKey,
    val fields: List<InlineField>,
    val raw: String,
    internal val heading: String,
    internal val fieldBlock: String,
    internal val gap: String,
    internal val core: String,
    internal val tail: String,
) {
    val time: LocalTime? get() = key.time

    /** Body text without the heading, inline fields and surrounding blank lines. */
    val body: String get() = core

    /** Inline `#tags` in this entry, outside code. */
    val tags: List<String> by lazy { Markdown.inlineTags(fieldBlock + gap + core) }

    /** Targets of `![[…]]` and `![](…)` embeds in this entry. */
    val embeds: List<String> by lazy { Markdown.embeds(core) }

    val hasMedia: Boolean get() = embeds.any(Markdown::isMedia)

    fun field(key: String): String? = fields.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    val location: String? get() = field("location")
    val weather: String? get() = field("weather")
    val mood: Int? get() = field("mood")?.trim()?.toIntOrNull()?.takeIf { it in 1..5 }

    override fun toString(): String = "Entry($key, fields=$fields, body=${core.take(40)})"
}

/**
 * A parsed daily note. The source text is held as segments whose concatenation is the
 * (line-ending-normalised) file: [frontmatter] raw + [preambleRaw] + every timed entry's raw.
 */
class DailyNote internal constructor(
    /** Date used when the frontmatter has none, normally taken from the filename. */
    val fallbackDate: LocalDate?,
    val frontmatter: Frontmatter?,
    /** Everything between the frontmatter and the first `## HH:mm` heading. */
    internal val preambleRaw: String,
    /** The untimed entry, or null when the preamble is blank. */
    val untimed: Entry?,
    /** `## HH:mm` entries in file order. */
    val timedEntries: List<Entry>,
) {
    /** Untimed entry (if any) followed by the timed entries, in file order. */
    val entries: List<Entry> get() = listOfNotNull(untimed) + timedEntries

    val date: LocalDate? get() = frontmatter?.date ?: fallbackDate

    val mood: Int? get() = frontmatter?.mood

    val starred: Boolean get() = frontmatter?.starred ?: false

    /** Frontmatter tags plus inline `#tags` from all entries, de-duplicated case-insensitively. */
    val tags: List<String>
        get() = Markdown.distinctTags(frontmatter?.tags.orEmpty() + entries.flatMap { it.tags })

    fun entry(key: EntryKey): Entry? =
        if (key.isUntimed) untimed else timedEntries.firstOrNull { it.key == key }

    /**
     * True when nothing the user wrote remains: no entry text and no frontmatter values
     * other than `date`. Callers should still confirm before deleting the file.
     */
    val isBlank: Boolean
        get() = untimed == null && timedEntries.isEmpty() && preambleRaw.isBlank() &&
            (frontmatter == null || frontmatter.keys.all { it == "date" || frontmatter[it]?.isEmpty != false })

    fun serialize(): String = buildString {
        frontmatter?.let { append(it.raw) }
        append(preambleRaw)
        timedEntries.forEach { append(it.raw) }
    }

    override fun toString(): String = "DailyNote(date=$date, entries=$entries)"
}
