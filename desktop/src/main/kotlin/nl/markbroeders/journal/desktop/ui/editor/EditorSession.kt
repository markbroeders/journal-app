package nl.markbroeders.journal.desktop.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markbroeders.journal.data.DayEdit
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.InlineField
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.data.Photos
import nl.markbroeders.journal.desktop.data.JournalFiles
import nl.markbroeders.journal.desktop.data.PhotoImporter
import nl.markbroeders.journal.desktop.data.SaveResult
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import kotlin.io.path.name

sealed interface EditorTarget {
    val date: LocalDate

    data class New(override val date: LocalDate, val time: LocalTime) : EditorTarget
    data class Existing(override val date: LocalDate, val key: EntryKey) : EditorTarget
}

/**
 * Edits one entry, or writes a new one, like the Android app's EditorViewModel. The entry is
 * loaded fresh from the file, and saving merges into the file as it is at that moment (see
 * [JournalFiles.save]). [onSaved] gets the date and key of the saved entry.
 */
class EditorSession(
    private val files: JournalFiles,
    val target: EditorTarget,
    private val scope: CoroutineScope,
    /** Names an embed could already resolve to, so new photos never take one. */
    private val takenNames: () -> Set<String>,
    private val onSaved: (LocalDate, EntryKey?) -> Unit,
    private val onClosed: () -> Unit,
) {
    val isNew = target is EditorTarget.New

    var loading by mutableStateOf(true); private set
    var date by mutableStateOf(target.date); private set

    /** Null for the untimed entry. */
    var time by mutableStateOf((target as? EditorTarget.New)?.time ?: (target as EditorTarget.Existing).key.time); private set
    var text by mutableStateOf(TextFieldValue("")); private set
    var mood by mutableStateOf<Int?>(null); private set
    var starred by mutableStateOf(false); private set

    /** The day's frontmatter tags. */
    var tags by mutableStateOf<List<String>>(emptyList()); private set
    var location by mutableStateOf<String?>(null); private set
    var weather by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    var addingPhotos by mutableStateOf(false); private set

    /** The entry changed on disk while editing; the user must choose. */
    var conflict by mutableStateOf(false); private set

    /** The entry no longer exists in the file. */
    var missing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    private val photos = PhotoImporter(files)

    /** Photos added in this session; any that end up unused are removed when the editor closes. */
    private val imported = mutableListOf<Path>()

    private var originalRaw = ""
    private var originalFields: List<InlineField> = emptyList()
    private var initial = Snapshot("", null, false, emptyList())

    private data class Snapshot(val body: String, val mood: Int?, val starred: Boolean, val tags: List<String>)

    init {
        load(target.date)
    }

    val isDirty: Boolean get() = Snapshot(text.text, mood, starred, tags) != initial

    fun onTextChange(value: TextFieldValue) {
        text = value
    }

    fun onMoodChange(value: Int?) {
        mood = value
    }

    fun onStarredChange(value: Boolean) {
        starred = value
    }

    fun onTagsChange(value: List<String>) {
        tags = value.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct()
    }

    /** New entries only: moving to another day reloads that day's star and tags. */
    fun onDateChange(value: LocalDate) {
        if (!isNew || value == date) return
        date = value
        load(value)
    }

    fun onTimeChange(value: LocalTime) {
        if (isNew) time = value
    }

    fun dismissError() {
        error = null
    }

    /** Copies [sources] into the attachment folder and embeds them at the cursor. */
    fun addPhotos(sources: List<Path>) {
        if (sources.isEmpty() || loading || missing || addingPhotos) return
        addingPhotos = true
        val day = date
        // The untimed day note has no time of its own; name its photos after the current time.
        val at = time ?: LocalTime.now()
        scope.launch {
            val created = try {
                withContext(Dispatchers.IO) { photos.import(day, at, sources, takenNames()) }
            } catch (e: Exception) {
                error = e.message ?: "The photo couldn't be added."
                emptyList()
            }
            imported += created
            val (body, cursor) = Photos.insertEmbeds(text.text, text.selection.start, created.map { it.name })
            text = TextFieldValue(body, TextRange(cursor))
            addingPhotos = false
        }
    }

    /** Saves if anything changed (a new entry needs text), then closes and runs [then]. */
    fun saveAndClose(force: Boolean = false, then: (() -> Unit)? = null) {
        if (loading || saving) return
        val body = text.text
        val newWithoutText = isNew && body.isBlank()
        if (!isDirty || newWithoutText || missing) {
            close(if (newWithoutText) "" else body, then)
            return
        }
        // Fields are only rewritten when the mood was actually changed.
        val fields = if (mood == initial.mood) originalFields else fieldsWithMood(originalFields, mood)
        val change = if (isNew) {
            EntryChange.Add(time!!, body, fields)
        } else {
            EntryChange.Edit((target as EditorTarget.Existing).key, originalRaw, body, fields)
        }
        val edit = NoteEdit(change, DayEdit(starred = starred.takeIf { it != initial.starred }, tags = tags.takeIf { it != initial.tags }))
        val day = date
        saving = true
        conflict = false
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { files.save(day, edit, force = force) }
            saving = false
            when (result) {
                is SaveResult.Saved -> {
                    withContext(Dispatchers.IO) { removeUnusedPhotos(body) }
                    onSaved(day, result.key)
                    then?.invoke()
                }
                SaveResult.Conflict -> conflict = true
                // Only happens when deleting; an edit never blanks a note.
                SaveResult.NoteWouldBeBlank -> onClosed()
                is SaveResult.Failed -> error = result.error.message ?: "Saving failed."
            }
        }
    }

    /** Throws away the user's changes, including photos added in this session. */
    fun discard() {
        conflict = false
        close("")
    }

    private fun close(body: String, then: (() -> Unit)? = null) {
        scope.launch {
            withContext(Dispatchers.IO) { removeUnusedPhotos(body) }
            onClosed()
            then?.invoke()
        }
    }

    private fun removeUnusedPhotos(body: String) {
        val unused = imported.filter { Photos.embed(it.name) !in body }
        photos.delete(unused)
        imported -= unused.toSet()
    }

    private fun load(day: LocalDate) {
        loading = true
        scope.launch {
            try {
                val note = withContext(Dispatchers.IO) { files.loadDay(day) }
                if (day != date) return@launch
                val entry = (target as? EditorTarget.Existing)?.let { note.entry(it.key) }
                if (target is EditorTarget.Existing && entry == null) {
                    missing = true
                    loading = false
                    return@launch
                }
                originalRaw = entry?.raw.orEmpty()
                originalFields = entry?.fields.orEmpty()
                // An existing entry starts from the file; a new one keeps what was typed (date change).
                if (entry != null) text = TextFieldValue(entry.body, TextRange(entry.body.length))
                if (entry != null) mood = entry.mood
                starred = note.starred
                tags = note.frontmatter?.tags.orEmpty()
                location = entry?.location
                weather = entry?.weather
                initial = Snapshot(entry?.body.orEmpty(), entry?.mood, note.starred, tags)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read the note."
            }
            loading = false
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
}
