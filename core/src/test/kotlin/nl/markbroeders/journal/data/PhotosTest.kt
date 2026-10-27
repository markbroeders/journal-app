package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotosTest {

    private val date = LocalDate.of(2026, 10, 23)
    private val time = LocalTime.of(20, 11)

    @Test
    fun `first free name`() {
        assertEquals("2026-10-23-2011-1.jpg", Photos.nextName(date, time, emptySet()))
        assertEquals(
            "2026-10-23-2011-3.jpg",
            Photos.nextName(date, time, setOf("2026-10-23-2011-1.jpg", "2026-10-23-2011-2.JPG", "other.jpg")),
        )
        assertEquals("2026-01-05-0807-1.jpg", Photos.nextName(LocalDate.of(2026, 1, 5), LocalTime.of(8, 7), emptySet()))
    }

    @Test
    fun `into empty text`() {
        assertEquals("![[a.jpg]]\n\n" to 12, Photos.insertEmbeds("", 0, listOf("a.jpg")))
    }

    @Test
    fun `at the end of a paragraph`() {
        val (text, cursor) = Photos.insertEmbeds("Hello", 5, listOf("a.jpg", "b.jpg"))
        assertEquals("Hello\n\n![[a.jpg]]\n![[b.jpg]]\n\n", text)
        assertEquals(text.length, cursor)
    }

    @Test
    fun `after a newline`() {
        assertEquals("Hello\n\n![[a.jpg]]\n\n", Photos.insertEmbeds("Hello\n", 6, listOf("a.jpg")).first)
        assertEquals("Hello\n\n![[a.jpg]]\n\n", Photos.insertEmbeds("Hello\n\n", 7, listOf("a.jpg")).first)
    }

    @Test
    fun `between paragraphs`() {
        val (text, cursor) = Photos.insertEmbeds("One\n\nTwo", 3, listOf("a.jpg"))
        assertEquals("One\n\n![[a.jpg]]\n\nTwo", text)
        assertEquals("One\n\n![[a.jpg]]".length, cursor)
    }

    @Test
    fun `in the middle of a line splits it`() {
        assertEquals("Hel\n\n![[a.jpg]]\n\nlo", Photos.insertEmbeds("Hello", 3, listOf("a.jpg")).first)
    }

    @Test
    fun `cursor out of range is clamped`() {
        assertEquals("Hi\n\n![[a.jpg]]\n\n", Photos.insertEmbeds("Hi", 99, listOf("a.jpg")).first)
    }
}
