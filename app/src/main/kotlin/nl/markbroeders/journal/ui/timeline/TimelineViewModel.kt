package nl.markbroeders.journal.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.data.SaveResult
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.data.VaultSettings
import nl.markbroeders.journal.data.toTimelineDay
import nl.markbroeders.journal.sync.ScanResult
import java.time.LocalDate

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
    /** Files that couldn't be read in the last scan. */
    val unreadable: Int = 0,
    val deletion: Deletion? = null,
)

/**
 * Shows the days from the index, a page at a time as the user scrolls, and asks
 * [nl.markbroeders.journal.sync.VaultScanner] to bring the index up to date on [refresh].
 * The index updates the list as the scan writes rows, so a first scan fills it from the top.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(
    container: AppContainer,
    private val settings: VaultSettings,
) : ViewModel() {

    private val repository = container.journal
    private val scanner = container.scanner
    private val dao = container.index.dao()
    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private val limit = MutableStateFlow(PAGE_SIZE)
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            // Drop an index of another vault before showing anything from it.
            scanner.useVault(settings)
            // One extra row tells whether there is another page.
            val days = limit.flatMapLatest { n -> dao.observeDays(n + 1) }
            combine(days, dao.observeUnreadable()) { rows, unreadable -> rows to unreadable }
                .collect { (rows, unreadable) ->
                    val n = limit.value
                    _state.update {
                        it.copy(
                            // Until the first scan finishes, an empty index just means "not scanned yet".
                            status = if (it.status == TimelineStatus.Loading && rows.isNotEmpty()) {
                                TimelineStatus.Ready
                            } else {
                                it.status
                            },
                            days = rows.take(n).map { r -> r.toTimelineDay() },
                            hasMore = rows.size > n,
                            unreadable = unreadable,
                        )
                    }
                }
        }
    }

    /**
     * Rescans the journal folder; only changed files are read. A running scan is restarted, so
     * changes made since it listed the folder are picked up (batches it already wrote are kept).
     */
    fun refresh(userInitiated: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (userInitiated) _state.update { it.copy(refreshing = true) }
            try {
                val result = scanner.scan(settings)
                val status = if (result == ScanResult.FolderMissing) TimelineStatus.FolderMissing else TimelineStatus.Ready
                _state.update { it.copy(status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Scanning the journal failed", e)
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

    /** The day for [date] as indexed (null if it has no entries), kept up to date. */
    fun day(date: LocalDate): Flow<TimelineDay?> =
        dao.observeDay(date).map { rows -> rows.firstOrNull()?.toTimelineDay() }

    fun dismissDeletion() = _state.update { it.copy(deletion = null) }

    /** Shows the next page of days, if there is one. */
    fun loadMore() {
        if (_state.value.hasMore) limit.update { it + PAGE_SIZE }
    }

    private companion object {
        const val TAG = "Timeline"
        const val PAGE_SIZE = 30
    }
}
