package nl.markbroeders.journal.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.EntryRow
import nl.markbroeders.journal.ui.components.monthYear
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.time.LocalDate
import java.time.YearMonth

/** Reverse-chronological feed of entries, from the index. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    journalFolder: String,
    onOpenEntry: (TimelineEntry) -> Unit,
    onNewEntry: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        bottomBar = bottomBar,
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
