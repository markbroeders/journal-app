package nl.markbroeders.journal.data

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private fun at(s: String) = ZonedDateTime.of(LocalDateTime.parse(s), amsterdam)
    private val eight = LocalTime.of(20, 0)

    @Test
    fun `before the time it is today`() {
        assertEquals(at("2026-10-28T20:00"), Reminder.nextAt(at("2026-10-28T13:15"), eight))
    }

    @Test
    fun `at or after the time it is tomorrow`() {
        assertEquals(at("2026-10-29T20:00"), Reminder.nextAt(at("2026-10-28T20:00"), eight))
        assertEquals(at("2026-10-29T20:00"), Reminder.nextAt(at("2026-10-28T20:07"), eight))
    }

    @Test
    fun `it stays at the same clock time across a DST change`() {
        // Summer time ends on 25 October 2026: that day has 25 hours.
        val next = Reminder.nextAt(at("2026-10-24T21:00"), eight)
        assertEquals(LocalDateTime.parse("2026-10-25T20:00"), next.toLocalDateTime())
        assertEquals(25, java.time.Duration.between(at("2026-10-24T20:00"), next).toHours())
    }

    @Test
    fun `a time skipped by DST moves forward`() {
        // Summer time starts on 29 March 2026: 02:00–03:00 doesn't exist.
        val next = Reminder.nextAt(at("2026-03-28T23:00"), LocalTime.of(2, 30))
        assertEquals(LocalDateTime.parse("2026-03-29T03:30"), next.toLocalDateTime())
    }

    @Test
    fun `a note with any written entry counts`() {
        assertTrue(Reminder.isWritten(Fixtures.note("2024/2024-03-14.md")))
        assertTrue(Reminder.isWritten(Fixtures.note("2025/2025-11-05.md")))
    }

    @Test
    fun `empty notes and notes with only frontmatter don't count`() {
        assertFalse(Reminder.isWritten(DailyNoteParser.parse("")))
        assertFalse(Reminder.isWritten(Fixtures.note("2026/2026-03-08.md")))
        assertFalse(Reminder.isWritten(Fixtures.note("2026/2026-04-01.md")))
        assertFalse(Reminder.isWritten(DailyNoteParser.parse("---\ntags: [journal]\n---\n\n## 09:00\n\n")))
    }
}
