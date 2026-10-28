package nl.markbroeders.journal.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedTextTest {

    @Test
    fun `plain text is kept`() {
        assertEquals("Hello\nthere", SharedText.compose(null, "  Hello\r\nthere\n"))
        assertEquals("", SharedText.compose(null, null))
        assertEquals("", SharedText.compose(" ", " "))
    }

    @Test
    fun `a page title and url become a link`() {
        assertEquals(
            "[Een \\[mooi\\] artikel](https://example.org/a_(b%29%29)",
            SharedText.compose("Een [mooi] artikel", "https://example.org/a_(b))"),
        )
    }

    @Test
    fun `the subject goes above other text unless repeated`() {
        assertEquals("Onderwerp\n\nTekst", SharedText.compose("Onderwerp", "Tekst"))
        assertEquals("Titel - https://x.nl", SharedText.compose("Titel", "Titel - https://x.nl"))
        assertEquals("Alleen onderwerp", SharedText.compose("Alleen onderwerp", null))
    }
}
