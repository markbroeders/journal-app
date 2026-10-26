package nl.markbroeders.journal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.data.VaultSettings
import nl.markbroeders.journal.ui.editor.EditorScreen
import nl.markbroeders.journal.ui.editor.EditorTarget
import nl.markbroeders.journal.ui.editor.EditorViewModel
import nl.markbroeders.journal.ui.entry.EntryScreen
import nl.markbroeders.journal.ui.AppState
import nl.markbroeders.journal.ui.AppViewModel
import nl.markbroeders.journal.ui.setup.SetupScreen
import nl.markbroeders.journal.ui.setup.SetupViewModel
import nl.markbroeders.journal.ui.timeline.TimelineScreen
import nl.markbroeders.journal.ui.timeline.TimelineViewModel
import nl.markbroeders.journal.ui.theme.JournalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blue header: light status bar icons. Light theme: dark navigation bar icons,
        // whatever the system dark mode setting is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as JournalApp).container
        setContent {
            JournalTheme {
                Surface(Modifier.fillMaxSize()) {
                    JournalRoot(container)
                }
            }
        }
    }
}

@Composable
private fun JournalRoot(container: AppContainer) {
    val appViewModel = viewModel { AppViewModel(container) }
    val state by appViewModel.state.collectAsStateWithLifecycle()
    var editingSettings by rememberSaveable { mutableStateOf(false) }
    // Each visit to the setup screen gets a fresh SetupViewModel.
    var setupSession by rememberSaveable { mutableIntStateOf(0) }
    // The entry being viewed, as a saveable id; it closes if a rescan removes the entry.
    var openEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    // The editor's target ("new|date|time" or "edit|date|time|ordinal"); each opening is a new session.
    var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    val screenStates = rememberSaveableStateHolder()

    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AppState.NeedsSetup -> SetupRoute(
            container, s.previous, setupSession,
            accessLost = s.previous != null,
            onDone = {},
            onCancel = null,
        )
        is AppState.Ready ->
            if (editingSettings) {
                SetupRoute(
                    container, s.settings, setupSession,
                    accessLost = false,
                    onDone = { editingSettings = false },
                    onCancel = { editingSettings = false },
                )
            } else {
                // A new vault or folder gets a fresh timeline.
                val timeline = viewModel(key = "timeline-${s.settings}") { TimelineViewModel(container, s.settings) }
                val timelineState by timeline.state.collectAsStateWithLifecycle()
                val open = openEntryId?.let { id ->
                    timelineState.days.firstNotNullOfOrNull { day ->
                        day.entries.firstOrNull { entryId(it) == id }?.let { day to it }
                    }
                }
                val editor = editorTarget?.let(::parseEditorTarget)
                when {
                    editor != null -> {
                        val vm = viewModel(key = "editor-$editorSession") {
                            EditorViewModel(container, s.settings, editor, createSavedStateHandle())
                        }
                        EditorScreen(vm, onClosed = {
                            editorTarget = null
                            timeline.refresh()
                        })
                    }
                    open != null -> {
                        val (day, entry) = open
                        EntryScreen(
                            day, entry,
                            deletion = timelineState.deletion,
                            onBack = { openEntryId = null },
                            onEdit = {
                                editorSession++
                                editorTarget = "edit|${entry.date}|${entry.key.time}|${entry.key.ordinal}"
                            },
                            onDelete = { deleteFile ->
                                timeline.delete(entry, deleteFile, onDeleted = { openEntryId = null })
                            },
                            onDismissDeletion = timeline::dismissDeletion,
                        )
                    }
                    // Keeps the scroll position while another screen is open.
                    else -> screenStates.SaveableStateProvider("timeline") {
                        TimelineScreen(
                            timeline,
                            s.settings.journalFolder,
                            onOpenEntry = { openEntryId = entryId(it) },
                            onNewEntry = {
                                val now = LocalDateTime.now()
                                editorSession++
                                editorTarget = "new|${now.toLocalDate()}|${now.toLocalTime().withSecond(0).withNano(0)}"
                            },
                            onOpenSettings = {
                                setupSession++
                                editingSettings = true
                            },
                        )
                    }
                }
            }
    }
}

private fun entryId(entry: TimelineEntry): String = "${entry.date}|${entry.key.time}|${entry.key.ordinal}"

private fun parseEditorTarget(value: String): EditorTarget? {
    val parts = value.split('|')
    val date = LocalDate.parse(parts[1])
    return when (parts[0]) {
        "new" -> EditorTarget.New(date, LocalTime.parse(parts[2]))
        "edit" -> EditorTarget.Existing(
            date,
            EntryKey(parts[2].takeIf { it != "null" }?.let(LocalTime::parse), parts[3].toInt()),
        )
        else -> null
    }
}

@Composable
private fun SetupRoute(
    container: AppContainer,
    initial: VaultSettings?,
    session: Int,
    accessLost: Boolean,
    onDone: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val viewModel = viewModel(key = "setup-$session") { SetupViewModel(container, initial) }
    SetupScreen(viewModel, accessLost, onDone, onCancel)
}
