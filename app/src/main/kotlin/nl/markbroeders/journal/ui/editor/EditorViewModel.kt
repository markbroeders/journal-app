package nl.markbroeders.journal.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.DayEdit
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.InlineField
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.data.SaveResult
import nl.markbroeders.journal.data.VaultSettings
import java.time.LocalDate
import java.time.LocalTime

sealed interface EditorTarget {
    val date: LocalDate

    data class New(override val date: LocalDate, val time: LocalTime) : EditorTarget
    data class Existing(override val date: LocalDate, val key: EntryKey) : EditorTarget
}

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean,
    val date: LocalDate,
    /** Null for the untimed entry. */
    val time: LocalTime?,
    val body: String = "",
    val mood: Int? = null,
    val starred: Boolean = false,
    val tags: List<String> = emptyList(),
    /** Shown but not editable yet (location and weather services come later). */
    val location: String? = null,
    val weather: String? = null,
    val saving: Boolean = false,
    /** The entry changed on disk while editing; the user must choose. */
    val conflict: Boolean = false,
    /** The entry no longer exists in the file. */
    val missing: Boolean = false,
    val error: String? = null,
    /** Saved (or nothing to save): the screen can close. */
    val closed: Boolean = false,
)

/**
 * Edits one entry, or writes a new one. The entry is loaded fresh from the file, and saving
 * merges into the file as it is at that moment (see JournalRepository.save).
 */
class EditorViewModel(
    container: AppContainer,
    private val settings: VaultSettings,
    private val target: EditorTarget,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val repository = container.journal

    private val _state = MutableStateFlow(
        when (target) {
            is EditorTarget.New -> EditorUiState(isNew = true, date = target.date, time = target.time)
            is EditorTarget.Existing -> EditorUiState(isNew = false, date = target.date, time = target.key.time)
        },
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    // What was loaded, to detect changes and to build the edit.
    private var originalRaw = ""
    private var originalFields: List<InlineField> = emptyList()
    private var initial = Snapshot("", null, false, emptyList())
    private var loadJob: Job? = null

    private data class Snapshot(val body: String, val mood: Int?, val starred: Boolean, val tags: List<String>)

    init {
        load(target.date)
    }

    fun onBodyChange(text: String) {
        saved[KEY_BODY] = text
        _state.update { it.copy(body = text) }
    }

    fun onMoodChange(mood: Int?) = _state.update { it.copy(mood = mood) }

    fun onStarredChange(starred: Boolean) = _state.update { it.copy(starred = starred) }

    fun onTagsChange(tags: List<String>) =
        _state.update { it.copy(tags = tags.map { t -> t.trim().removePrefix("#") }.filter { t -> t.isNotEmpty() }.distinct()) }

    /** New entries only: moving to another day reloads that day's star and tags. */
    fun onDateChange(date: LocalDate) {
        if (!_state.value.isNew || date == _state.value.date) return
        _state.update { it.copy(date = date) }
        load(date)
    }

    fun onTimeChange(time: LocalTime) {
        if (_state.value.isNew) _state.update { it.copy(time = time) }
    }

    val isDirty: Boolean
        get() = _state.value.let { Snapshot(it.body, it.mood, it.starred, it.tags) != initial }

    /** Saves if anything changed (a new entry needs text), then closes. */
    fun saveAndClose(force: Boolean = false) {
        val s = _state.value
        if (s.loading || s.saving) return
        val newWithoutText = s.isNew && s.body.isBlank()
        if (!isDirty || newWithoutText || s.missing) {
            _state.update { it.copy(closed = true) }
            return
        }
        // Fields are only rewritten when the mood was actually changed.
        val fields = if (s.mood == initial.mood) originalFields else fieldsWithMood(originalFields, s.mood)
        val change = if (s.isNew) {
            EntryChange.Add(s.time!!, s.body, fields)
        } else {
            EntryChange.Edit((target as EditorTarget.Existing).key, originalRaw, s.body, fields)
        }
        val day = DayEdit(
            starred = s.starred.takeIf { it != initial.starred },
            tags = s.tags.takeIf { it != initial.tags },
        )
        _state.update { it.copy(saving = true, conflict = false, error = null) }
        viewModelScope.launch {
            when (val result = repository.save(settings, s.date, NoteEdit(change, day), force = force)) {
                is SaveResult.Saved -> _state.update { it.copy(saving = false, closed = true) }
                SaveResult.Conflict -> _state.update { it.copy(saving = false, conflict = true) }
                // Only happens when deleting; an edit never blanks a note.
                SaveResult.NoteWouldBeBlank -> _state.update { it.copy(saving = false, closed = true) }
                is SaveResult.Failed -> _state.update {
                    it.copy(saving = false, error = result.error.message ?: "Saving failed.")
                }
            }
        }
    }

    /** Throws away the user's changes. */
    fun discard() = _state.update { it.copy(closed = true, conflict = false) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun load(date: LocalDate) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val note = repository.loadDay(settings, date)
                val entry = (target as? EditorTarget.Existing)?.let { note.entry(it.key) }
                if (target is EditorTarget.Existing && entry == null) {
                    _state.update { it.copy(loading = false, missing = true) }
                    return@launch
                }
                originalRaw = entry?.raw.orEmpty()
                originalFields = entry?.fields.orEmpty()
                val tags = note.frontmatter?.tags.orEmpty()
                // An existing entry starts from the file; a new one keeps what was typed (date change).
                val body = if (entry != null) entry.body else _state.value.body
                initial = Snapshot(entry?.body.orEmpty(), entry?.mood, note.starred, tags)
                _state.update {
                    it.copy(
                        loading = false,
                        // A draft restored after process death wins over the file.
                        body = saved.get<String>(KEY_BODY) ?: body,
                        mood = if (it.isNew) it.mood else entry?.mood,
                        starred = note.starred,
                        tags = tags,
                        location = entry?.location,
                        weather = entry?.weather,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't read the note.") }
            }
        }
    }

    /** Sets or removes `mood::`, keeping every other field (known or not) and its position. */
    private fun fieldsWithMood(fields: List<InlineField>, mood: Int?): List<InlineField> {
        val existing = fields.firstOrNull { it.key.equals("mood", ignoreCase = true) }
        return when {
            mood == null -> fields.filterNot { it === existing }
            existing != null -> fields.map { if (it === existing) it.copy(value = mood.toString()) else it }
            else -> fields + InlineField("mood", mood.toString())
        }
    }

    private companion object {
        const val KEY_BODY = "body"
    }
}
