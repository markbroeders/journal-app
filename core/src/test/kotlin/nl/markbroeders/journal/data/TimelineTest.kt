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
    fun `title is the first line and preview the rest`() {
        val d = day("2024/2024-03-14.md")!!.entries.single()
        assertTrue(d.title.startsWith("Rustige dag thuis gewerkt."))
        assertEquals(
            "Avond: boek uitgelezen (Klara and the Sun). Mooi einde. bibliotheekboeken terugbrengen verjaardagskaart oma",
            d.preview,
        )
        val empty = Timeline.day(DailyNoteParser.parse("## 09:00\n"), LocalDate.of(2026, 1, 1))!!.entries.single()
        assertEquals("", empty.title)
        assertEquals("", empty.preview)
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
        assertEquals(listOf("2025-06-21-1932-1.jpg", "2025-06-21-1932-2.jpg"), d.entries[0].media)
        assertEquals(listOf("tuin"), d.entries[1].tags)
        assertEquals("BBQ met familie in de tuin.\n\nNog een losse foto met gewone markdown-syntax:", d.entries[0].text)
        assertEquals("BBQ met familie in de tuin.", d.entries[0].title)
        assertEquals("Nog een losse foto met gewone markdown-syntax:", d.entries[0].preview)
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

    private fun entry(body: String) =
        Timeline.day(DailyNoteParser.parse("## 09:00\n$body"), LocalDate.of(2026, 1, 1))!!.entries.single()

    @Test
    fun `photos are placed where the text embeds them`() {
        val e = entry("Before.\n\n![[a.jpg]]\n\nMiddle.\n![[b.jpg]]\n\n![](c%20d.png)\n\nAfter.\n")
        assertEquals("Before.\n\nMiddle.\n\nAfter.", e.text)
        assertEquals(listOf("a.jpg", "b.jpg", "c%20d.png"), e.media)
        assertEquals(
            listOf(
                EntryBlock.Text("Before."),
                EntryBlock.Media(0, "a.jpg"),
                EntryBlock.Text("Middle."),
                EntryBlock.Media(1, "b.jpg"),
                EntryBlock.Media(2, "c%20d.png"),
                EntryBlock.Text("After."),
            ),
            e.blocks(),
        )
    }

    @Test
    fun `a photo first, last or alone`() {
        assertEquals(
            listOf(EntryBlock.Media(0, "a.jpg"), EntryBlock.Text("Text."), EntryBlock.Media(1, "b.jpg")),
            entry("![[a.jpg]]\nText.\n\n![[b.jpg]]\n").blocks(),
        )
        val alone = entry("![[a.jpg|300]]\n")
        assertEquals("", alone.text)
        assertEquals(listOf(EntryBlock.Media(0, "a.jpg")), alone.blocks())
    }

    @Test
    fun `an embed inside a paragraph splits it`() {
        assertEquals(
            listOf(EntryBlock.Text("See"), EntryBlock.Media(0, "a.jpg"), EntryBlock.Text("here.")),
            entry("See ![[a.jpg]] here.\n").blocks(),
        )
    }

    @Test
    fun `embeds in code or comments and links to notes are not photos`() {
        val e = entry("![[Other note]]\n\n`![[x.jpg]]`\n\n```\n![[y.jpg]]\n```\n\n%% ![[z.jpg]] %%\n\nEnd ![[ok.jpg]]\n")
        assertEquals(listOf("ok.jpg"), e.media)
        val blocks = e.blocks()
        assertEquals(EntryBlock.Media(0, "ok.jpg"), blocks.last())
        assertTrue((blocks[blocks.size - 2] as EntryBlock.Text).text.endsWith("End"))
    }

    @Test
    fun `without embeds the text is unchanged`() {
        val d = day("2024/2024-03-14.md")!!.entries.single()
        assertEquals(listOf(EntryBlock.Text(d.text)), d.blocks())
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
