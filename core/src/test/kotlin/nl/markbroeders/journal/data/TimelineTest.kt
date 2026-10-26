package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTest {

    private fun t(s: String) = LocalTime.parse(s)
    private fun day(path: String) =
        Timeline.day(Fixtures.note(path), VaultPaths.dateOfDailyNote(path.substringAfter('/'))!!)

    @Test
    fun `entries are newest first with the untimed entry last`() {
        val d = day("2024/2024-07-02.md")!!
        assertEquals(listOf(t("21:40"), t("07:15"), null), d.entries.map { it.key.time })
        assertTrue(d.starred)
        assertEquals(5, d.mood)
        assertEquals(listOf("journal", "vakantie"), d.tags)
        assertEquals(4, d.entries[0].mood)
        assertEquals("Domburg (51.5636, 3.4972)", d.entries[0].location)
        assertEquals("19 °C, Clear", d.entries[0].weather)
    }

    @Test
    fun `same-minute entries keep their identity`() {
        val d = day("2025/2025-11-05.md")!!
        assertEquals(
            listOf(EntryKey(t("18:00")), EntryKey(t("12:30"), 1), EntryKey(t("12:30"), 0)),
            d.entries.map { it.key },
        )
    }

    @Test
    fun `out-of-order sections are sorted`() {
        val note = DailyNoteParser.parse("## 20:00\nlate\n\n## 08:00\nearly\n")
        val d = Timeline.day(note, LocalDate.of(2026, 1, 1))!!
        assertEquals(listOf("late", "early"), d.entries.map { it.text })
    }

    @Test
    fun `days without entries are skipped`() {
        assertNull(day("2026/2026-03-08.md"))
        assertNull(day("2026/2026-04-01.md"))
    }

    @Test
    fun `media count and tags`() {
        val d = day("2025/2025-06-21.md")!!
        assertEquals(listOf(2, 2), d.entries.map { it.mediaCount })
        assertEquals(listOf("tuin"), d.entries[1].tags)
        assertEquals("BBQ met familie in de tuin.\n\nNog een losse foto met gewone markdown-syntax:", d.entries[0].text)
        assertEquals("BBQ met familie in de tuin.\nNog een losse foto met gewone markdown-syntax:", d.entries[0].snippet)
    }

    @Test
    fun `date comes from the file, not the frontmatter`() {
        val note = DailyNoteParser.parse("---\ndate: 2020-01-01\n---\nText\n")
        assertEquals(LocalDate.of(2026, 2, 2), Timeline.day(note, LocalDate.of(2026, 2, 2))!!.date)
    }

    @Test
    fun `every fixture produces a day or is blank`() {
        for (path in Fixtures.all) {
            val note = Fixtures.note(path)
            assertEquals(note.entries.isEmpty(), day(path) == null, path)
        }
    }
}

class PlainTextTest {

    @Test
    fun `markdown syntax is removed`() {
        val md = """
            # Kop
            Een **vet** en *schuin* woord, `code`, ==highlight== en ~~weg~~.
            Link naar [[Anna]], [[Jesse|J.]], [[Plan#Week 1]] en [site](https://x.nl).
            ![[foto.jpg]]
            > citaat
            - [ ] taak
            - punt
            ---
            ```kotlin
            val x = 1
            ```
            %% verborgen %%<!-- ook verborgen -->snake_case_naam blijft.
        """.trimIndent()
        assertEquals(
            listOf(
                "Kop",
                "Een vet en schuin woord, code, highlight en weg.",
                "Link naar Anna, J., Plan en site.",
                "citaat",
                "taak",
                "• punt",
                "val x = 1",
                "snake_case_naam blijft.",
            ).joinToString("\n"),
            PlainText.from(md),
        )
    }

    @Test
    fun `paragraph breaks are kept but collapsed`() {
        assertEquals("een\n\ntwee\ndrie", PlainText.from("\n\neen\n\n![[x.jpg]]\n\n\ntwee\ndrie\n\n"))
    }

    @Test
    fun `shorten cuts at a word boundary`() {
        assertEquals("kort", PlainText.shorten("kort", 10))
        assertEquals("een twee…", PlainText.shorten("een twee drie vier", 12))
        assertEquals("abcdefghij…", PlainText.shorten("abcdefghijklmnop", 10))
    }
}
