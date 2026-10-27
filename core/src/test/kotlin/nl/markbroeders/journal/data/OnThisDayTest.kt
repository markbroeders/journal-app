package nl.markbroeders.journal.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OnThisDayTest {

    @Test
    fun `an ordinary day matches itself`() {
        assertEquals(listOf("10-27"), OnThisDay.monthDays(LocalDate.of(2026, 10, 27)))
        assertEquals(listOf("01-05"), OnThisDay.monthDays(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `28 February in a non-leap year includes the leap day`() {
        assertEquals(listOf("02-28", "02-29"), OnThisDay.monthDays(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `in a leap year 28 and 29 February are separate`() {
        assertEquals(listOf("02-28"), OnThisDay.monthDays(LocalDate.of(2028, 2, 28)))
        assertEquals(listOf("02-29"), OnThisDay.monthDays(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun `years ago label`() {
        val today = LocalDate.of(2026, 10, 27)
        assertEquals("1 year ago", OnThisDay.yearsAgo(LocalDate.of(2025, 10, 27), today))
        assertEquals("3 years ago", OnThisDay.yearsAgo(LocalDate.of(2023, 10, 27), today))
    }
}
