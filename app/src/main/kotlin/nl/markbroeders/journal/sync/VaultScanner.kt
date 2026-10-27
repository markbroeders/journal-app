package nl.markbroeders.journal.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.markbroeders.journal.data.EntryRow
import nl.markbroeders.journal.data.IndexDao
import nl.markbroeders.journal.data.IndexDb
import nl.markbroeders.journal.data.IndexPlan
import nl.markbroeders.journal.data.IndexStamp
import nl.markbroeders.journal.data.JournalRepository
import nl.markbroeders.journal.data.ListedNote
import nl.markbroeders.journal.data.NoteFile
import nl.markbroeders.journal.data.NoteListing
import nl.markbroeders.journal.data.NoteRow
import nl.markbroeders.journal.data.Timeline
import nl.markbroeders.journal.data.VaultAccess
import nl.markbroeders.journal.data.VaultSettings
import nl.markbroeders.journal.data.toRows

sealed interface ScanResult {
    data object FolderMissing : ScanResult

    /** [read] files were (re-)indexed, [removed] rows dropped, [failed] files couldn't be read. */
    data class Done(val read: Int, val removed: Int, val failed: Int) : ScanResult
}

/**
 * Keeps [IndexDb] in step with the journal folder. Lists every daily note (one query per
 * folder) and only reads files whose last-modified time or size differs from the index.
 * Rows are written in batches, newest first, so a first scan fills the timeline from the top.
 * Then the attachment folders are listed (see [AttachmentScanner]) for the media grid.
 * Scans are serialised; the app and the background worker share one scanner.
 */
class VaultScanner(private val repository: JournalRepository, access: VaultAccess, db: IndexDb) {

    private val dao: IndexDao = db.dao()
    private val attachments = AttachmentScanner(access, dao)
    private val lock = Mutex()
    private val readDispatcher = Dispatchers.IO.limitedParallelism(4)

    /** Clears the index if it was built from a different vault or journal folder. */
    suspend fun useVault(settings: VaultSettings) = lock.withLock { ensureSource(settings) }

    /** Brings the index up to date. Throws if the journal folder can't be listed. */
    suspend fun scan(settings: VaultSettings): ScanResult = lock.withLock {
        ensureSource(settings)
        val listing = when (val l = repository.listNoteFiles(settings)) {
            NoteListing.FolderMissing -> {
                dao.reset(source(settings))
                return@withLock ScanResult.FolderMissing
            }
            is NoteListing.Files -> l
        }
        val files = listing.files
        val byId = files.associateBy { it.node.documentId }
        val indexed = dao.stamps().associate { it.documentId to IndexStamp(it.lastModified, it.size, it.readFailed) }
        val plan = IndexPlan.compute(indexed, files.map { ListedNote(it.node.documentId, it.date, it.node.lastModified, it.node.size) })

        if (plan.toRemove.isNotEmpty()) dao.remove(plan.toRemove)
        var failed = 0
        for (batch in plan.toRead.chunked(BATCH_SIZE)) {
            val results = coroutineScope {
                batch.map { async(readDispatcher) { index(byId.getValue(it.id)) } }.awaitAll()
            }
            // A failed read keeps what was indexed before (if anything) and is retried next scan.
            val (unread, rows) = results.partition { it.first.readFailed && it.first.documentId in indexed }
            failed += results.count { it.first.readFailed }
            dao.replace(rows.map { it.first }, rows.flatMap { it.second })
            if (unread.isNotEmpty()) dao.markFailed(unread.map { it.first.documentId })
        }
        attachments.scan(settings, listing.yearFolders)
        if (plan.toRead.isNotEmpty() || plan.toRemove.isNotEmpty()) {
            Log.i(TAG, "Indexed ${plan.toRead.size} notes ($failed failed), removed ${plan.toRemove.size}")
        }
        ScanResult.Done(plan.toRead.size, plan.toRemove.size, failed)
    }

    /** Reads and parses one note; an unreadable file gets a row marked [NoteRow.readFailed]. */
    private suspend fun index(file: NoteFile): Pair<NoteRow, List<EntryRow>> {
        val node = file.node
        return try {
            val day = Timeline.day(repository.readNote(file), file.date)
            day?.toRows(node.documentId, node.lastModified, node.size) ?: (emptyRow(file, readFailed = false) to emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read ${node.name}", e)
            emptyRow(file, readFailed = true) to emptyList()
        }
    }

    /** A note without entries (blank, or unreadable). */
    private fun emptyRow(file: NoteFile, readFailed: Boolean) = NoteRow(
        file.node.documentId, file.date, file.node.lastModified, file.node.size,
        starred = false, mood = null, tags = emptyList(), entryCount = 0, readFailed = readFailed,
    )

    private suspend fun ensureSource(settings: VaultSettings) {
        val source = source(settings)
        if (dao.meta(IndexDao.SOURCE_KEY) != source) dao.reset(source)
    }

    private fun source(settings: VaultSettings) = "${settings.vaultUri}|${settings.journalFolder}"

    private companion object {
        const val TAG = "VaultScanner"
        const val BATCH_SIZE = 40
    }
}
