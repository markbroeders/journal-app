package nl.markbroeders.journal.desktop.data

import nl.markbroeders.journal.data.ApplyResult
import nl.markbroeders.journal.data.AttachmentFolder
import nl.markbroeders.journal.data.DailyNote
import nl.markbroeders.journal.data.DailyNoteParser
import nl.markbroeders.journal.data.Embeds
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.data.NoteEdits
import nl.markbroeders.journal.data.ObsidianConfig
import nl.markbroeders.journal.data.VaultPaths
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** The vault folder and the journal folder inside it (vault-relative, e.g. `Journal`). */
data class VaultSettings(val vault: Path, val journalFolder: String) {
    val journalDir: Path get() = resolve(VaultPaths.segments(journalFolder))

    fun notePath(date: LocalDate): Path = resolve(VaultPaths.dailyNotePath(journalFolder, date))

    fun resolve(segments: List<String>): Path = segments.fold(vault) { path, segment -> path.resolve(segment) }
}

/** When a file was last changed, precisely enough to notice an edit by another program. */
data class FileStamp(val modifiedNanos: Long, val size: Long)

/** A daily note file found in the journal folder; [date] comes from its name. */
data class NoteFile(val date: LocalDate, val path: Path, val stamp: FileStamp)

sealed interface NoteListing {
    data object FolderMissing : NoteListing

    /** [files] newest first; [yearDirs] are the `YYYY` folders they were found in. */
    data class Files(val files: List<NoteFile>, val yearDirs: List<Path>) : NoteListing
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
 * Reads and writes daily notes in the vault, like the Android app's JournalRepository but with
 * plain file access. Saves re-read the file and merge the change into what is on disk now (see
 * [NoteEdits]), then replace the file atomically: the new text goes into `.YYYY-MM-DD.md.tmp`
 * in the same folder, which is then renamed over the note. If the note changed between reading
 * and replacing it, the save starts over. Saves and listings share one lock, so a listing never
 * touches a temp file a save is still writing; a temp file left by a crash (here or in the
 * Android app, via sync) is recovered by the next listing.
 */
class JournalFiles(val settings: VaultSettings) {

    private val lock = ReentrantLock()

    /** Lists `JOURNAL/YYYY/YYYY-MM-DD.md`. Other files and folders (and notes in the wrong year folder) are ignored. */
    fun listNotes(): NoteListing = lock.withLock {
        val journal = settings.journalDir
        if (!journal.isDirectory()) return NoteListing.FolderMissing
        val years = children(journal).filter { it.isDirectory() && VaultPaths.isYearFolder(it.name) }.sorted()
        val files = years.flatMap { year ->
            recoverTempFiles(children(year)).mapNotNull { path ->
                val date = VaultPaths.dateOfDailyNote(path.name)
                    // A note in the wrong year folder isn't where saves go; skip it.
                    ?.takeIf { "%04d".format(it.year) == year.name }
                    ?: return@mapNotNull null
                val stamp = stamp(path)?.takeIf { !path.isDirectory() } ?: return@mapNotNull null
                NoteFile(date, path, stamp)
            }
        }
        NoteListing.Files(files.sortedByDescending { it.date }, years)
    }

    fun read(file: NoteFile): DailyNote = DailyNoteParser.parse(readText(file.path), file.date)

    /** The note for [date] as it is on disk now; an empty note if the file doesn't exist. */
    fun loadDay(date: LocalDate): DailyNote {
        val path = settings.notePath(date)
        val text = try {
            readText(path)
        } catch (_: NoSuchFileException) {
            ""
        }
        return DailyNoteParser.parse(text, date)
    }

    /**
     * Applies [edit] to the note for [date]. With [force], the user's version of a changed entry
     * wins. [deleteBlankFile] answers [SaveResult.NoteWouldBeBlank]: true deletes the file,
     * false keeps it (with whatever frontmatter is left).
     */
    fun save(date: LocalDate, edit: NoteEdit, force: Boolean = false, deleteBlankFile: Boolean? = null): SaveResult =
        lock.withLock {
            try {
                repeat(MAX_ATTEMPTS) {
                    attemptSave(date, edit, force, deleteBlankFile)?.let { return it }
                }
                SaveResult.Failed(IOException("The note kept changing while saving. Try again."))
            } catch (e: Exception) {
                log.log(Level.WARNING, "Saving $date failed", e)
                SaveResult.Failed(e)
            }
        }

    /** One read-merge-write attempt; null means the file changed underneath and it should retry. */
    private fun attemptSave(date: LocalDate, edit: NoteEdit, force: Boolean, deleteBlankFile: Boolean?): SaveResult? {
        val path = settings.notePath(date)
        val before = stamp(path)
        val currentText = if (before != null) readText(path) else ""
        val current = DailyNoteParser.parse(currentText, date)

        val applied = when (val r = NoteEdits.apply(current, edit, force)) {
            ApplyResult.Conflict -> return SaveResult.Conflict
            is ApplyResult.Applied -> r
        }
        val newText = applied.note.serialize()
        if (newText == currentText) return SaveResult.Saved(applied.key)

        if (before != null && applied.note.isBlank && edit.entry is EntryChange.Delete) {
            when (deleteBlankFile) {
                null -> return SaveResult.NoteWouldBeBlank
                true -> {
                    if (stamp(path) != before) return null
                    Files.delete(path)
                    return SaveResult.Saved(null)
                }
                false -> {}
            }
        }

        // Also catches a note created by someone else since we looked (before == null).
        if (stamp(path) != before) return null
        Files.createDirectories(path.parent)
        replaceAtomically(path, newText)
        return SaveResult.Saved(applied.key)
    }

    private fun replaceAtomically(path: Path, text: String) {
        val temp = path.resolveSibling(VaultPaths.tempName(path.name))
        Files.deleteIfExists(temp)
        try {
            FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(temp) }
            throw e
        }
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Finishes or discards replacements interrupted by a crash. A temp file whose note is
     * missing holds the newer, complete content (the Android app deletes the original before
     * renaming), so it is renamed into place. If the note still exists, the temp file is removed.
     */
    private fun recoverTempFiles(children: List<Path>): List<Path> {
        val temps = children.filter { VaultPaths.targetOfTemp(it.name) != null && !it.isDirectory() }
        if (temps.isEmpty()) return children
        val names = children.mapTo(HashSet()) { it.name }
        val result = children.filterNot { it in temps }.toMutableList()
        for (temp in temps) {
            val target = VaultPaths.targetOfTemp(temp.name)!!
            try {
                if (target in names) {
                    Files.deleteIfExists(temp)
                } else {
                    result.add(Files.move(temp, temp.resolveSibling(target)))
                    log.info("Recovered ${temp.parent.name}/$target from an interrupted save")
                }
            } catch (e: Exception) {
                log.log(Level.WARNING, "Couldn't recover ${temp.name}", e)
            }
        }
        return result
    }

    /** Where Obsidian puts new attachments for the note at [date] (from `.obsidian/app.json`). */
    fun attachmentDir(date: LocalDate): Path = when (val folder = attachmentFolder()) {
        AttachmentFolder.VaultRoot -> settings.vault
        is AttachmentFolder.InVault -> settings.resolve(VaultPaths.segments(folder.path))
        is AttachmentFolder.NextToNote -> VaultPaths.segments(folder.sub).fold(settings.notePath(date).parent) { p, s -> p.resolve(s) }
    }

    fun attachmentFolder(): AttachmentFolder {
        val json = runCatching { Files.readString(settings.vault.resolve(".obsidian").resolve("app.json")) }.getOrNull()
        return ObsidianConfig.attachmentFolder(json)
    }

    /**
     * The media files embeds can refer to, by lowercase name. Looks where the Android app's
     * AttachmentScanner does, in the same order (earlier folders win a name): Obsidian's attachment
     * folder, the vault root, `Attachments` and the year folders. Also returns the folders it
     * looked in, so they can be watched.
     */
    fun listAttachments(yearDirs: List<Path>): Pair<Map<String, Path>, List<Path>> {
        val found = LinkedHashMap<String, Path>()
        val visited = LinkedHashSet<Path>()
        fun collect(dir: Path?, depth: Int) {
            if (dir == null || !dir.isDirectory() || !visited.add(dir.toAbsolutePath().normalize())) return
            for (child in children(dir)) {
                when {
                    child.isDirectory() -> if (depth > 0 && !child.name.startsWith('.')) collect(child, depth - 1)
                    Embeds.isMedia(child.name) -> found.putIfAbsent(child.name.lowercase(), child)
                }
            }
        }
        val root = settings.vault
        when (val folder = attachmentFolder()) {
            AttachmentFolder.VaultRoot -> collect(root, 0)
            is AttachmentFolder.InVault -> collect(settings.resolve(VaultPaths.segments(folder.path)), SUBFOLDER_DEPTH)
            is AttachmentFolder.NextToNote -> for (year in yearDirs) {
                collect(VaultPaths.segments(folder.sub).fold(year) { p, s -> p.resolve(s) }, SUBFOLDER_DEPTH)
            }
        }
        collect(root, 0)
        collect(root.resolve(DEFAULT_ATTACHMENTS), SUBFOLDER_DEPTH)
        for (year in yearDirs) collect(year, 0)
        return found to visited.toList()
    }

    companion object {
        private val log = Logger.getLogger("JournalFiles")
        private const val MAX_ATTEMPTS = 3
        private const val DEFAULT_ATTACHMENTS = "Attachments"

        // Attachment folders are sometimes split into subfolders (per year, per note); don't go deeper.
        private const val SUBFOLDER_DEPTH = 2

        fun stamp(path: Path): FileStamp? = try {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            val time = attrs.lastModifiedTime().toInstant()
            FileStamp(time.epochSecond * 1_000_000_000 + time.nano, attrs.size())
        } catch (_: NoSuchFileException) {
            null
        }

        fun readText(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

        private fun children(dir: Path): List<Path> = try {
            dir.listDirectoryEntries()
        } catch (e: IOException) {
            log.log(Level.WARNING, "Couldn't list $dir", e)
            emptyList()
        }
    }
}
