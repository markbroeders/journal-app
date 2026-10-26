package nl.markbroeders.journal.data

import java.time.LocalTime

/**
 * Section-level edits on a [DailyNote]. Every operation rebuilds the file text by replacing
 * only the affected segment, so all other bytes stay identical, and returns the re-parsed note.
 *
 * Body text passed in is treated as plain Markdown; surrounding blank lines are dropped.
 * Note that a body line of the form `## HH:mm` will start a new entry when the file is
 * read back, exactly as it would in Obsidian.
 */
object DailyNoteWriter {

    /**
     * Adds a `## HH:mm` entry in chronological order: after the untimed preamble and after
     * any existing entries at the same minute or earlier.
     */
    fun addEntry(
        note: DailyNote,
        time: LocalTime,
        body: String,
        fields: List<InlineField> = emptyList(),
    ): DailyNote {
        val original = note.serialize()
        val insertAt = note.timedEntries.indexOfFirst { it.time!! > time }
        val after = if (insertAt < 0) "" else note.timedEntries.drop(insertAt).joinToString("") { it.raw }
        val before = original.substring(0, original.length - after.length)

        val separator = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val section = renderEntry(time, fields, normalizeBody(body))
        val text = before + separator + section + (if (after.isEmpty()) "" else "\n") + after
        return finish(note, original, text)
    }

    /** Replaces the fields and body of one entry. For the untimed entry, [fields] must be empty. */
    fun editEntry(
        note: DailyNote,
        key: EntryKey,
        body: String,
        fields: List<InlineField> = note.entry(key)?.fields.orEmpty(),
    ): DailyNote {
        if (key.isUntimed) {
            require(fields.isEmpty()) { "The untimed entry has no inline fields" }
            return editUntimed(note, normalizeBody(body))
        }
        val entry = requireNotNull(note.entry(key)) { "No entry $key" }
        val core = normalizeBody(body)

        val heading = entry.heading.ensureNewline()
        val fieldBlock = (if (fields == entry.fields) entry.fieldBlock else renderFields(fields)).let {
            if (it.isEmpty()) it else it.ensureNewline()
        }
        val keepGap = entry.core.isNotEmpty() &&
            entry.fieldBlock.isEmpty() == fieldBlock.isEmpty() &&
            (entry.gap.isNotEmpty() || !looksLikeField(core))
        val gap = when {
            core.isEmpty() -> ""
            keepGap -> entry.gap
            else -> "\n"
        }
        val content = heading + fieldBlock + gap + (if (core.isEmpty()) "" else core + "\n")
        val (_, trailer) = splitTrailer(entry.raw)
        return replaceEntry(note, entry, content + trailer)
    }

    /** Removes one entry. The caller decides (with confirmation) whether to delete a blank file. */
    fun deleteEntry(note: DailyNote, key: EntryKey): DailyNote {
        val original = note.serialize()
        if (key.isUntimed) {
            if (note.untimed == null) return note
            val preamble = if (note.timedEntries.isNotEmpty() && note.frontmatter != null) "\n" else ""
            return finish(note, original, rebuild(note, preamble = preamble))
        }
        val entry = requireNotNull(note.entry(key)) { "No entry $key" }
        if (entry !== note.timedEntries.last()) return replaceEntry(note, entry, "")
        // Last section: also drop the blank lines that separated it from the previous one.
        val before = original.substring(0, original.length - entry.raw.length)
        return finish(note, original, splitTrailer(before).first)
    }

    /** Sets a frontmatter key in place, or appends it; null removes it. Creates frontmatter if needed. */
    fun setFrontmatterValue(note: DailyNote, key: String, value: FrontmatterValue?): DailyNote {
        val fm = note.frontmatter
        if (fm == null && value == null) return note
        val newRaw = (fm ?: Frontmatter("---\n---\n")).withValue(key, value)
        return finish(note, note.serialize(), rebuild(note, frontmatter = newRaw))
    }

    fun setMood(note: DailyNote, mood: Int?): DailyNote {
        require(mood == null || mood in 1..5) { "Mood must be 1–5" }
        return setFrontmatterValue(note, "mood", mood?.let { FrontmatterValue.Scalar(it.toString()) })
    }

    /** Sets `starred`; never adds `starred: false` to a note that doesn't have the key. */
    fun setStarred(note: DailyNote, starred: Boolean): DailyNote {
        if (!starred && note.frontmatter?.get("starred") == null) return note
        return setFrontmatterValue(note, "starred", FrontmatterValue.Scalar(starred.toString()))
    }

    /** Sets the frontmatter `tags`; never adds an empty `tags` key. Inline `#tags` are untouched. */
    fun setTags(note: DailyNote, tags: List<String>): DailyNote {
        if (tags.isEmpty() && note.frontmatter?.get("tags") == null) return note
        return setFrontmatterValue(note, "tags", FrontmatterValue.Sequence(tags.map { it.removePrefix("#") }))
    }

    private fun editUntimed(note: DailyNote, core: String): DailyNote {
        if (core.isEmpty()) return deleteEntry(note, EntryKey.UNTIMED)
        val untimed = note.untimed
        val preamble = if (untimed != null) {
            untimed.gap + core + untimed.tail
        } else {
            core + "\n" + if (note.timedEntries.isEmpty()) "" else note.preambleRaw.ifEmpty { "\n" }
        }
        return finish(note, note.serialize(), rebuild(note, preamble = preamble))
    }

    private fun replaceEntry(note: DailyNote, entry: Entry, raw: String): DailyNote =
        finish(
            note,
            note.serialize(),
            rebuild(note, entries = note.timedEntries.map { if (it === entry) raw else it.raw }),
        )

    private fun rebuild(
        note: DailyNote,
        frontmatter: String = note.frontmatter?.raw.orEmpty(),
        preamble: String = note.preambleRaw,
        entries: List<String> = note.timedEntries.map { it.raw },
    ): String = frontmatter + preamble + entries.joinToString("")

    /** Keeps the file's trailing-newline behaviour and re-parses. */
    private fun finish(note: DailyNote, original: String, text: String): DailyNote {
        val result = if (original.isNotEmpty() && !original.endsWith("\n")) text.trimEnd('\n') else text
        return DailyNoteParser.parse(result, note.fallbackDate)
    }

    internal fun renderEntry(time: LocalTime, fields: List<InlineField>, core: String): String =
        "## %02d:%02d\n".format(time.hour, time.minute) + renderFields(fields) +
            (if (core.isEmpty()) "" else "\n" + core + "\n")

    private fun renderFields(fields: List<InlineField>): String = fields.joinToString("") { f ->
        val key = f.key.trim()
        require(DailyNoteParser.FIELD_LINE.matches("$key::")) { "Invalid field key: '${f.key}'" }
        val value = f.value.replace(Regex("""\s*\n\s*"""), " ").trim()
        if (value.isEmpty()) "$key::\n" else "$key:: $value\n"
    }

    private fun normalizeBody(body: String): String =
        DailyNoteParser.splitBody(Markdown.splitLines(DailyNoteParser.normalizeLineEndings(body))).second

    private fun looksLikeField(core: String): Boolean =
        DailyNoteParser.FIELD_LINE.matches(core.substringBefore('\n'))

    /** Splits [s] after its last non-blank line (including that line's newline). */
    private fun splitTrailer(s: String): Pair<String, String> {
        val lines = Markdown.splitLines(s)
        val last = lines.indexOfLast { it.isNotBlank() }
        val content = lines.subList(0, last + 1).joinToString("")
        return content to s.substring(content.length)
    }

    private fun String.ensureNewline(): String = if (endsWith("\n")) this else this + "\n"
}
