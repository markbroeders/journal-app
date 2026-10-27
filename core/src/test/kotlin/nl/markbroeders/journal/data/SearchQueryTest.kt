package nl.markbroeders.journal.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchQueryTest {

    @Test
    fun `blank input is empty`() {
        assertTrue(SearchQuery.parse("   ").isEmpty)
        assertNull(SearchQuery.parse("").matchExpression())
    }

    @Test
    fun `words become prefix terms`() {
        val q = SearchQuery.parse("Utrecht  rain")
        assertEquals("utrecht* rain*", q.matchExpression())
        assertEquals(emptyList(), q.tags)
    }

    @Test
    fun `quoted phrases match exactly`() {
        assertEquals("\"light rain\" walk*", SearchQuery.parse("\"Light rain\" walk").matchExpression())
    }

    @Test
    fun `an unclosed quote is a prefix phrase`() {
        assertEquals("\"light ra*\"", SearchQuery.parse("\"light ra").matchExpression())
    }

    @Test
    fun `punctuation splits words like the tokenizer`() {
        assertEquals("\"don t*\"", SearchQuery.parse("don't").matchExpression())
        assertEquals("caf*", SearchQuery.parse("caf...").matchExpression())
    }

    @Test
    fun `operators and syntax cannot be injected`() {
        assertEquals("a* or* not* b*", SearchQuery.parse("a OR NOT b").matchExpression())
        assertEquals("x* y*", SearchQuery.parse("x* -y (").matchExpression())
        assertNull(SearchQuery.parse("* ( ) -").matchExpression())
    }

    @Test
    fun `tags are separated from words`() {
        val q = SearchQuery.parse("#family beach #Holiday/2024 #family")
        assertEquals(listOf("family", "Holiday/2024"), q.tags)
        assertEquals("beach*", q.matchExpression())
    }

    @Test
    fun `a lone hash or a number is not a tag`() {
        val q = SearchQuery.parse("# #123")
        assertEquals(emptyList(), q.tags)
        assertEquals("123*", q.matchExpression())
    }

    @Test
    fun `tag patterns match the tag and nested tags, escaping wildcards`() {
        val q = SearchQuery.parse("#my_tag")
        assertEquals(listOf("%\nmy\\_tag\n%", "%\nmy\\_tag/%"), q.tagPatterns(q.tags.single()))
    }
}
