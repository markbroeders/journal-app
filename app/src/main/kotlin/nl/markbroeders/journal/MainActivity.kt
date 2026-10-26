package nl.markbroeders.journal

import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.markbroeders.journal.data.VaultSettings
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
        enableEdgeToEdge()
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
                TimelineScreen(timeline, s.settings.journalFolder, onOpenSettings = {
                    setupSession++
                    editingSettings = true
                })
            }
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
