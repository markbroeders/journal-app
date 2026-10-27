package nl.markbroeders.journal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.data.VaultSettings
import nl.markbroeders.journal.ui.calendar.CalendarScreen
import nl.markbroeders.journal.ui.calendar.CalendarViewModel
import nl.markbroeders.journal.ui.editor.EditorScreen
import nl.markbroeders.journal.ui.editor.EditorTarget
import nl.markbroeders.journal.ui.editor.EditorViewModel
import nl.markbroeders.journal.ui.entry.EntryScreen
import nl.markbroeders.journal.ui.media.MediaScreen
import nl.markbroeders.journal.ui.media.MediaViewModel
import nl.markbroeders.journal.ui.media.MediaViewer
import nl.markbroeders.journal.ui.onthisday.OnThisDayScreen
import nl.markbroeders.journal.ui.onthisday.OnThisDayViewModel
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
    // The entry being viewed, as a saveable id ("date|time|ordinal"); it closes if a rescan removes the entry.
    var openEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    // The editor's target ("new|date|time" or "edit|date|time|ordinal"); each opening is a new session.
    var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(Tab.Timeline) }
    var showOnThisDay by rememberSaveable { mutableStateOf(false) }
    // Index into the media list of the photo shown full screen.
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
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
                val calendar = viewModel(key = "calendar-${s.settings}") { CalendarViewModel(container) }
                val onThisDay = viewModel(key = "onthisday-${s.settings}") { OnThisDayViewModel(container) }
                val media = viewModel(key = "media-${s.settings}") { MediaViewModel(container) }
                val mediaItems by media.media.collectAsStateWithLifecycle()
                val timelineState by timeline.state.collectAsStateWithLifecycle()
                // Re-scan whenever the app comes back: Obsidian or sync may have changed files.
                LifecycleResumeEffect(timeline) {
                    timeline.refresh()
                    onPauseOrDispose {}
                }
                val open = rememberOpenEntry(timeline, openEntryId, onGone = { openEntryId = null })
                val editor = editorTarget?.let(::parseEditorTarget)
                val newEntry = { date: LocalDate ->
                    editorSession++
                    editorTarget = "new|$date|${LocalTime.now().withSecond(0).withNano(0)}"
                }
                val bottomBar = @Composable {
                    JournalNavigationBar(tab, onSelect = { tab = it })
                }
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
                    viewerIndex != null && !mediaItems.isNullOrEmpty() -> MediaViewer(
                        mediaItems.orEmpty(),
                        initial = viewerIndex ?: 0,
                        onOpenEntry = { openEntryId = "${it.date}|${it.key.time}|${it.key.ordinal}" },
                        onBack = { viewerIndex = null },
                    )
                    showOnThisDay -> OnThisDayScreen(
                        onThisDay,
                        onOpenEntry = { openEntryId = entryId(it) },
                        onBack = { showOnThisDay = false },
                    )
                    tab == Tab.Media -> {
                        BackHandler { tab = Tab.Timeline }
                        screenStates.SaveableStateProvider("media") {
                            MediaScreen(
                                media,
                                onOpen = { item -> viewerIndex = mediaItems?.indexOf(item)?.takeIf { it >= 0 } },
                                bottomBar = bottomBar,
                            )
                        }
                    }
                    tab == Tab.Calendar -> {
                        BackHandler { tab = Tab.Timeline }
                        screenStates.SaveableStateProvider("calendar") {
                            CalendarScreen(
                                calendar,
                                onOpenEntry = { openEntryId = entryId(it) },
                                onNewEntry = newEntry,
                                bottomBar = bottomBar,
                            )
                        }
                    }
                    // Keeps the scroll position while another screen is open.
                    else -> screenStates.SaveableStateProvider("timeline") {
                        val today = LocalDate.now()
                        val earlier by remember(today) { onThisDay.matches(today) }
                            .collectAsStateWithLifecycle(emptyList())
                        TimelineScreen(
                            timeline,
                            s.settings.journalFolder,
                            onOpenEntry = { openEntryId = entryId(it) },
                            onNewEntry = { newEntry(LocalDate.now()) },
                            onOpenSettings = {
                                setupSession++
                                editingSettings = true
                            },
                            bottomBar = bottomBar,
                            onThisDay = earlier,
                            onOpenOnThisDay = {
                                onThisDay.show(today)
                                showOnThisDay = true
                            },
                        )
                    }
                }
            }
    }
}

private enum class Tab { Timeline, Calendar, Media }

@Composable
private fun JournalNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = selected == Tab.Timeline,
            onClick = { onSelect(Tab.Timeline) },
            icon = { Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null) },
            label = { Text("Journal") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == Tab.Calendar,
            onClick = { onSelect(Tab.Calendar) },
            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            label = { Text("Calendar") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == Tab.Media,
            onClick = { onSelect(Tab.Media) },
            icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
            label = { Text("Media") },
            colors = colors,
        )
    }
}

/**
 * The entry for [id] ("date|time|ordinal") with its day, read from the index and kept up to date.
 * Null while loading or when nothing is open; calls [onGone] if the entry no longer exists
 * (e.g. a rescan found it deleted).
 */
@Composable
private fun rememberOpenEntry(
    timeline: TimelineViewModel,
    id: String?,
    onGone: () -> Unit,
): Pair<TimelineDay, TimelineEntry>? {
    val date = id?.let { LocalDate.parse(it.substringBefore('|')) }
    val loaded by produceState<Pair<String, TimelineDay?>?>(null, id) {
        value = null
        if (id != null && date != null) timeline.day(date).collect { value = id to it }
    }
    val current = loaded?.takeIf { it.first == id } ?: return null
    val day = current.second
    val entry = day?.entries?.firstOrNull { entryId(it) == id }
    if (entry == null) {
        LaunchedEffect(id) { onGone() }
        return null
    }
    return day to entry
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
