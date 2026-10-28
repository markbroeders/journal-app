package nl.markbroeders.journal.data

import java.time.LocalTime
import java.time.ZonedDateTime

/** The optional daily reminder to write an entry. */
object Reminder {
    val DEFAULT_TIME: LocalTime = LocalTime.of(20, 0)

    /**
     * The first moment after [now] at [time] on the clock, today or tomorrow. A time that doesn't
     * exist on a day (skipped by a DST change) moves forward by the length of the gap.
     */
    fun nextAt(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    /** Whether [note] already has something written in it, so today's reminder can be skipped. */
    fun isWritten(note: DailyNote): Boolean = note.entries.any { it.body.isNotBlank() }
}
