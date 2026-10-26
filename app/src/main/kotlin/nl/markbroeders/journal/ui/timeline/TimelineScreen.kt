package nl.markbroeders.journal.ui.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Reverse-chronological feed of entries, read straight from the vault. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    journalFolder: String,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-scan whenever the app comes back: Obsidian or sync may have changed files.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Vault settings")
                    }
                },
            )
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
                else -> TimelineList(state, viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun TimelineList(state: TimelineUiState, onLoadMore: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.unreadable > 0) {
            item(key = "unreadable") {
                Text(
                    "${state.unreadable} ${if (state.unreadable == 1) "note" else "notes"} couldn't be read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        for (day in state.days) {
            item(key = "day-${day.date}") { DayHeader(day) }
            items(day.entries, key = { "entry-${it.date}-${it.key.time}-${it.key.ordinal}" }) { EntryCard(it) }
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
private fun DayHeader(day: TimelineDay) {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val weekday = when (day.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    }
    val monthYear = day.date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    ) {
        Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(weekday, style = MaterialTheme.typography.titleSmall)
            Text(
                monthYear,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        day.mood?.let { Text(moodEmoji(it), style = MaterialTheme.typography.titleMedium) }
        if (day.starred) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Star, contentDescription = "Starred", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EntryCard(entry: TimelineEntry) {
    var expanded by rememberSaveable(entry.date, entry.key) { mutableStateOf(false) }
    val canExpand = entry.text != entry.snippet || entry.text.lines().size > COLLAPSED_LINES
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        // Not Card(onClick, enabled): a disabled card greys out its content.
        Column(
            Modifier
                .clickable(enabled = canExpand) { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.key.time?.toString() ?: "Day note",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (entry.mediaCount > 0) {
                    Text(
                        "${entry.mediaCount} ${if (entry.mediaCount == 1) "photo" else "photos"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.mood?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(moodEmoji(it))
                }
            }
            if (entry.text.isNotEmpty()) {
                Text(
                    if (expanded) entry.text else entry.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(entry.location, entry.weather)
            if (meta.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.location != null) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        meta.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (tag in entry.tags) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private const val COLLAPSED_LINES = 6

private fun moodEmoji(mood: Int): String = when (mood) {
    1 -> "😞"
    2 -> "🙁"
    3 -> "😐"
    4 -> "🙂"
    else -> "😄"
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Full-screen message. Wrapped in a scrollable list so pull-to-refresh still works.
 */
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
