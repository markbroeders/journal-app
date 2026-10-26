package nl.markbroeders.journal.data

import java.time.LocalDate

/** A daily note file found in the journal folder; [date] comes from its filename. */
data class NoteFile(val date: LocalDate, val node: DocNode)

sealed interface NoteListing {
    data object FolderMissing : NoteListing

    /** Newest first. */
    data class Files(val files: List<NoteFile>) : NoteListing
}

/** Reads the journal straight from the vault (no index yet). */
class JournalRepository(private val access: VaultAccess) {

    /** Lists `JOURNAL/YYYY/YYYY-MM-DD.md` files. Other files and folders are ignored. */
    suspend fun listNoteFiles(settings: VaultSettings): NoteListing {
        val uri = settings.vaultUri
        val journal = access.resolve(uri, VaultPaths.segments(settings.journalFolder))
            ?.takeIf { it.isDirectory }
            ?: return NoteListing.FolderMissing
        val files = access.listChildren(uri, journal)
            .filter { it.isDirectory && VaultPaths.isYearFolder(it.name) }
            .flatMap { year -> access.listChildren(uri, year) }
            .mapNotNull { node ->
                if (node.isDirectory) null else VaultPaths.dateOfDailyNote(node.name)?.let { NoteFile(it, node) }
            }
            .sortedByDescending { it.date }
        return NoteListing.Files(files)
    }

    suspend fun readNote(file: NoteFile): DailyNote =
        DailyNoteParser.parse(access.readText(file.node), file.date)
}
