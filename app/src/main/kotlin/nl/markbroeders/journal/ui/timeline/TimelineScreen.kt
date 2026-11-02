package nl.markbroeders.journal.ui.timeline

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.EntryRow
import nl.markbroeders.journal.ui.components.ListHeader
import nl.markbroeders.journal.ui.components.NewEntryButton
import nl.markbroeders.journal.ui.components.ScreenHeader
import nl.markbroeders.journal.ui.components.dayAndMonthWithWeekday
import nl.markbroeders.journal.ui.components.monthYear
import nl.markbroeders.journal.ui.theme.JournalType
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import java.time.LocalDate
import java.time.YearMonth

/** Reverse-chronological feed of entries, from the index. */
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    journalFolder: String,
    onOpenEntry: (TimelineEntry) -> Unit,
    onNewEntry: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    /** Today's date in earlier years, for the banner at the top (empty: no banner). */
    onThisDay: List<TimelineDay> = emptyList(),
    onOpenOnThisDay: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = bottomBar,
        topBar = {
            ScreenHeader(
                "Journal",
                Icons.AutoMirrored.Outlined.MenuBook,
                MaterialTheme.colorScheme.primary,
                subtitle = dayAndMonthWithWeekday(LocalDate.now()),
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        floatingActionButton = { NewEntryButton(onNewEntry) },
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
                else -> TimelineList(state, onOpenEntry, viewModel::loadMore, hasHeader = onThisDay.isNotEmpty()) {
                    OnThisDayBanner(onThisDay, onOpenOnThisDay)
                }
            }
        }
    }
}

@Composable
private fun TimelineList(
    state: TimelineUiState,
    onOpenEntry: (TimelineEntry) -> Unit,
    onLoadMore: () -> Unit,
    hasHeader: Boolean,
    header: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore)
    // The list keeps its first visible item in place, so a header that appears later would land
    // just above the screen; show it if the user is at the top.
    LaunchedEffect(hasHeader) {
        if (hasHeader && listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(
        state = listState,
        // Room for the floating + button.
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hasHeader) item(key = "header") { header() }
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
                stickyHeader(key = "month-$dayMonth") { ListHeader(monthYear(day.date)) }
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

/** "On This Day · 2024 · 2025" in amber, opening the On This Day screen. */
@Composable
private fun OnThisDayBanner(days: List<TimelineDay>, onClick: () -> Unit) {
    val colors = LocalJournalColors.current
    val entries = days.sumOf { it.entries.size }
    val years = days.map { it.date.year }.distinct().sorted()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.amberSoft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "On This Day: $entries ${if (entries == 1) "entry" else "entries"} from ${years.joinToString(", ")}"
            },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = colors.amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("On This Day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                years.joinToString(" · "),
                style = JournalType.meta,
                fontWeight = FontWeight.Medium,
                color = colors.amberText,
                maxLines = 1,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.amberText,
                modifier = Modifier.size(18.dp),
            )
        }
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
