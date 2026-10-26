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
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.data.VaultSettings
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
        // The header is blue in light and dark theme, so status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
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
                if (open != null) {
                    EntryScreen(open.first, open.second, onBack = { openEntryId = null })
                } else {
                    // Keeps the scroll position while an entry is open.
                    screenStates.SaveableStateProvider("timeline") {
                        TimelineScreen(
                            timeline,
                            s.settings.journalFolder,
                            onOpenEntry = { openEntryId = entryId(it) },
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
