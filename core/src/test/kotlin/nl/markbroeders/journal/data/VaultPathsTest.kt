package nl.markbroeders.journal.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultPathsTest {

    @Test
    fun `folder input is normalised`() {
        assertEquals("Journal", VaultPaths.normalizeFolder("Journal"))
        assertEquals("Journal", VaultPaths.normalizeFolder("  /Journal/ "))
        assertEquals("Notes/Journal", VaultPaths.normalizeFolder("Notes//Journal"))
        assertEquals("Notes/Journal", VaultPaths.normalizeFolder("Notes\\Journal"))
        assertEquals("x - dagboek/2e map", VaultPaths.normalizeFolder("x - dagboek / 2e map"))
    }

    @Test
    fun `invalid folders are rejected`() {
        for (bad in listOf("", "  ", "/", ".", "..", "Journal/../x", ".obsidian", "Notes/.trash", "a:b", "a?", "a*b")) {
            assertNull(VaultPaths.normalizeFolder(bad), bad)
        }
    }

    @Test
    fun `year folders and daily note names`() {
        assertTrue(VaultPaths.isYearFolder("2026"))
        assertFalse(VaultPaths.isYearFolder("26"))
        assertFalse(VaultPaths.isYearFolder("2026-01"))
        assertTrue(VaultPaths.isDailyNoteName("2026-10-26.md"))
        assertFalse(VaultPaths.isDailyNoteName("2026-02-30.md"))
        assertFalse(VaultPaths.isDailyNoteName("2026-10-26.txt"))
        assertFalse(VaultPaths.isDailyNoteName("2026-10-26 copy.md"))
        assertEquals(LocalDate.of(2024, 3, 14), VaultPaths.dateOfDailyNote("2024-03-14.md"))
    }

    @Test
    fun `temp file names`() {
        assertEquals(".2026-10-26.md.tmp", VaultPaths.tempName("2026-10-26.md"))
        assertEquals("2026-10-26.md", VaultPaths.targetOfTemp(".2026-10-26.md.tmp"))
        assertNull(VaultPaths.targetOfTemp("2026-10-26.md.tmp"))
        assertNull(VaultPaths.targetOfTemp(".notes.md.tmp"))
        assertNull(VaultPaths.targetOfTemp(".2026-10-26.md"))
    }

    @Test
    fun `daily note path`() {
        assertEquals(
            listOf("Notes", "Journal", "2026", "2026-01-09.md"),
            VaultPaths.dailyNotePath("Notes/Journal", LocalDate.of(2026, 1, 9)),
        )
    }
}
