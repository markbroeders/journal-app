package nl.markbroeders.journal.data

import java.io.File

object Fixtures {
    private val journalDir: File =
        File(requireNotNull(Fixtures::class.java.getResource("/fixtures/vault/Journal")).toURI())

    /** Paths like `2024/2024-03-14.md` of every daily note fixture. */
    val all: List<String> = journalDir.walk()
        .filter { it.isFile && it.extension == "md" }
        .map { it.relativeTo(journalDir).invariantSeparatorsPath }
        .sorted()
        .toList()

    fun text(path: String): String = File(journalDir, path).readText()

    /** A file elsewhere in the fixture vault, e.g. `.obsidian/app.json`. */
    fun vaultFile(path: String): String = File(journalDir.parentFile, path).readText()

    fun note(path: String): DailyNote = DailyNoteParser.parse(text(path), path)
}
