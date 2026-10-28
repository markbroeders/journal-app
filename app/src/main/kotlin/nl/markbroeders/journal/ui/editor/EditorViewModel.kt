package nl.markbroeders.journal.ui.editor

import android.net.Uri
import android.util.Log
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
import nl.markbroeders.journal.data.DocNode
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.InlineField
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.data.Photos
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
    /** Where the cursor is (photos go here). */
    val cursor: Int = 0,
    /** Bumped when [body] is replaced by the editor itself (loading, photos), so the field resyncs. */
    val bodyRevision: Int = 0,
    val addingPhotos: Boolean = false,
    val photoError: String? = null,
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
 *
 * A new entry can start with [initialText] and [initialPhotos] (shared from another app); the
 * photos are copied into the vault once loaded, like photos added from the gallery.
 */
class EditorViewModel(
    container: AppContainer,
    private val settings: VaultSettings,
    private val target: EditorTarget,
    private val saved: SavedStateHandle,
    private val initialText: String = "",
    private val initialPhotos: List<Uri> = emptyList(),
) : ViewModel() {

    private val repository = container.journal
    private val photos = container.photos

    /** Photos added in this session; any that end up unused are removed when the editor closes. */
    private val imported = mutableListOf<DocNode>()

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

    fun onBodyChange(text: String, cursor: Int) {
        saved[KEY_BODY] = text
        _state.update { it.copy(body = text, cursor = cursor) }
    }

    /** Copies [uris] into the attachment folder and embeds them at the cursor. */
    fun addPhotos(uris: List<Uri>, onDone: () -> Unit = {}) {
        val s = _state.value
        if (uris.isEmpty() || s.loading || s.missing || s.addingPhotos) return onDone()
        _state.update { it.copy(addingPhotos = true) }
        viewModelScope.launch {
            // The untimed day note has no time of its own; name its photos after the current time.
            val time = s.time ?: LocalTime.now()
            val created = try {
                photos.import(settings, s.date, time, uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Adding photos failed", e)
                _state.update { it.copy(photoError = e.message ?: "The photo couldn't be added.") }
                emptyList()
            }
            imported += created
            _state.update {
                val (body, cursor) = Photos.insertEmbeds(it.body, it.cursor, created.map { f -> f.name })
                saved[KEY_BODY] = body
                it.copy(addingPhotos = false, body = body, cursor = cursor, bodyRevision = it.bodyRevision + 1)
            }
            onDone()
        }
    }

    fun dismissPhotoError() = _state.update { it.copy(photoError = null) }

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
            closeRemovingUnusedPhotos(if (newWithoutText) "" else s.body)
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
                is SaveResult.Saved -> {
                    removeUnusedPhotos(s.body)
                    _state.update { it.copy(saving = false, closed = true) }
                }
                SaveResult.Conflict -> _state.update { it.copy(saving = false, conflict = true) }
                // Only happens when deleting; an edit never blanks a note.
                SaveResult.NoteWouldBeBlank -> _state.update { it.copy(saving = false, closed = true) }
                is SaveResult.Failed -> _state.update {
                    it.copy(saving = false, error = result.error.message ?: "Saving failed.")
                }
            }
        }
    }

    /** Throws away the user's changes, including photos added in this session. */
    fun discard() {
        _state.update { it.copy(conflict = false) }
        closeRemovingUnusedPhotos("")
    }

    private fun closeRemovingUnusedPhotos(body: String) {
        viewModelScope.launch {
            removeUnusedPhotos(body)
            _state.update { it.copy(closed = true) }
        }
    }

    /** Deletes photos added in this session that [body] no longer embeds. */
    private suspend fun removeUnusedPhotos(body: String) {
        val unused = imported.filter { Photos.embed(it.name) !in body }
        if (unused.isEmpty()) return
        photos.delete(unused)
        imported -= unused.toSet()
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private val shareApplied: Boolean
        get() = target !is EditorTarget.New || saved.get<Boolean>(KEY_SHARE_APPLIED) == true

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
                // An existing entry starts from the file; a new one keeps what was typed (date change)
                // or starts with the shared text.
                val body = when {
                    entry != null -> entry.body
                    !shareApplied -> initialText
                    else -> _state.value.body
                }
                initial = Snapshot(entry?.body.orEmpty(), entry?.mood, note.starred, tags)
                val restored = saved.get<String>(KEY_BODY) ?: body
                _state.update {
                    it.copy(
                        loading = false,
                        // A draft restored after process death wins over the file.
                        body = restored,
                        cursor = restored.length,
                        bodyRevision = it.bodyRevision + 1,
                        mood = if (it.isNew) it.mood else entry?.mood,
                        starred = note.starred,
                        tags = tags,
                        location = entry?.location,
                        weather = entry?.weather,
                    )
                }
                if (!shareApplied) {
                    // Once only, also after process death (the draft then holds the result).
                    saved[KEY_SHARE_APPLIED] = true
                    if (initialText.isNotEmpty()) saved[KEY_BODY] = _state.value.body
                    addPhotos(initialPhotos)
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
        const val TAG = "Editor"
        const val KEY_BODY = "body"
        const val KEY_SHARE_APPLIED = "shareApplied"
    }
}
