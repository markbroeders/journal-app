package nl.markbroeders.journal.data

import nl.markbroeders.journal.data.MdBlock.Callout
import nl.markbroeders.journal.data.MdBlock.Code
import nl.markbroeders.journal.data.MdBlock.Heading
import nl.markbroeders.journal.data.MdBlock.ListBlock
import nl.markbroeders.journal.data.MdBlock.Paragraph
import nl.markbroeders.journal.data.MdBlock.Quote
import nl.markbroeders.journal.data.MdBlock.Rule
import nl.markbroeders.journal.data.MdBlock.Table
import nl.markbroeders.journal.data.MdInline.Bold
import nl.markbroeders.journal.data.MdInline.Embed
import nl.markbroeders.journal.data.MdInline.Highlight
import nl.markbroeders.journal.data.MdInline.Italic
import nl.markbroeders.journal.data.MdInline.LineBreak
import nl.markbroeders.journal.data.MdInline.Link
import nl.markbroeders.journal.data.MdInline.Strike
import nl.markbroeders.journal.data.MdInline.Tag
import nl.markbroeders.journal.data.MdInline.Text
import nl.markbroeders.journal.data.MdInline.WikiLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownDocTest {

    private fun p(vararg content: MdInline) = Paragraph(content.toList())
    private fun t(s: String) = Text(s)
    private fun inl(s: String) = MarkdownDoc.inlines(s)

    @Test
    fun `paragraphs with line breaks`() {
        assertEquals(
            listOf(p(t("een"), LineBreak, t("twee")), p(t("drie"))),
            MarkdownDoc.parse("een  \ntwee\n\n\ndrie\n"),
        )
    }

    @Test
    fun `headings, rules and setext`() {
        assertEquals(
            listOf(
                Heading(1, listOf(t("Titel"))),
                Heading(3, listOf(t("Sub"))),
                Rule,
                Heading(2, listOf(t("Setext"))),
                Paragraph(listOf(Tag("tag"), t(" is no heading"))),
            ),
            MarkdownDoc.parse("# Titel\n### Sub ###\n***\n\nSetext\n---\n\n#tag is no heading\n"),
        )
    }

    @Test
    fun `emphasis, strike, highlight and code`() {
        assertEquals(
            listOf(
                Bold(listOf(t("vet"))), t(" "), Italic(listOf(t("schuin"))), t(" "),
                Strike(listOf(t("weg"))), t(" "), Highlight(listOf(t("geel"))), t(" "),
                MdInline.Code("a*b*"), t(" "), Bold(listOf(Italic(listOf(t("beide"))))),
            ),
            inl("**vet** _schuin_ ~~weg~~ ==geel== `a*b*` ***beide***"),
        )
        assertEquals(listOf(Italic(listOf(t("a "), Bold(listOf(t("b"))), t(" c")))), inl("*a **b** c*"))
        assertEquals(listOf(t("snake_case_name en 2 * 3 * 4")), inl("snake_case_name en 2 * 3 * 4"))
        assertEquals(listOf(t("*niet* gesloten **")), inl("\\*niet\\* gesloten **"))
    }

    @Test
    fun `links, wikilinks, embeds and tags`() {
        assertEquals(
            listOf(
                Link("https://x.nl/a_(b)", listOf(t("site"))), t(" "),
                WikiLink("2026-10-23", "gisteren"), t(" "),
                WikiLink("Notitie#Kop", "Notitie#Kop"), t(" "),
                Embed("foto.jpg"), Embed("b c.png"), t(" "),
                Tag("reis/2026"), t(" #123 a#b"),
            ),
            inl("[site](https://x.nl/a_(b)) [[2026-10-23|gisteren]] [[Notitie#Kop]] ![[foto.jpg|300]]![](<b c.png>) #reis/2026 #123 a#b"),
        )
        assertEquals(
            listOf(t("Zie "), Link("https://example.org/pad", listOf(t("https://example.org/pad"))), t(".")),
            inl("Zie https://example.org/pad."),
        )
    }

    @Test
    fun `lists, nesting and tasks`() {
        val doc = MarkdownDoc.parse("- een\n- twee\n  doorloop\n\t- genest\n- [x] klaar\n- [ ] open\n\n3. drie\n4. vier\n")
        assertEquals(2, doc.size)
        val bullets = doc[0] as ListBlock
        assertEquals(false, bullets.ordered)
        assertEquals(4, bullets.items.size)
        assertEquals(listOf(p(t("een"))), bullets.items[0].blocks)
        assertEquals(p(t("twee"), LineBreak, t("doorloop")), bullets.items[1].blocks[0])
        assertEquals(listOf(MdListItem(null, listOf(p(t("genest"))))), (bullets.items[1].blocks[1] as ListBlock).items)
        assertEquals('x', bullets.items[2].task)
        assertTrue(bullets.items[2].checked)
        assertEquals(listOf(p(t("open"))), bullets.items[3].blocks)
        assertEquals(false, bullets.items[3].checked)
        val numbers = doc[1] as ListBlock
        assertEquals(true, numbers.ordered)
        assertEquals(3, numbers.start)
        assertEquals(2, numbers.items.size)
    }

    @Test
    fun `a blank line between items keeps one list`() {
        val doc = MarkdownDoc.parse("- a\n\n- b\n\nNa de lijst.\n")
        assertEquals(2, doc.size)
        assertEquals(2, (doc[0] as ListBlock).items.size)
        assertEquals(p(t("Na de lijst.")), doc[1])
    }

    @Test
    fun `quotes and callouts`() {
        assertEquals(
            listOf(
                Quote(listOf(p(t("citaat"), LineBreak, t("verder")))),
                Callout("tip", listOf(t("Let op")), listOf(p(t("inhoud")))),
            ),
            MarkdownDoc.parse("> citaat\n> verder\n\n> [!TIP]- Let op\n> inhoud\n"),
        )
    }

    @Test
    fun `code blocks are not parsed`() {
        assertEquals(
            listOf(Code("kotlin", "val a = **b**\n\n# geen kop"), p(t("na"))),
            MarkdownDoc.parse("```kotlin\nval a = **b**\n\n# geen kop\n```\nna\n"),
        )
        assertEquals(listOf(Code("", "ingesprongen")), MarkdownDoc.parse("    ingesprongen\n"))
    }

    @Test
    fun `tables`() {
        val doc = MarkdownDoc.parse("| Wat | Hoe |\n|---|:-:|\n| a \\| b | [[x\\|y]] |\n| alleen |\n")
        val table = doc.single() as Table
        assertEquals(listOf(listOf(t("Wat")), listOf(t("Hoe"))), table.header)
        assertEquals(listOf(t("a | b")), table.rows[0][0])
        assertEquals(2, table.rows[1].size)
    }

    @Test
    fun `comments are hidden`() {
        assertEquals(listOf(p(t("a  b"))), MarkdownDoc.parse("a %%verborgen%% b"))
        assertEquals(listOf(p(t("voor")), p(t("na"))), MarkdownDoc.parse("voor\n\n%%\nlang\n%%\n\nna"))
    }

    @Test
    fun `every fixture parses`() {
        for (path in Fixtures.all) {
            val note = Fixtures.note(path)
            for (entry in note.entries) MarkdownDoc.parse(entry.body)
        }
    }
}
