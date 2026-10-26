package nl.markbroeders.journal.data

import java.time.LocalTime

/** What the editor wants to do with one entry. */
sealed interface EntryChange {
    /** Adds a new `## HH:mm` entry. */
    data class Add(val time: LocalTime, val body: String, val fields: List<InlineField> = emptyList()) : EntryChange

    /**
     * Replaces an entry's body and fields. [originalKey] and [originalRaw] describe the entry
     * as it was when the editor opened it, so concurrent changes to it can be detected.
     */
    data class Edit(
        val originalKey: EntryKey,
        val originalRaw: String,
        val body: String,
        val fields: List<InlineField>,
    ) : EntryChange

    data class Delete(val originalKey: EntryKey, val originalRaw: String) : EntryChange

    /** Only day-level values change. */
    data object None : EntryChange
}

/** Day-level (frontmatter) values the user set; null means "leave as it is on disk". */
data class DayEdit(val starred: Boolean? = null, val tags: List<String>? = null)

data class NoteEdit(val entry: EntryChange, val day: DayEdit = DayEdit())

sealed interface ApplyResult {
    /** [key] identifies the added or edited entry in [note], if there is one. */
    data class Applied(val note: DailyNote, val key: EntryKey?) : ApplyResult

    /** The entry being edited or deleted changed (or vanished) since the editor opened it. */
    data object Conflict : ApplyResult
}

/**
 * Applies an editor change to the note as it is on disk *now*. Adding always merges. Editing
 * or deleting only goes ahead if the entry's text is still exactly what the editor opened
 * (changes elsewhere in the file don't matter); otherwise it's a [ApplyResult.Conflict],
 * unless [force] is set, in which case the user's version wins.
 */
object NoteEdits {

    fun apply(current: DailyNote, edit: NoteEdit, force: Boolean = false): ApplyResult {
        var note = current
        var key: EntryKey? = null
        when (val change = edit.entry) {
            is EntryChange.Add -> {
                val ordinal = note.timedEntries.count { it.time == change.time }
                note = DailyNoteWriter.addEntry(note, change.time, change.body, change.fields)
                key = EntryKey(change.time, ordinal)
            }
            is EntryChange.Edit -> {
                val target = find(note, change.originalKey, change.originalRaw)
                    ?: if (force) note.entry(change.originalKey)?.key else return ApplyResult.Conflict
                if (target != null) {
                    note = DailyNoteWriter.editEntry(note, target, change.body, if (target.isUntimed) emptyList() else change.fields)
                    key = target
                } else {
                    // Forced, and the entry is gone: re-create it.
                    val time = change.originalKey.time
                    if (time == null) {
                        note = DailyNoteWriter.editEntry(note, EntryKey.UNTIMED, change.body, emptyList())
                        key = EntryKey.UNTIMED
                    } else {
                        val ordinal = note.timedEntries.count { it.time == time }
                        note = DailyNoteWriter.addEntry(note, time, change.body, change.fields)
                        key = EntryKey(time, ordinal)
                    }
                }
            }
            is EntryChange.Delete -> {
                val target = find(note, change.originalKey, change.originalRaw)
                    ?: if (force) note.entry(change.originalKey)?.key else return ApplyResult.Conflict
                if (target != null) note = DailyNoteWriter.deleteEntry(note, target)
            }
            EntryChange.None -> {}
        }
        edit.day.starred?.let { if (it != note.starred) note = DailyNoteWriter.setStarred(note, it) }
        edit.day.tags?.let { if (it != note.frontmatter?.tags.orEmpty()) note = DailyNoteWriter.setTags(note, it) }
        return ApplyResult.Applied(note, key)
    }

    /**
     * Finds the entry the editor opened: same time and identical text. Prefers the original
     * ordinal, but tolerates the ordinal shifting when a same-minute entry was added earlier.
     */
    private fun find(note: DailyNote, key: EntryKey, raw: String): EntryKey? {
        if (key.isUntimed) return note.untimed?.takeIf { it.raw == raw }?.key
        val matches = note.timedEntries.filter { it.time == key.time && it.raw == raw }
        return (matches.firstOrNull { it.key == key } ?: matches.firstOrNull())?.key
    }
}
