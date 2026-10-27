package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.MonthDay

/** Matching a date against the same day in earlier years. */
object OnThisDay {

    /**
     * The `MM-DD` suffixes of ISO dates that count as "this day" for [date]. In a non-leap year,
     * 28 February also shows 29 February, so leap-day entries aren't hidden for three years.
     */
    fun monthDays(date: LocalDate): List<String> {
        val own = suffix(MonthDay.from(date))
        return if (date.monthValue == 2 && date.dayOfMonth == 28 && !date.isLeapYear) {
            listOf(own, suffix(MonthDay.of(2, 29)))
        } else {
            listOf(own)
        }
    }

    /** "1 year ago", "5 years ago" for an entry from [then], seen on [date]. */
    fun yearsAgo(then: LocalDate, date: LocalDate): String {
        val years = date.year - then.year
        return if (years == 1) "1 year ago" else "$years years ago"
    }

    private fun suffix(day: MonthDay) = "%02d-%02d".format(day.monthValue, day.dayOfMonth)
}
