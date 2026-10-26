package nl.markbroeders.journal.data

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DailyNoteWriterTest {

    private fun t(s: String) = LocalTime.parse(s)

    /** Asserts [after] equals [before] with exactly [old] (found once) replaced by [new]. */
    private fun assertOnlyReplaced(before: String, old: String, new: String, after: String) {
        val at = before.indexOf(old)
        assertTrue(at >= 0 && before.indexOf(old, at + 1) < 0, "section must occur exactly once")
        assertEquals(before.substring(0, at) + new + before.substring(at + old.length), after)
    }

    @Test
    fun `editing with unchanged content is byte-identical for every entry of every fixture`() {
        for (path in Fixtures.all) {
            val note = Fixtures.note(path)
            for (entry in note.entries) {
                val edited = DailyNoteWriter.editEntry(note, entry.key, entry.body, entry.fields)
                assertEquals(note.serialize(), edited.serialize(), "$path ${entry.key}")
            }
        }
    }

    @Test
    fun `editing the last entry leaves all other bytes unchanged`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        val entry = note.entry(EntryKey(t("21:40")))!!
        val edited = DailyNoteWriter.editEntry(note, entry.key, "Zonsondergang. Nu echt naar bed.")
        assertOnlyReplaced(
            note.serialize(),
            entry.raw,
            "## 21:40\nlocation:: Domburg (51.5636, 3.4972)\nweather:: 19 °C, Clear\nmood:: 4\n\n" +
                "Zonsondergang. Nu echt naar bed.\n",
            edited.serialize(),
        )
    }

    @Test
    fun `editing a middle entry keeps its trailing blank line`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        val entry = note.entry(EntryKey(t("07:15")))!!
        val edited = DailyNoteWriter.editEntry(note, entry.key, "Andere tekst\n\nmet twee alinea's\n\n\n")
        assertOnlyReplaced(
            note.serialize(),
            entry.raw,
            "## 07:15\nlocation:: Utrecht Centraal\nweather:: 17 °C, Sunny\n\nAndere tekst\n\nmet twee alinea's\n\n",
            edited.serialize(),
        )
    }

    @Test
    fun `editing one of two same-minute entries leaves the other untouched`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        val second = note.entry(EntryKey(t("12:30"), 1))!!
        val edited = DailyNoteWriter.editEntry(note, second.key, "Aangepast.")
        assertOnlyReplaced(note.serialize(), second.raw, "## 12:30\nAangepast.\n\n", edited.serialize())
        assertEquals(note.entry(EntryKey(t("12:30"), 0))!!.raw, edited.entry(EntryKey(t("12:30"), 0))!!.raw)
    }

    @Test
    fun `unknown inline fields survive edits`() {
        val note = Fixtures.note("2025/2025-06-21.md")
        val entry = note.entry(EntryKey(t("10:12")))!!
        val fields = entry.fields.map { if (it.key == "location") it.copy(value = "Amersfoort") else it }
        val edited = DailyNoteWriter.editEntry(note, entry.key, entry.body, fields)
        assertOnlyReplaced(
            note.serialize(),
            "location:: Utrecht (52.0907, 5.1214)\nweather:: 22",
            "location:: Amersfoort\nweather:: 22",
            edited.serialize(),
        )
        assertEquals(listOf("location", "weather", "people", "energy"), edited.timedEntries[0].fields.map { it.key })
    }

    @Test
    fun `body starting with a field-like line gets a blank line so it stays body`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        val edited = DailyNoteWriter.editEntry(note, EntryKey(t("18:00")), "eten:: pasta")
        val entry = edited.entry(EntryKey(t("18:00")))!!
        assertTrue(entry.fields.isEmpty())
        assertEquals("eten:: pasta", entry.body)
        assertTrue(edited.serialize().endsWith("## 18:00\n\neten:: pasta\n"))
    }

    @Test
    fun `untimed entry edit keeps its position`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        val edited = DailyNoteWriter.editEntry(note, EntryKey.UNTIMED, "Nieuwe voortekst.")
        assertOnlyReplaced(
            note.serialize(),
            "Geschreven in de trein, nog voor de eerste entry. Deze tekst hoort bij het untimed deel.",
            "Nieuwe voortekst.",
            edited.serialize(),
        )
        assertFailsWith<IllegalArgumentException> {
            DailyNoteWriter.editEntry(note, EntryKey.UNTIMED, "x", listOf(InlineField("a", "b")))
        }
    }

    @Test
    fun `untimed text can be added before existing entries`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        val edited = DailyNoteWriter.editEntry(note, EntryKey.UNTIMED, "Voortekst")
        assertEquals(
            "---\ndate: 2025-11-05\n---\nVoortekst\n\n## 12:30\n",
            edited.serialize().substringBefore("Eerste"),
        )
        assertEquals("Voortekst", edited.untimed!!.body)
    }

    @Test
    fun `backdated entry is inserted in chronological order`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        val added = DailyNoteWriter.addEntry(note, t("13:00"), "Lunch in Middelburg.")
        assertEquals(listOf(null, t("07:15"), t("13:00"), t("21:40")), added.entries.map { it.time })
        val evening = note.entry(EntryKey(t("21:40")))!!.raw
        assertOnlyReplaced(
            note.serialize(),
            evening,
            "## 13:00\n\nLunch in Middelburg.\n\n$evening",
            added.serialize(),
        )
    }

    @Test
    fun `entry earlier than all timed entries goes after the untimed preamble`() {
        val note = Fixtures.note("2025/2025-01-01.md")
        val added = DailyNoteWriter.addEntry(note, t("06:00"), "Vroeg wakker.")
        assertEquals(listOf(null, t("06:00"), t("11:05"), t("16:30")), added.entries.map { it.time })
        assertOnlyReplaced(
            note.serialize(),
            "# Nieuwjaarsdag\n\n## 11:05",
            "# Nieuwjaarsdag\n\n## 06:00\n\nVroeg wakker.\n\n## 11:05",
            added.serialize(),
        )
    }

    @Test
    fun `entry at an existing minute goes after the existing ones`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        val added = DailyNoteWriter.addEntry(note, t("12:30"), "Derde.")
        assertEquals(
            listOf(EntryKey(t("12:30"), 0), EntryKey(t("12:30"), 1), EntryKey(t("12:30"), 2), EntryKey(t("18:00"))),
            added.entries.map { it.key },
        )
        assertEquals("Derde.", added.entry(EntryKey(t("12:30"), 2))!!.body)
    }

    @Test
    fun `appending entries with fields reproduces the documented format`() {
        var note = DailyNoteParser.parse(
            "---\ndate: 2025-10-23\ntags: [journal, family]\nmood: 4\nstarred: false\n---\n",
        )
        note = DailyNoteWriter.addEntry(
            note, t("08:42"), "Morning entry, plain markdown.",
            listOf(InlineField("location", "Utrecht"), InlineField("weather", "9 °C, Cloudy")),
        )
        note = DailyNoteWriter.addEntry(
            note, t("20:11"), "Evening entry.\n\n![[2025-10-23-2011-1.jpg]]",
            listOf(InlineField("location", "Utrecht (52.0907, 5.1214)"), InlineField("weather", "11 °C, Light rain")),
        )
        assertEquals(Fixtures.text("2025/2025-10-23.md"), note.serialize())
    }

    @Test
    fun `appending to a file without trailing newline keeps it that way`() {
        val note = Fixtures.note("2026/2026-02-14.md")
        val added = DailyNoteWriter.addEntry(note, t("21:00"), "Avond.")
        assertEquals(note.serialize() + "\n\n## 21:00\n\nAvond.", added.serialize())
    }

    @Test
    fun `adding to an empty file adds no frontmatter`() {
        val note = Fixtures.note("2026/2026-04-01.md")
        val added = DailyNoteWriter.addEntry(note, t("08:00"), "Hallo.")
        assertEquals("## 08:00\n\nHallo.\n", added.serialize())
    }

    @Test
    fun `adding to a frontmatter-only file`() {
        val note = Fixtures.note("2026/2026-03-08.md")
        val added = DailyNoteWriter.addEntry(note, t("08:00"), "")
        assertEquals(note.serialize() + "\n## 08:00\n", added.serialize())
        assertEquals("", added.timedEntries.single().body)
    }

    @Test
    fun `adding to an Obsidian note with only untimed text`() {
        val note = Fixtures.note("2024/2024-03-14.md")
        val added = DailyNoteWriter.addEntry(note, t("22:00"), "Later.")
        assertEquals(note.serialize() + "\n## 22:00\n\nLater.\n", added.serialize())
        assertEquals(listOf(null, t("22:00")), added.entries.map { it.time })
    }

    @Test
    fun `deleting the only timed entry keeps the frontmatter`() {
        val note = Fixtures.note("2026/2026-02-14.md")
        val deleted = DailyNoteWriter.deleteEntry(note, EntryKey(t("09:00")))
        assertEquals("---\ndate: 2026-02-14\ntags: [journal]\n---", deleted.serialize())
        assertTrue(deleted.entries.isEmpty())
        assertFalse(deleted.isBlank)
    }

    @Test
    fun `deleting the last entry drops the separating blank line`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        val deleted = DailyNoteWriter.deleteEntry(note, EntryKey(t("21:40")))
        assertEquals(note.serialize().substringBefore("## 21:40").dropLast(1), deleted.serialize())
        assertTrue(deleted.serialize().endsWith("Trein was op tijd!\n"))
    }

    @Test
    fun `deleting a middle entry removes only that section`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        val second = note.entry(EntryKey(t("12:30"), 1))!!
        val deleted = DailyNoteWriter.deleteEntry(note, second.key)
        assertOnlyReplaced(note.serialize(), second.raw, "", deleted.serialize())
    }

    @Test
    fun `deleting everything leaves a blank note`() {
        var note = Fixtures.note("2024/2024-03-14.md")
        note = DailyNoteWriter.deleteEntry(note, EntryKey.UNTIMED)
        assertEquals("", note.serialize())
        assertTrue(note.isBlank)
    }

    @Test
    fun `frontmatter edits keep unknown keys and order`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        val edited = DailyNoteWriter.setMood(note, 3)
        assertOnlyReplaced(note.serialize(), "mood: 5\n", "mood: 3\n", edited.serialize())

        val unstarred = DailyNoteWriter.setStarred(note, false)
        assertOnlyReplaced(note.serialize(), "starred: true\n", "starred: false\n", unstarred.serialize())

        val noMood = DailyNoteWriter.setMood(note, null)
        assertOnlyReplaced(note.serialize(), "mood: 5\n", "", noMood.serialize())
    }

    @Test
    fun `new frontmatter keys are appended, empty ones are not added`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        assertSame(note, DailyNoteWriter.setStarred(note, false))
        assertSame(note, DailyNoteWriter.setTags(note, emptyList()))
        val starred = DailyNoteWriter.setStarred(note, true)
        assertOnlyReplaced(note.serialize(), "date: 2025-11-05\n", "date: 2025-11-05\nstarred: true\n", starred.serialize())
    }

    @Test
    fun `frontmatter is created only when the user sets something`() {
        val note = Fixtures.note("2024/2024-03-14.md")
        assertSame(note, DailyNoteWriter.setMood(note, null))
        val withMood = DailyNoteWriter.setMood(note, 4)
        assertEquals("---\nmood: 4\n---\n" + note.serialize(), withMood.serialize())
        assertEquals(4, withMood.mood)
    }

    @Test
    fun `tag lists keep their block or flow style`() {
        val block = Fixtures.note("2025/2025-06-21.md")
        val edited = DailyNoteWriter.setTags(block, listOf("journal", "tuin", "zomer"))
        assertOnlyReplaced(
            block.serialize(),
            "tags:\n  - journal\n  - family\n  - tuin\n",
            "tags:\n  - journal\n  - tuin\n  - zomer\n",
            edited.serialize(),
        )

        val flow = Fixtures.note("2025/2025-10-23.md")
        val flowEdited = DailyNoteWriter.setTags(flow, listOf("journal", "nieuw tag"))
        assertOnlyReplaced(
            flow.serialize(),
            "tags: [journal, family]\n",
            "tags: [journal, nieuw tag]\n",
            flowEdited.serialize(),
        )
        assertEquals(listOf("journal", "nieuw tag"), flowEdited.frontmatter!!.tags)
    }

    @Test
    fun `field values are kept on one line`() {
        val note = DailyNoteWriter.addEntry(
            DailyNoteParser.parse(""), t("10:00"), "x", listOf(InlineField("weather", "9 °C,\nCloudy")),
        )
        assertEquals("## 10:00\nweather:: 9 °C, Cloudy\n\nx\n", note.serialize())
        assertFailsWith<IllegalArgumentException> {
            DailyNoteWriter.addEntry(note, t("11:00"), "x", listOf(InlineField("bad:key", "v")))
        }
    }
}
