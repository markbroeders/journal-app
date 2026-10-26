package nl.markbroeders.journal.data

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NoteEditsTest {

    private fun t(s: String) = LocalTime.parse(s)
    private fun applied(r: ApplyResult) = assertIs<ApplyResult.Applied>(r)

    private val note = Fixtures.note("2024/2024-07-02.md")
    private val evening = note.entry(EntryKey(t("21:40")))!!

    private fun editEvening(body: String) =
        NoteEdit(EntryChange.Edit(evening.key, evening.raw, body, evening.fields))

    @Test
    fun `edit applies when the entry is unchanged on disk`() {
        val r = applied(NoteEdits.apply(note, editEvening("Nieuw.")))
        assertEquals(EntryKey(t("21:40")), r.key)
        assertEquals("Nieuw.", r.note.entry(r.key!!)!!.body)
    }

    @Test
    fun `edit merges with changes elsewhere in the file`() {
        // Someone edited the 07:15 entry in Obsidian after the editor opened 21:40.
        val changedElsewhere = DailyNoteWriter.editEntry(note, EntryKey(t("07:15")), "Obsidian-tekst.")
        val r = applied(NoteEdits.apply(changedElsewhere, editEvening("Nieuw.")))
        assertEquals("Obsidian-tekst.", r.note.entry(EntryKey(t("07:15")))!!.body)
        assertEquals("Nieuw.", r.note.entry(EntryKey(t("21:40")))!!.body)
    }

    @Test
    fun `edit conflicts when the entry itself changed, unless forced`() {
        val changed = DailyNoteWriter.editEntry(note, evening.key, "Ook in Obsidian aangepast.")
        assertSame(ApplyResult.Conflict, NoteEdits.apply(changed, editEvening("Nieuw.")))
        val forced = applied(NoteEdits.apply(changed, editEvening("Nieuw."), force = true))
        assertEquals("Nieuw.", forced.note.entry(evening.key)!!.body)
    }

    @Test
    fun `edit of a vanished entry conflicts, and forcing re-creates it`() {
        val gone = DailyNoteWriter.deleteEntry(note, evening.key)
        assertSame(ApplyResult.Conflict, NoteEdits.apply(gone, editEvening("Nieuw.")))
        val forced = applied(NoteEdits.apply(gone, editEvening("Nieuw."), force = true))
        assertEquals("Nieuw.", forced.note.entry(EntryKey(t("21:40")))!!.body)
        assertEquals(evening.fields, forced.note.entry(EntryKey(t("21:40")))!!.fields)
    }

    @Test
    fun `entry is found when its ordinal shifted`() {
        val n = Fixtures.note("2025/2025-11-05.md")
        val second = n.entry(EntryKey(t("12:30"), 1))!!
        // Remove the first 12:30 so the second becomes ordinal 0.
        val shifted = DailyNoteWriter.deleteEntry(n, EntryKey(t("12:30"), 0))
        val r = applied(NoteEdits.apply(shifted, NoteEdit(EntryChange.Edit(second.key, second.raw, "Aangepast.", emptyList()))))
        assertEquals(EntryKey(t("12:30"), 0), r.key)
        assertEquals(listOf("Aangepast.", "Avondeten."), r.note.timedEntries.map { it.body })
    }

    @Test
    fun `add always merges and reports the new key`() {
        val n = Fixtures.note("2025/2025-11-05.md")
        val r = applied(NoteEdits.apply(n, NoteEdit(EntryChange.Add(t("12:30"), "Derde."))))
        assertEquals(EntryKey(t("12:30"), 2), r.key)
        assertEquals("Derde.", r.note.entry(r.key!!)!!.body)
    }

    @Test
    fun `delete conflicts when the entry changed`() {
        val change = NoteEdit(EntryChange.Delete(evening.key, evening.raw))
        val ok = applied(NoteEdits.apply(note, change))
        assertEquals(listOf(null, t("07:15")), ok.note.entries.map { it.time })
        val changed = DailyNoteWriter.editEntry(note, evening.key, "Anders.")
        assertSame(ApplyResult.Conflict, NoteEdits.apply(changed, change))
    }

    @Test
    fun `untimed entry edit`() {
        val untimed = note.untimed!!
        val r = applied(NoteEdits.apply(note, NoteEdit(EntryChange.Edit(EntryKey.UNTIMED, untimed.raw, "Voortekst.", emptyList()))))
        assertEquals("Voortekst.", r.note.untimed!!.body)
    }

    @Test
    fun `day edits only touch what changed`() {
        val same = applied(NoteEdits.apply(note, NoteEdit(EntryChange.None, DayEdit(starred = true, tags = listOf("journal", "vakantie")))))
        assertEquals(note.serialize(), same.note.serialize())
        val changed = applied(NoteEdits.apply(note, NoteEdit(EntryChange.None, DayEdit(starred = false))))
        assertTrue("starred: false\n" in changed.note.serialize())
        val fresh = DailyNoteParser.parse("")
        val noStar = applied(NoteEdits.apply(fresh, NoteEdit(EntryChange.Add(t("09:00"), "Hoi"), DayEdit(starred = false, tags = emptyList()))))
        assertEquals("## 09:00\n\nHoi\n", noStar.note.serialize())
    }
}
