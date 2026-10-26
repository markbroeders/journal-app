package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyNoteParserTest {

    private fun t(s: String) = LocalTime.parse(s)
    private fun keys(note: DailyNote) = note.entries.map { it.key }

    @Test
    fun `every fixture round-trips byte for byte`() {
        assertTrue(Fixtures.all.size >= 11)
        for (path in Fixtures.all) {
            assertEquals(Fixtures.text(path), Fixtures.note(path).serialize(), path)
        }
    }

    @Test
    fun `note without frontmatter or headings is one untimed entry`() {
        val note = Fixtures.note("2024/2024-03-14.md")
        assertNull(note.frontmatter)
        assertEquals(listOf(EntryKey.UNTIMED), keys(note))
        assertEquals(LocalDate.of(2024, 3, 14), note.date)
        assertTrue(note.untimed!!.body.startsWith("Rustige dag"))
        assertTrue(note.untimed.body.endsWith("- [x] verjaardagskaart oma"))
        assertEquals(listOf("werk", "wandelen"), note.tags)
    }

    @Test
    fun `preamble plus timed entries, unknown keys and per-entry mood`() {
        val note = Fixtures.note("2024/2024-07-02.md")
        assertEquals(listOf(EntryKey.UNTIMED, EntryKey(t("07:15")), EntryKey(t("21:40"))), keys(note))
        assertTrue(note.untimed!!.body.startsWith("Geschreven in de trein"))
        val fm = note.frontmatter!!
        assertEquals(listOf("aliases", "date", "tags", "cssclasses", "mood", "custom_rating", "starred"), fm.keys)
        assertEquals(FrontmatterValue.Sequence(listOf("Vakantie dag 1")), fm["aliases"])
        assertEquals(FrontmatterValue.Scalar("8/10"), fm["custom_rating"])
        assertTrue(note.starred)
        assertEquals(5, note.mood)
        assertEquals(listOf("journal", "vakantie"), note.tags)

        val morning = note.timedEntries[0]
        assertEquals("Utrecht Centraal", morning.location)
        assertEquals("17 °C, Sunny", morning.weather)
        assertNull(morning.mood)
        assertEquals("Vertrek richting Zeeland. Trein was op tijd!", morning.body)

        val evening = note.timedEntries[1]
        assertEquals(4, evening.mood)
        assertEquals("Domburg (51.5636, 3.4972)", evening.location)
    }

    @Test
    fun `other headings stay inside entries`() {
        val note = Fixtures.note("2025/2025-01-01.md")
        assertEquals(listOf(EntryKey.UNTIMED, EntryKey(t("11:05")), EntryKey(t("16:30"))), keys(note))
        assertEquals("# Nieuwjaarsdag", note.untimed!!.body)
        val first = note.timedEntries[0].body
        assertTrue("### Goede voornemens" in first)
        assertTrue("## Plannen voor januari" in first)
        assertTrue(first.endsWith("hoort bij de entry van 11:05."))
        val second = note.timedEntries[1].body
        assertTrue("#### Notitie" in second)
        assertTrue("## Meeting om 14:00" in second)
        assertTrue(note.timedEntries[0].fields.isEmpty())
    }

    @Test
    fun `block tag list, unknown inline fields and media`() {
        val note = Fixtures.note("2025/2025-06-21.md")
        assertEquals(listOf("journal", "family", "tuin"), note.frontmatter!!.tags)
        assertEquals(listOf("journal", "family", "tuin"), note.tags)
        assertEquals(4, note.mood)
        assertFalse(note.starred)

        val (morning, evening) = note.timedEntries
        assertEquals(listOf("location", "weather", "people", "energy"), morning.fields.map { it.key })
        assertEquals("[[Anna]], [[Jesse]]", morning.field("people"))
        assertEquals("high", morning.field("energy"))
        assertEquals(listOf("tuin"), morning.tags)
        assertEquals(listOf("2025-06-21-1012-1.jpg", "2025-06-21-1012-2.jpg"), morning.embeds)
        assertTrue(morning.hasMedia)

        assertEquals(5, evening.mood)
        assertEquals(listOf("2025-06-21-1932-1.jpg", "2025-06-21-1932-2.jpg"), evening.embeds)
        assertTrue(evening.hasMedia)
    }

    @Test
    fun `example from the project guide`() {
        val note = Fixtures.note("2025/2025-10-23.md")
        assertNull(note.untimed)
        assertEquals(listOf(EntryKey(t("08:42")), EntryKey(t("20:11"))), keys(note))
        assertEquals("Utrecht (52.0907, 5.1214)", note.timedEntries[1].location)
        assertEquals("11 °C, Light rain", note.timedEntries[1].weather)
        assertFalse(note.timedEntries[0].hasMedia)
        assertTrue(note.timedEntries[1].hasMedia)
        assertEquals("Evening entry.\n\n![[2025-10-23-2011-1.jpg]]", note.timedEntries[1].body)
    }

    @Test
    fun `two entries in the same minute are both addressable`() {
        val note = Fixtures.note("2025/2025-11-05.md")
        assertEquals(
            listOf(EntryKey(t("12:30"), 0), EntryKey(t("12:30"), 1), EntryKey(t("18:00"), 0)),
            keys(note),
        )
        assertTrue(note.entry(EntryKey(t("12:30"), 0))!!.body.startsWith("Eerste"))
        assertTrue(note.entry(EntryKey(t("12:30"), 1))!!.body.startsWith("Tweede"))
        assertNull(note.entry(EntryKey(t("12:30"), 2)))
    }

    @Test
    fun `lenient frontmatter types`() {
        val note = Fixtures.note("2026/2026-01-09.md")
        assertEquals(LocalDate.of(2026, 1, 9), note.date)
        assertEquals(listOf("journal"), note.tags)
        assertTrue(note.starred)
        assertEquals(listOf(EntryKey.UNTIMED), keys(note))
    }

    @Test
    fun `no trailing newline`() {
        val note = Fixtures.note("2026/2026-02-14.md")
        assertEquals(listOf(EntryKey(t("09:00"))), keys(note))
        assertEquals("Geen afsluitende newline aan het einde van dit bestand.", note.timedEntries[0].body)
    }

    @Test
    fun `frontmatter only and empty file have no entries`() {
        val fmOnly = Fixtures.note("2026/2026-03-08.md")
        assertTrue(fmOnly.entries.isEmpty())
        assertEquals(FrontmatterValue.Sequence(emptyList()), fmOnly.frontmatter!!["tags"])
        assertTrue(fmOnly.isBlank)

        val empty = Fixtures.note("2026/2026-04-01.md")
        assertTrue(empty.entries.isEmpty())
        assertNull(empty.frontmatter)
        assertEquals(LocalDate.of(2026, 4, 1), empty.date)
        assertTrue(empty.isBlank)
    }

    @Test
    fun `thematic break is not frontmatter and fenced headings are not entries`() {
        val note = Fixtures.note("2026/2026-05-17.md")
        assertNull(note.frontmatter)
        assertEquals(listOf(EntryKey.UNTIMED, EntryKey(t("14:20"))), keys(note))
        assertTrue("## 10:00" in note.untimed!!.body)
        val entry = note.timedEntries[0]
        assertTrue(entry.fields.isEmpty())
        assertTrue(entry.body.endsWith("key zonder dubbele dubbele punt: gewoon tekst"))
    }

    @Test
    fun `crlf line endings are normalised`() {
        val note = DailyNoteParser.parse("---\r\nmood: 3\r\n---\r\n## 09:00\r\nHi\r\n")
        assertEquals("---\nmood: 3\n---\n## 09:00\nHi\n", note.serialize())
        assertEquals(3, note.mood)
        assertEquals("Hi", note.timedEntries.single().body)
    }

    @Test
    fun `unclosed frontmatter is body text`() {
        val text = "---\ntitle: x\n\n## 09:00\nHi\n"
        val note = DailyNoteParser.parse(text)
        assertNull(note.frontmatter)
        assertNotNull(note.untimed)
        assertEquals(text, note.serialize())
    }

    @Test
    fun `heading time must be exactly HH colon mm`() {
        val note = DailyNoteParser.parse("## 9:00\na\n## 24:00\nb\n## 09:00 \nc\n### 10:00\nd\n")
        assertEquals(listOf(EntryKey(t("09:00"))), note.timedEntries.map { it.key })
        assertEquals("c\n### 10:00\nd", note.timedEntries[0].body)
    }

    @Test
    fun `inline tags skip code, headings, numbers and links`() {
        val note = DailyNoteParser.parse(
            "# Kop\nEen #echt tag, `#code`, #123, [[Note#Sectie]], (#haakjes) en #a/b.\n```\n#fenced\n```\n",
        )
        assertEquals(listOf("echt", "haakjes", "a/b"), note.tags)
    }

    @Test
    fun `date falls back to filename`() {
        assertEquals(LocalDate.of(2025, 1, 2), DailyNoteParser.parse("x", "Journal/2025/2025-01-02.md").date)
        assertNull(DailyNoteParser.parse("x", "notes.md").date)
        assertEquals(
            LocalDate.of(2024, 12, 31),
            DailyNoteParser.parse("---\ndate: 2024-12-31\n---\n", "2025-01-02.md").date,
        )
    }
}
