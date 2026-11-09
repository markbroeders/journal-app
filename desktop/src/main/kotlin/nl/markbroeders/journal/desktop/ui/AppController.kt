package nl.markbroeders.journal.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.desktop.Platform
import nl.markbroeders.journal.desktop.data.JournalIndex
import nl.markbroeders.journal.desktop.data.SaveResult
import nl.markbroeders.journal.desktop.data.SettingsStore
import nl.markbroeders.journal.desktop.ui.editor.EditorSession
import nl.markbroeders.journal.desktop.ui.editor.EditorTarget
import java.time.LocalDate
import java.time.LocalTime

/** What the right-hand pane shows. */
sealed interface Detail {
    data object None : Detail
    data class View(val date: LocalDate, val key: EntryKey) : Detail
    data class Edit(val session: EditorSession) : Detail
}

/** Deleting an entry: asking about an empty file, or what went wrong. */
sealed interface Deletion {
    data class AskDeleteFile(val date: LocalDate, val key: EntryKey, val raw: String) : Deletion
    data class Failed(val message: String) : Deletion
}

/**
 * The window's state: which entry is shown or edited, the search, and the questions to ask
 * before leaving unsaved changes.
 */
class AppController(
    val settings: SettingsStore,
    val index: JournalIndex,
    private val scope: CoroutineScope,
) {
    var detail by mutableStateOf<Detail>(Detail.None); private set
    var query by mutableStateOf("")
    var deletion by mutableStateOf<Deletion?>(null); private set

    /** Waiting for the user to decide about unsaved changes; runs after a discard. */
    var pendingLeave by mutableStateOf<(() -> Unit)?>(null); private set

    var showSettings by mutableStateOf(false)

    /** Bumped to move the keyboard focus to the search field. */
    var searchFocusRequests by mutableStateOf(0); private set

    private val editing: EditorSession? get() = (detail as? Detail.Edit)?.session

    /** Runs [action] now, or after the user agreed to drop the unsaved entry being edited. */
    fun leaveEditor(action: () -> Unit) {
        val session = editing
        if (session == null || !session.isDirty) {
            action()
        } else {
            pendingLeave = action
        }
    }

    /** The answer to [pendingLeave]: save first (true), discard (false), or stay (null). */
    fun resolveLeave(save: Boolean?) {
        val action = pendingLeave ?: return
        pendingLeave = null
        val session = editing ?: return action()
        when (save) {
            null -> {}
            true -> session.saveAndClose(then = action)
            false -> {
                session.discard()
                action()
            }
        }
    }

    fun select(date: LocalDate, key: EntryKey) = leaveEditor { detail = Detail.View(date, key) }

    fun newEntry(date: LocalDate = LocalDate.now()) = leaveEditor {
        val time = LocalTime.now().withSecond(0).withNano(0)
        detail = Detail.Edit(session(EditorTarget.New(date, time)))
    }

    fun edit(date: LocalDate, key: EntryKey) = leaveEditor {
        detail = Detail.Edit(session(EditorTarget.Existing(date, key)))
    }

    /** Ctrl+E: edits the entry on show. */
    fun editShown() {
        val view = detail as? Detail.View ?: return
        edit(view.date, view.key)
    }

    /** Esc / Cancel in the editor: asks first if there are unsaved changes. */
    fun cancelEditing() {
        val session = editing ?: return
        if (session.isDirty) pendingLeave = {} else session.discard()
    }

    fun saveEditing() {
        editing?.saveAndClose()
    }

    fun focusSearch() {
        searchFocusRequests++
    }

    fun searchTag(tag: String) {
        query = "#$tag"
    }

    /** A `[[YYYY-MM-DD]]` link: shows that day's latest entry, if it has any. */
    fun openDate(date: LocalDate) {
        val entry = index.state.value.day(date)?.entries?.firstOrNull() ?: return
        select(date, entry.key)
    }

    fun delete(date: LocalDate, key: EntryKey, raw: String, deleteBlankFile: Boolean? = null) {
        val files = index.files ?: return
        deletion = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                files.save(date, NoteEdit(EntryChange.Delete(key, raw)), deleteBlankFile = deleteBlankFile)
            }
            when (result) {
                is SaveResult.Saved -> {
                    if ((detail as? Detail.View)?.let { it.date == date && it.key == key } == true) detail = Detail.None
                    index.refresh()
                }
                SaveResult.NoteWouldBeBlank -> deletion = Deletion.AskDeleteFile(date, key, raw)
                SaveResult.Conflict -> {
                    deletion = Deletion.Failed("This entry was changed in the file (in Obsidian or by sync) since it was shown, so it wasn't deleted. Have a look at it now and try again.")
                    index.refresh()
                }
                is SaveResult.Failed -> deletion = Deletion.Failed(result.error.message ?: "Deleting failed.")
            }
        }
    }

    fun dismissDeletion() {
        deletion = null
    }

    fun openInObsidian(date: LocalDate) {
        val settings = index.files?.settings ?: return
        Platform.openInObsidian(settings.notePath(date))
    }

    private fun session(target: EditorTarget): EditorSession {
        lateinit var session: EditorSession
        val back = (target as? EditorTarget.Existing)?.let { Detail.View(it.date, it.key) } ?: Detail.None
        session = EditorSession(
            files = index.files!!,
            target = target,
            scope = scope,
            takenNames = { index.state.value.attachments.keys },
            onSaved = { date, key ->
                index.refresh()
                if (editing === session) detail = if (key != null) Detail.View(date, key) else Detail.None
            },
            onClosed = { if (editing === session) detail = back },
        )
        return session
    }
}
