package nl.markbroeders.journal.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchMatcherTest {

    private fun matcher(input: String) = SearchMatcher(SearchQuery.parse(input))

    @Test
    fun `words match as prefixes, case-insensitively`() {
        val m = matcher("Rid wal")
        assertTrue(m.matches("Finally did the ridge walk.", emptyList()))
        assertFalse(m.matches("Finally did the ridge.", emptyList()))
    }

    @Test
    fun `a prefix only matches at the start of a word`() {
        assertFalse(matcher("idge").matches("ridge", emptyList()))
    }

    @Test
    fun `accents are ignored on both sides`() {
        assertTrue(matcher("cafe").matches("Koffie bij het Café", emptyList()))
        assertTrue(matcher("café").matches("cafe au lait", emptyList()))
        // Decomposed: e followed by a combining acute accent.
        assertTrue(matcher("cafe").matches("Café", emptyList()))
    }

    @Test
    fun `closed phrases match consecutive whole words`() {
        val m = matcher("\"light rain\"")
        assertTrue(m.matches("11 °C, Light rain today", emptyList()))
        assertFalse(m.matches("light rainy day", emptyList()))
        assertFalse(m.matches("rain, light", emptyList()))
    }

    @Test
    fun `an unclosed phrase ends in a prefix`() {
        assertTrue(matcher("\"light ra").matches("light rainy day", emptyList()))
    }

    @Test
    fun `punctuation splits words like the index does`() {
        assertTrue(matcher("don't").matches("I don't know", emptyList()))
    }

    @Test
    fun `tags match themselves and nested tags, ignoring case`() {
        val m = matcher("#family")
        assertTrue(m.matches("", listOf("Family")))
        assertTrue(m.matches("", listOf("family/kids")))
        assertFalse(m.matches("", listOf("familyday")))
        assertFalse(m.matches("family", emptyList()))
    }

    @Test
    fun `every term and tag must match`() {
        val m = matcher("walk #hiking")
        assertTrue(m.matches("a walk", listOf("hiking")))
        assertFalse(m.matches("a walk", listOf("running")))
        assertFalse(m.matches("a run", listOf("hiking")))
    }

    @Test
    fun `highlights cover each match`() {
        val text = "Short hike up, hiking again"
        assertEquals(listOf(6..9, 15..20), matcher("hik").highlights(text))
        assertEquals(listOf(6..12), matcher("\"hike up\"").highlights(text))
    }
}
