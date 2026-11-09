package nl.markbroeders.journal.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.data.SearchMatcher
import nl.markbroeders.journal.data.SearchQuery
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.desktop.data.IndexState
import nl.markbroeders.journal.desktop.ui.components.EntryRow
import nl.markbroeders.journal.desktop.ui.components.ListHeader
import nl.markbroeders.journal.desktop.ui.components.ScreenHeader
import nl.markbroeders.journal.desktop.ui.components.longDate
import nl.markbroeders.journal.desktop.ui.components.monthYear
import nl.markbroeders.journal.desktop.ui.editor.EditorTarget
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors
import java.time.LocalDate

/** One entry in the list, with its day, where it falls in the day, and a search snippet. */
private data class Row(
    val day: TimelineDay,
    val entry: TimelineEntry,
    val first: Boolean,
    val last: Boolean,
    val snippet: AnnotatedString?,
)

/** The journal's timeline (newest first) with the search field above it. */
@Composable
fun Sidebar(controller: AppController, state: IndexState, modifier: Modifier) {
    val matcher = remember(controller.query) { SearchMatcher(SearchQuery.parse(controller.query)) }
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val rows = remember(state.days, matcher, highlight) { rows(state.days, matcher, highlight) }
    val selected = when (val d = controller.detail) {
        is Detail.View -> d.date to d.key
        is Detail.Edit -> (d.session.target as? EditorTarget.Existing)?.let { it.date to it.key }
        Detail.None -> null
    }

    Column(modifier) {
        ScreenHeader(
            title = "Journal",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            tint = MaterialTheme.colorScheme.primary,
            subtitle = longDate(LocalDate.now()).substringAfter(" · "),
        ) {
            IconButton(onClick = { controller.showSettings = true }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = { controller.newEntry() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New entry (Ctrl+N)")
            }
        }
        SearchField(controller)
        if (!matcher.isEmpty) {
            Text(
                when (val n = rows.size) {
                    0 -> "No entries found"
                    1 -> "1 entry"
                    else -> "$n entries"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.folderMissing -> Message(
                    "There's no ${state.settings?.journalFolder ?: "journal"} folder in this vault yet. " +
                        "It's created with the first entry, or choose another folder in Settings.",
                )
                state.days.isEmpty() -> Message("No entries yet. Press + (or Ctrl+N) to write the first one.")
                else -> {
                    val list = rememberLazyListState()
                    LaunchedEffect(controller.query) { list.scrollToItem(0) }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        var month: Pair<Int, Int>? = null
                        for (row in rows) {
                            val m = row.day.date.year to row.day.date.monthValue
                            if (m != month && row.first) {
                                month = m
                                item(key = "month-$m") { ListHeader(monthYear(row.day.date)) }
                            }
                            item(key = "${row.day.date}-${row.entry.key.time}-${row.entry.key.ordinal}") {
                                EntryRow(
                                    day = row.day,
                                    entry = row.entry,
                                    firstOfDay = row.first,
                                    lastOfDay = row.last,
                                    preview = row.snippet,
                                    selected = selected == (row.day.date to row.entry.key),
                                    onClick = { controller.select(row.day.date, row.entry.key) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(controller: AppController) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(controller.searchFocusRequests) { if (controller.searchFocusRequests > 0) focus.requestFocus() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LocalJournalColors.current.chip,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)) {
                if (controller.query.isEmpty()) {
                    Text("Search (Ctrl+F): words, \"phrases\", #tags", style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                BasicTextField(
                    value = controller.query,
                    onValueChange = { controller.query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag("search")
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && controller.query.isNotEmpty()) {
                                controller.query = ""
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            if (controller.query.isNotEmpty()) {
                IconButton(onClick = { controller.query = "" }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** The list's rows: every entry, or the ones matching a search with their matches highlighted. */
private fun rows(days: List<TimelineDay>, matcher: SearchMatcher, highlight: androidx.compose.ui.graphics.Color): List<Row> =
    days.flatMap { day ->
        val entries = if (matcher.isEmpty) {
            day.entries
        } else {
            day.entries.filter { matcher.matches(listOfNotNull(it.text, it.location).joinToString("\n"), it.tags + day.tags) }
        }
        entries.mapIndexed { i, entry ->
            Row(day, entry, first = i == 0, last = i == entries.lastIndex, snippet = if (matcher.isEmpty) null else snippet(entry, matcher, highlight))
        }
    }

/**
 * The entry's preview with the matches marked: the text from a little before the first match
 * (the title stays the title when there is one), like the Android app's search snippets.
 */
private fun snippet(entry: TimelineEntry, matcher: SearchMatcher, highlight: androidx.compose.ui.graphics.Color): AnnotatedString? {
    val text = if (entry.hasTitle) entry.text.substringAfter(entry.title, "").trim() else entry.text
    val flat = text.replace(Regex("""\s+"""), " ")
    val ranges = matcher.highlights(flat)
    if (ranges.isEmpty()) return null
    val start = ranges.first().first.let { first ->
        if (first < SNIPPET_LEAD) 0 else flat.lastIndexOf(' ', first - SNIPPET_LEAD).let { if (it < 0) 0 else it + 1 }
    }
    return buildAnnotatedString {
        if (start > 0) append("…")
        val offset = length - start
        append(flat.substring(start))
        for (range in ranges) if (range.first >= start) {
            addStyle(SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold), range.first + offset, range.last + 1 + offset)
        }
    }
}

private const val SNIPPET_LEAD = 30
