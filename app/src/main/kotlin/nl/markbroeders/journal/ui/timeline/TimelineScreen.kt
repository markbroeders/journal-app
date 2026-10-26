package nl.markbroeders.journal.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.monthYear
import nl.markbroeders.journal.ui.components.moodEmoji
import nl.markbroeders.journal.ui.components.timeLabel
import nl.markbroeders.journal.ui.components.weatherIcon
import nl.markbroeders.journal.ui.components.weekdayShort
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.time.LocalDate
import java.time.YearMonth

/** Reverse-chronological feed of entries, read straight from the vault. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    journalFolder: String,
    onOpenEntry: (TimelineEntry) -> Unit,
    onNewEntry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-scan whenever the app comes back: Obsidian or sync may have changed files.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Journal") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Vault settings")
                    }
                },
                colors = journalTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewEntry,
                shape = CircleShape,
                containerColor = LocalJournalColors.current.header,
                contentColor = LocalJournalColors.current.onHeader,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New entry")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh(userInitiated = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.status == TimelineStatus.Loading -> Centered { CircularProgressIndicator() }
                state.status == TimelineStatus.FolderMissing -> Message(
                    "The journal folder “$journalFolder” doesn't exist in the vault yet.",
                    action = "Vault settings" to onOpenSettings,
                )
                state.status == TimelineStatus.Error && state.days.isEmpty() -> Message(
                    "Couldn't read the journal folder.",
                    action = "Try again" to { viewModel.refresh(userInitiated = true) },
                )
                state.days.isEmpty() && !state.hasMore -> Message("No entries yet.")
                else -> TimelineList(state, onOpenEntry, viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun TimelineList(state: TimelineUiState, onOpenEntry: (TimelineEntry) -> Unit, onLoadMore: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore)
    LazyColumn(
        state = listState,
        // Room for the floating + button.
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.unreadable > 0) {
            item(key = "unreadable") {
                Text(
                    "${state.unreadable} ${if (state.unreadable == 1) "note" else "notes"} couldn't be read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        var month: YearMonth? = null
        for (day in state.days) {
            val dayMonth = YearMonth.from(day.date)
            if (dayMonth != month) {
                month = dayMonth
                stickyHeader(key = "month-$dayMonth") { MonthHeader(day.date) }
            }
            day.entries.forEachIndexed { i, entry ->
                item(key = "entry-${entry.date}-${entry.key.time}-${entry.key.ordinal}") {
                    EntryRow(day, entry, firstOfDay = i == 0, lastOfDay = i == day.entries.lastIndex) {
                        onOpenEntry(entry)
                    }
                }
            }
        }
        if (state.hasMore) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Asks for the next page when the last few items come into view (and again after each load). */
@Composable
private fun LoadMoreEffect(listState: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            (last >= info.totalItemsCount - 5) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { onLoadMore() }
    }
}

@Composable
private fun MonthHeader(date: LocalDate) {
    Text(
        monthYear(date),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val DateColumnWidth = 44.dp

/** Day One-style row: date column (first entry of the day only), bold title, grey preview, meta line. */
@Composable
private fun EntryRow(
    day: TimelineDay,
    entry: TimelineEntry,
    firstOfDay: Boolean,
    lastOfDay: Boolean,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Column(Modifier.width(DateColumnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            if (firstOfDay) {
                Text(
                    weekdayShort(entry.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.date == today) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${entry.date.dayOfMonth}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (entry.date == today) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (day.starred || day.mood != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        day.mood?.let { Text(moodEmoji(it), style = MaterialTheme.typography.labelMedium) }
                        if (day.starred) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = "Starred day",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (entry.title.isNotEmpty()) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.preview.isNotEmpty()) {
                Text(
                    entry.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MetaLine(entry)
        }
    }
    if (!lastOfDay) {
        HorizontalDivider(Modifier.padding(start = 16.dp + DateColumnWidth + 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    } else {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MetaLine(entry: TimelineEntry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(timeLabel(entry.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        entry.mood?.let {
            Text(moodEmoji(it), style = MaterialTheme.typography.labelMedium)
        }
        if (entry.mediaCount > 0) MetaItem(Icons.Outlined.PhotoCamera, "${entry.mediaCount}")
        entry.weather?.let { MetaItem(weatherIcon(it), it.substringBefore(',').trim()) }
        entry.location?.let {
            MetaItem(Icons.Outlined.Place, it.substringBefore('(').trim(), Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Full-screen message. Wrapped in a scrollable list so pull-to-refresh still works. */
@Composable
private fun Message(text: String, action: Pair<String, () -> Unit>? = null) {
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.padding(top = 96.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
                action?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
            }
        }
    }
}
