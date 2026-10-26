package nl.markbroeders.journal.data

import java.time.LocalDate

/** Vault-relative path rules for the journal layout `JOURNAL/YYYY/YYYY-MM-DD.md`. */
object VaultPaths {

    const val DEFAULT_JOURNAL_FOLDER = "Journal"

    // Characters that Android's FAT/exFAT-backed storage (and Windows peers in sync) reject.
    private val ILLEGAL_CHARS = Regex("""[:*?"<>|\u0000-\u001F]""")
    private val YEAR_FOLDER = Regex("""\d{4}""")
    private val DAILY_NOTE = Regex("""(\d{4})-(\d{2})-(\d{2})\.md""")

    /**
     * Normalises a user-entered vault-relative folder: trims, accepts `\` as a separator and
     * drops empty segments, so ` /Journal/ ` becomes `Journal`. Returns null if the path is
     * empty, contains `.`/`..` or hidden (dot) segments, or characters that aren't allowed.
     */
    fun normalizeFolder(input: String): String? {
        val segments = input.trim().replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        if (segments.any { it.startsWith('.') || ILLEGAL_CHARS.containsMatchIn(it) }) return null
        return segments.joinToString("/")
    }

    fun segments(folder: String): List<String> = folder.split('/').filter { it.isNotEmpty() }

    fun isYearFolder(name: String): Boolean = YEAR_FOLDER.matches(name)

    /** True for `YYYY-MM-DD.md` names that are a real date. */
    fun isDailyNoteName(name: String): Boolean = dateOfDailyNote(name) != null

    fun dateOfDailyNote(name: String): LocalDate? {
        val m = DAILY_NOTE.matchEntire(name) ?: return null
        val (y, mo, d) = m.destructured
        return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }

    /** Name of the temp file used while atomically replacing [fileName] (hidden, same folder). */
    fun tempName(fileName: String): String = ".$fileName.tmp"

    /** The file a temp file belongs to, or null if [name] isn't one of our temp files. */
    fun targetOfTemp(name: String): String? =
        name.takeIf { it.startsWith(".") && it.endsWith(".md.tmp") }?.removePrefix(".")?.removeSuffix(".tmp")
            ?.takeIf { isDailyNoteName(it) }

    fun dailyNoteName(date: LocalDate): String = "%s.md".format(date)

    /** Path segments of the daily note for [date], relative to the vault root. */
    fun dailyNotePath(journalFolder: String, date: LocalDate): List<String> =
        segments(journalFolder) + "%04d".format(date.year) + dailyNoteName(date)
}
