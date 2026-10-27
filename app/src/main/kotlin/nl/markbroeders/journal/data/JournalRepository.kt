package nl.markbroeders.journal.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate

/** A daily note file found in the journal folder; [date] comes from its filename. */
data class NoteFile(val date: LocalDate, val node: DocNode)

sealed interface NoteListing {
    data object FolderMissing : NoteListing

    /** [files] newest first; [yearFolders] are the `YYYY` folders they were found in. */
    data class Files(val files: List<NoteFile>, val yearFolders: List<DocNode>) : NoteListing
}

sealed interface SaveResult {
    /** [key] identifies the added or edited entry, if any. */
    data class Saved(val key: EntryKey?) : SaveResult

    /** The entry changed on disk since it was opened; ask the user, then save with `force`. */
    data object Conflict : SaveResult

    /** Deleting left the note blank; ask whether to delete the file, then save with `deleteBlankFile`. */
    data object NoteWouldBeBlank : SaveResult

    data class Failed(val error: Exception) : SaveResult
}

/**
 * Reads and writes daily notes in the vault. Saves re-read the file and merge the change into
 * what is on disk now (see [NoteEdits]), then replace the file atomically: write a temp file in
 * the same folder, delete the original, rename the temp file. A temp file left behind by a crash
 * is recovered on the next listing. Saves and listings are serialised so a listing never touches
 * a temp file that a save is still using.
 */
class JournalRepository(private val access: VaultAccess) {

    private val lock = Mutex()

    /** Lists `JOURNAL/YYYY/YYYY-MM-DD.md` files. Other files and folders (and notes in the wrong year folder) are ignored. */
    suspend fun listNoteFiles(settings: VaultSettings): NoteListing = lock.withLock {
        val uri = settings.vaultUri
        val journal = access.resolve(uri, VaultPaths.segments(settings.journalFolder))
            ?.takeIf { it.isDirectory }
            ?: return NoteListing.FolderMissing
        val years = access.listChildren(uri, journal)
            .filter { it.isDirectory && VaultPaths.isYearFolder(it.name) }
        val files = years
            .flatMap { year ->
                recoverTempFiles(year, access.listChildren(uri, year)).mapNotNull { node ->
                    VaultPaths.dateOfDailyNote(node.name)
                        // A note in the wrong year folder isn't where saves go; skip it.
                        ?.takeIf { !node.isDirectory && "%04d".format(it.year) == year.name }
                        ?.let { NoteFile(it, node) }
                }
            }
            .sortedByDescending { it.date }
        NoteListing.Files(files, years)
    }

    suspend fun readNote(file: NoteFile): DailyNote =
        DailyNoteParser.parse(access.readText(file.node), file.date)

    /** The note for [date] as it is on disk now; an empty note if the file doesn't exist. */
    suspend fun loadDay(settings: VaultSettings, date: LocalDate): DailyNote {
        val node = access.resolve(settings.vaultUri, VaultPaths.dailyNotePath(settings.journalFolder, date))
        return DailyNoteParser.parse(node?.let { access.readText(it) }.orEmpty(), date)
    }

    /**
     * Applies [edit] to the note for [date]. With [force], the user's version of a changed entry
     * wins. [deleteBlankFile] answers [SaveResult.NoteWouldBeBlank]: true deletes the file,
     * false keeps it (with whatever frontmatter is left).
     */
    suspend fun save(
        settings: VaultSettings,
        date: LocalDate,
        edit: NoteEdit,
        force: Boolean = false,
        deleteBlankFile: Boolean? = null,
    ): SaveResult = lock.withLock {
        try {
            // Retry if the file changes between reading it and replacing it.
            repeat(MAX_ATTEMPTS) {
                attemptSave(settings, date, edit, force, deleteBlankFile)?.let { return@withLock it }
            }
            SaveResult.Failed(IOException("The note kept changing while saving. Try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Saving $date failed", e)
            SaveResult.Failed(e)
        }
    }

    /** One read-merge-write attempt; null means the file changed underneath and it should retry. */
    private suspend fun attemptSave(
        settings: VaultSettings,
        date: LocalDate,
        edit: NoteEdit,
        force: Boolean,
        deleteBlankFile: Boolean?,
    ): SaveResult? {
        val path = VaultPaths.dailyNotePath(settings.journalFolder, date)
        val fileName = path.last()
        val file = access.resolve(settings.vaultUri, path)
        val currentText = file?.let { access.readText(it) }.orEmpty()
        val current = DailyNoteParser.parse(currentText, date)

        val applied = when (val r = NoteEdits.apply(current, edit, force)) {
            ApplyResult.Conflict -> return SaveResult.Conflict
            is ApplyResult.Applied -> r
        }
        val newText = applied.note.serialize()
        if (newText == currentText) return SaveResult.Saved(applied.key)

        if (file != null && applied.note.isBlank && edit.entry is EntryChange.Delete) {
            when (deleteBlankFile) {
                null -> return SaveResult.NoteWouldBeBlank
                true -> {
                    if (changedSince(file)) return null
                    access.delete(file)
                    return SaveResult.Saved(null)
                }
                false -> {}
            }
        }

        if (file != null && changedSince(file)) return null
        val folder = access.ensureDirectory(settings.vaultUri, path.dropLast(1))
        replaceAtomically(folder, fileName, file, newText)
        return SaveResult.Saved(applied.key)
    }

    private suspend fun changedSince(file: DocNode): Boolean =
        access.stat(file)?.lastModified != file.lastModified

    private suspend fun replaceAtomically(folder: DocNode, fileName: String, existing: DocNode?, text: String) {
        val tempName = VaultPaths.tempName(fileName)
        access.child(folder, tempName)?.let { access.delete(it) }
        val temp = access.createFile(folder, tempName)
        try {
            access.writeText(temp, text)
        } catch (e: Exception) {
            runCatching { access.delete(temp) }
            throw e
        }
        // From here the new content is safe in the temp file; a crash is recovered by the next listing.
        existing?.let { access.delete(it) }
        access.rename(temp, fileName)
    }

    /**
     * Finishes or discards replacements interrupted by a crash. A temp file whose note is
     * missing holds the newer, complete content (the original was already deleted), so it is
     * renamed into place. If the note still exists, the save never completed and the temp file
     * is removed.
     */
    private suspend fun recoverTempFiles(folder: DocNode, children: List<DocNode>): List<DocNode> {
        val temps = children.filter { !it.isDirectory && VaultPaths.targetOfTemp(it.name) != null }
        if (temps.isEmpty()) return children
        val names = children.mapTo(HashSet()) { it.name }
        val result = children.filterNot { it in temps }.toMutableList()
        for (temp in temps) {
            val target = VaultPaths.targetOfTemp(temp.name)!!
            try {
                if (target in names) {
                    access.delete(temp)
                } else {
                    result += access.rename(temp, target)
                    Log.w(TAG, "Recovered ${folder.name}/$target from an interrupted save")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't recover ${temp.name}", e)
            }
        }
        return result
    }

    private companion object {
        const val TAG = "JournalRepository"
        const val MAX_ATTEMPTS = 3
    }
}
