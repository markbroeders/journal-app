package nl.markbroeders.journal

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch
import nl.markbroeders.journal.capture.QuickCapture
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.data.VaultSettings
import nl.markbroeders.journal.lock.authenticate
import nl.markbroeders.journal.lock.isDeviceSecure
import nl.markbroeders.journal.ui.calendar.CalendarScreen
import nl.markbroeders.journal.ui.calendar.CalendarViewModel
import nl.markbroeders.journal.ui.editor.EditorScreen
import nl.markbroeders.journal.ui.editor.EditorTarget
import nl.markbroeders.journal.ui.editor.EditorViewModel
import nl.markbroeders.journal.ui.entry.EntryScreen
import nl.markbroeders.journal.ui.lock.LockScreen
import nl.markbroeders.journal.ui.media.MediaScreen
import nl.markbroeders.journal.ui.media.MediaViewModel
import nl.markbroeders.journal.ui.media.MediaViewer
import nl.markbroeders.journal.ui.onthisday.OnThisDayScreen
import nl.markbroeders.journal.ui.onthisday.OnThisDayViewModel
import nl.markbroeders.journal.ui.AppState
import nl.markbroeders.journal.ui.AppViewModel
import nl.markbroeders.journal.ui.search.SearchScreen
import nl.markbroeders.journal.ui.search.SearchViewModel
import nl.markbroeders.journal.ui.setup.SetupScreen
import nl.markbroeders.journal.ui.setup.SetupViewModel
import nl.markbroeders.journal.ui.timeline.TimelineScreen
import nl.markbroeders.journal.ui.timeline.TimelineViewModel
import nl.markbroeders.journal.ui.theme.JournalTheme

// A FragmentActivity because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    private lateinit var container: AppContainer

    // An activity started for a result (photo picker, camera, folder picker) doesn't lock the app
    // when we come back from it within RESULT_GRACE_MS; leaving it any other way does.
    private var awayForResult = false
    private var stoppedAt = 0L
    private var lockError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blue header: light status bar icons. Light theme: dark navigation bar icons,
        // whatever the system dark mode setting is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        container = (application as JournalApp).container
        // While the lock is on, the recents screen shows no content.
        lifecycleScope.launch { container.settings.appLock.collect(::hideFromRecents) }
        // After a restore the editor (and its draft) is already part of the saved state.
        if (savedInstanceState == null) capture = QuickCapture.from(intent)
        setContent {
            // Not lifecycle-aware on purpose: the lock taken in onStop must be there on the first frame back.
            val lockEnabled by container.settings.appLock.collectAsState(initial = null)
            val unlocked by container.lock.unlocked.collectAsState()
            val promptRequest by container.lock.promptRequests.collectAsState()
            JournalTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Nothing is shown until it's known whether the app is locked.
                    val enabled = lockEnabled ?: return@Surface
                    JournalRoot(
                        container,
                        capture = capture,
                        onCaptureTaken = { capture = null },
                        onFinish = ::finish,
                        lock = LockUi(
                            enabled = enabled,
                            locked = enabled && !unlocked,
                            promptRequest = promptRequest,
                            error = lockError,
                            onUnlock = ::unlock,
                            onSetEnabled = ::setLockEnabled,
                        ),
                    )
                }
            }
        }
    }

    private fun unlock() {
        // Without a screen lock there is nothing to check against (and the device itself is open).
        if (!isDeviceSecure(this)) {
            container.lock.unlock()
            return
        }
        authenticate(
            title = "Unlock Journal",
            onSuccess = {
                lockError = null
                container.lock.unlock()
            },
            onError = { lockError = it },
        )
    }

    /** Turning the lock on needs a successful unlock first, so it can't lock the user out. */
    private fun setLockEnabled(enabled: Boolean) {
        if (!enabled) {
            lifecycleScope.launch { container.settings.setAppLock(false) }
            return
        }
        if (!isDeviceSecure(this)) return
        authenticate(
            title = "Turn on app lock",
            subtitle = "Confirm it's you",
            onSuccess = {
                container.lock.unlock()
                lifecycleScope.launch { container.settings.setAppLock(true) }
            },
        )
    }

    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            // A blank snapshot in recents; screenshots still work.
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStart() {
        super.onStart()
        if (awayForResult && SystemClock.elapsedRealtime() - stoppedAt > RESULT_GRACE_MS) container.lock.lock()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        if (!awayForResult && !isChangingConfigurations) container.lock.lock()
    }

    // Activity result launchers (Compose's included) end up here, but so does a plain startActivity
    // (a link, a video, Android's settings) with requestCode -1: no result comes back from those.
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (requestCode < 0) {
            @Suppress("DEPRECATION")
            super.startActivityForResult(intent, requestCode, options)
            return
        }
        awayForResult = true
        try {
            @Suppress("DEPRECATION")
            super.startActivityForResult(intent, requestCode, options)
        } catch (e: RuntimeException) {
            awayForResult = false
            throw e
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        awayForResult = false
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private companion object {
        const val RESULT_GRACE_MS = 5 * 60_000L
    }

    private var capture by mutableStateOf<QuickCapture?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        QuickCapture.from(intent)?.let { capture = it }
    }
}

/** The app lock as the UI sees it. */
private class LockUi(
    val enabled: Boolean,
    val locked: Boolean,
    /** See [nl.markbroeders.journal.lock.AppLock.promptRequests]. */
    val promptRequest: Int,
    val error: String?,
    val onUnlock: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
)

/**
 * @param capture a new entry to open right away (share, widget, shortcut); [onCaptureTaken] is
 *   called once it is open. A shared entry calls [onFinish] when it closes, to return to the
 *   app it came from.
 *
 * While [lock] is locked only the lock screen shows, except for a quick capture: that opens the
 * new entry's editor in write-only mode (nothing of the journal visible), and closing it leaves
 * the app, still locked.
 */
@Composable
private fun JournalRoot(
    container: AppContainer,
    capture: QuickCapture?,
    onCaptureTaken: () -> Unit,
    onFinish: () -> Unit,
    lock: LockUi,
) {
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
    // What the open editor was started with by a quick capture (not saved: the editor's draft is).
    var editorCapture by remember { mutableStateOf<QuickCapture?>(null) }
    var closeAfterEditor by rememberSaveable { mutableStateOf(false) }
    // The open editor is a quick capture made while locked.
    var writeOnly by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.Timeline) }
    var showOnThisDay by rememberSaveable { mutableStateOf(false) }
    // Index into the media list of the photo shown full screen.
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val screenStates = rememberSaveableStateHolder()
    // Keeps the screens' state (and pending photo/camera results) while the lock screen replaces them.
    val lockedStates = rememberSaveableStateHolder()

    if (capture != null && state is AppState.Ready) {
        LaunchedEffect(capture) {
            editingSettings = false
            openEntryId = null
            viewerIndex = null
            showOnThisDay = false
            editorCapture = capture
            writeOnly = lock.locked
            // While locked every capture leaves the app when done, instead of showing the journal.
            closeAfterEditor = capture is QuickCapture.Share || lock.locked
            editorSession++
            editorTarget = "new|${LocalDate.now()}|${LocalTime.now().withSecond(0).withNano(0)}"
            onCaptureTaken()
        }
    }

    if (lock.locked && !(writeOnly && editorTarget != null)) {
        // A capture about to open its editor (above) doesn't ask to unlock first.
        if (capture == null || state is AppState.NeedsSetup) {
            LockScreen(lock.promptRequest, lock.error, onUnlock = lock.onUnlock, onExit = onFinish)
        }
        return
    }

    lockedStates.SaveableStateProvider("journal") {
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
                        lock = lock,
                    )
                } else {
                    // A new vault or folder gets a fresh timeline.
                    val timeline = viewModel(key = "timeline-${s.settings}") { TimelineViewModel(container, s.settings) }
                    val calendar = viewModel(key = "calendar-${s.settings}") { CalendarViewModel(container) }
                    val onThisDay = viewModel(key = "onthisday-${s.settings}") { OnThisDayViewModel(container) }
                    val media = viewModel(key = "media-${s.settings}") { MediaViewModel(container) }
                    val search = viewModel(key = "search-${s.settings}") { SearchViewModel(container) }
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
                                val shared = editorCapture as? QuickCapture.Share
                                EditorViewModel(
                                    container, s.settings, editor, createSavedStateHandle(),
                                    initialText = shared?.text.orEmpty(),
                                    initialPhotos = shared?.photos.orEmpty(),
                                    writeOnly = writeOnly,
                                )
                            }
                            EditorScreen(
                                vm,
                                startWithCamera = (editorCapture as? QuickCapture.NewEntry)?.camera == true,
                                onClosed = {
                                    editorTarget = null
                                    editorCapture = null
                                    writeOnly = false
                                    timeline.refresh()
                                    if (closeAfterEditor) {
                                        closeAfterEditor = false
                                        onFinish()
                                    }
                                },
                            )
                        }
                        viewerIndex != null && !mediaItems.isNullOrEmpty() -> MediaViewer(
                            mediaItems.orEmpty(),
                            initial = viewerIndex ?: 0,
                            onOpenEntry = {
                                openEntryId = "${it.date}|${it.key.time}|${it.key.ordinal}"
                                viewerIndex = null
                            },
                            onBack = { viewerIndex = null },
                        )
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
                                photos = mediaItems.orEmpty().filter { it.date == entry.date && it.key == entry.key },
                                onOpenPhoto = { photo ->
                                    viewerIndex = mediaItems?.indexOf(photo)?.takeIf { it >= 0 }
                                },
                                onOpenDate = { date ->
                                    openEntryId = null
                                    calendar.select(date)
                                    tab = Tab.Calendar
                                },
                                onTag = { tag ->
                                    openEntryId = null
                                    search.setText("")
                                    search.addTag(tag)
                                    tab = Tab.Search
                                },
                            )
                        }
                        showOnThisDay -> OnThisDayScreen(
                            onThisDay,
                            onOpenEntry = { openEntryId = entryId(it) },
                            onBack = { showOnThisDay = false },
                        )
                        tab == Tab.Search -> {
                            BackHandler { tab = Tab.Timeline }
                            screenStates.SaveableStateProvider("search") {
                                SearchScreen(
                                    search,
                                    onOpenEntry = { openEntryId = entryId(it) },
                                    bottomBar = bottomBar,
                                )
                            }
                        }
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
}

private enum class Tab { Timeline, Calendar, Search, Media }

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
            selected = selected == Tab.Search,
            onClick = { onSelect(Tab.Search) },
            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            label = { Text("Search") },
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
    /** Settings only (not first-run setup): the app lock switch. */
    lock: LockUi? = null,
) {
    val viewModel = viewModel(key = "setup-$session") { SetupViewModel(container, initial) }
    val reminder by container.settings.reminder.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    SetupScreen(
        viewModel,
        accessLost,
        onDone,
        onCancel,
        lockEnabled = lock?.enabled,
        onLockChange = { lock?.onSetEnabled(it) },
        // Like the lock, only in settings, not during first-run setup.
        reminder = reminder.takeIf { lock != null },
        onReminderChange = { scope.launch { container.settings.setReminder(it) } },
    )
}
