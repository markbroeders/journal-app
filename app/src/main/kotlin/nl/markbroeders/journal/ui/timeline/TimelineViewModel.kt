package nl.markbroeders.journal.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.data.NoteFile
import nl.markbroeders.journal.data.NoteListing
import nl.markbroeders.journal.data.SaveResult
import nl.markbroeders.journal.data.Timeline
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.data.VaultSettings

enum class TimelineStatus { Loading, Ready, FolderMissing, Error }

/** Progress of deleting an entry from the entry screen. */
sealed interface Deletion {
    data object InProgress : Deletion

    /** Deleting leaves the note blank: delete the file too? */
    data class AskDeleteFile(val entry: TimelineEntry) : Deletion

    data class Failed(val message: String) : Deletion
}

data class TimelineUiState(
    val status: TimelineStatus = TimelineStatus.Loading,
    val days: List<TimelineDay> = emptyList(),
    val hasMore: Boolean = false,
    val refreshing: Boolean = false,
    /** Files that couldn't be read in the last load. */
    val unreadable: Int = 0,
    val deletion: Deletion? = null,
)

/**
 * Lists every daily note (cheap: one query per folder), then reads and parses them newest
 * first, a page at a time as the user scrolls. Parsed days are cached by document and
 * last-modified time, so a refresh only re-reads files that changed.
 */
class TimelineViewModel(
    container: AppContainer,
    private val settings: VaultSettings,
) : ViewModel() {

    private val repository = container.journal
    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private class Cached(val lastModified: Long, val day: TimelineDay?)

    private val cache = HashMap<String, Cached>()
    private var files: List<NoteFile> = emptyList()
    private var shownFiles = 0
    private var job: Job? = null
    private val readDispatcher = Dispatchers.IO.limitedParallelism(4)

    /** Re-lists the journal folder and re-reads changed files. */
    fun refresh(userInitiated: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (userInitiated) _state.update { it.copy(refreshing = true) }
            try {
                when (val listing = repository.listNoteFiles(settings)) {
                    NoteListing.FolderMissing -> {
                        files = emptyList()
                        _state.update { TimelineUiState(status = TimelineStatus.FolderMissing) }
                    }
                    is NoteListing.Files -> {
                        files = listing.files
                        val ids = files.mapTo(HashSet()) { it.node.documentId }
                        cache.keys.retainAll(ids)
                        shownFiles = maxOf(shownFiles, PAGE_SIZE)
                        load()
                        extendUntil(minDays = 1)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Listing the journal failed", e)
                _state.update { it.copy(status = TimelineStatus.Error) }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /**
     * Deletes [entry] if it is unchanged on disk. [deleteBlankFile] answers
     * [Deletion.AskDeleteFile]. Calls [onDeleted] when the entry is gone.
     */
    fun delete(entry: TimelineEntry, deleteBlankFile: Boolean? = null, onDeleted: () -> Unit) {
        _state.update { it.copy(deletion = Deletion.InProgress) }
        viewModelScope.launch {
            val edit = NoteEdit(EntryChange.Delete(entry.key, entry.raw))
            val deletion = when (val r = repository.save(settings, entry.date, edit, deleteBlankFile = deleteBlankFile)) {
                is SaveResult.Saved -> null.also { onDeleted() }
                SaveResult.NoteWouldBeBlank -> Deletion.AskDeleteFile(entry)
                SaveResult.Conflict -> Deletion.Failed(
                    "This entry was changed elsewhere (in Obsidian or by sync), so it wasn't deleted. " +
                        "Check the new version and try again.",
                )
                is SaveResult.Failed -> Deletion.Failed(r.error.message ?: "Deleting failed.")
            }
            _state.update { it.copy(deletion = deletion) }
            refresh()
        }
    }

    fun dismissDeletion() = _state.update { it.copy(deletion = null) }

    /** Shows the next page of days, if there is one and nothing is loading. */
    fun loadMore() {
        if (job?.isActive == true || shownFiles >= files.size) return
        job = viewModelScope.launch {
            val before = _state.value.days.size
            shownFiles += PAGE_SIZE
            load()
            extendUntil(minDays = before + 1)
        }
    }

    /** Some notes are blank, so a page may add no days; keep going until one shows or files run out. */
    private suspend fun extendUntil(minDays: Int) {
        while (_state.value.days.size < minDays && shownFiles < files.size) {
            shownFiles += PAGE_SIZE
            load()
        }
    }

    /** Reads uncached files among the shown ones and publishes the days. */
    private suspend fun load() {
        val shown = files.take(shownFiles)
        val toRead = shown.filter { cache[it.node.documentId]?.lastModified != it.node.lastModified }
        val results = coroutineScope {
            toRead.map { file -> async(readDispatcher) { file to read(file) } }.awaitAll()
        }
        var failures = 0
        for ((file, cached) in results) {
            if (cached == null) failures++ else cache[file.node.documentId] = cached
        }
        val days = shown.mapNotNull { cache[it.node.documentId]?.day }
        _state.update {
            it.copy(
                status = TimelineStatus.Ready,
                days = days,
                hasMore = shownFiles < files.size,
                unreadable = failures,
            )
        }
    }

    /** Null if the file couldn't be read. A blank note gives a [Cached] with a null day. */
    private suspend fun read(file: NoteFile): Cached? = try {
        Cached(file.node.lastModified, Timeline.day(repository.readNote(file), file.date))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read ${file.node.name}", e)
        null
    }

    private companion object {
        const val TAG = "Timeline"
        const val PAGE_SIZE = 30
    }
}
