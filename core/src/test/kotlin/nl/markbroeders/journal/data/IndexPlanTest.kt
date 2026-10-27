package nl.markbroeders.journal.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexPlanTest {

    private fun file(id: String, date: String, modified: Long = 1, size: Long = 10) =
        ListedNote(id, LocalDate.parse(date), modified, size)

    @Test
    fun `empty index reads every file, newest first`() {
        val plan = IndexPlan.compute(
            emptyMap(),
            listOf(file("a", "2024-01-01"), file("c", "2026-03-01"), file("b", "2025-06-01")),
        )
        assertEquals(listOf("c", "b", "a"), plan.toRead.map { it.id })
        assertTrue(plan.toRemove.isEmpty())
    }

    @Test
    fun `unchanged files are skipped`() {
        val plan = IndexPlan.compute(mapOf("a" to IndexStamp(1, 10)), listOf(file("a", "2026-01-01")))
        assertTrue(plan.toRead.isEmpty())
        assertTrue(plan.toRemove.isEmpty())
    }

    @Test
    fun `changed time or size is read again`() {
        val plan = IndexPlan.compute(
            mapOf("a" to IndexStamp(1, 10), "b" to IndexStamp(1, 10)),
            listOf(file("a", "2026-01-01", modified = 2), file("b", "2026-01-02", size = 11)),
        )
        assertEquals(setOf("a", "b"), plan.toRead.map { it.id }.toSet())
    }

    @Test
    fun `files that failed to read are retried`() {
        val plan = IndexPlan.compute(
            mapOf("a" to IndexStamp(1, 10, readFailed = true)),
            listOf(file("a", "2026-01-01")),
        )
        assertEquals(listOf("a"), plan.toRead.map { it.id })
    }

    @Test
    fun `missing files are removed`() {
        val plan = IndexPlan.compute(
            mapOf("a" to IndexStamp(1, 10), "gone" to IndexStamp(1, 10)),
            listOf(file("a", "2026-01-01")),
        )
        assertEquals(setOf("gone"), plan.toRemove)
        assertTrue(plan.toRead.isEmpty())
    }
}
