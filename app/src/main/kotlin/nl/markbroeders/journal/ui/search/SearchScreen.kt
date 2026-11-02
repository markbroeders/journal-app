package nl.markbroeders.journal.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markbroeders.journal.data.SearchSql
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.EntryRow
import nl.markbroeders.journal.ui.components.ListHeader
import nl.markbroeders.journal.ui.components.ScreenHeader
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.JournalType

/** Search field in the header; tag suggestions while it's empty, matching entries by day below. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenEntry: (TimelineEntry) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    // Start typing straight away on a fresh search.
    LaunchedEffect(Unit) {
        if (viewModel.text.value.isEmpty()) focus.requestFocus()
    }
    // Make room for the results once the user starts scrolling through them.
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) keyboard?.hide()
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                ScreenHeader("Search", Icons.Outlined.Search, LocalJournalColors.current.teal)
                SearchField(text, viewModel::setText, focus, onSearch = { keyboard?.hide() })
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            state = list,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            val current = results
            when {
                text.isBlank() -> {
                    if (tags.isEmpty()) {
                        item(key = "hint") { Message("Search for words, \"phrases\" and #tags.") }
                    } else {
                        item(key = "tags-header") { ListHeader("Tags") }
                        item(key = "tags") {
                            FlowRow(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (tag in tags) {
                                    TagCount(tag.tag, tag.count) {
                                        viewModel.addTag(tag.tag)
                                        focus.requestFocus()
                                    }
                                }
                            }
                        }
                    }
                }
                // Nothing until the first results arrive, so typing doesn't flash "no entries".
                current == null -> {}
                current.days.isEmpty() -> item(key = "empty") { Message("No entries found.") }
                else -> {
                    item(key = "count") {
                        val label = when {
                            current.limited -> "Newest ${current.count} entries"
                            current.count == 1 -> "1 entry"
                            else -> "${current.count} entries"
                        }
                        Text(
                            label,
                            style = JournalType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                        )
                    }
                    for ((day, snippets) in current.days) {
                        item(key = "day-${day.date}-${day.entries.first().key}") { ListHeader(longDate(day.date)) }
                        day.entries.forEachIndexed { i, entry ->
                            item(key = "entry-${entry.date}-${entry.key.time}-${entry.key.ordinal}") {
                                EntryRow(
                                    day, entry,
                                    firstOfDay = i == 0,
                                    lastOfDay = i == day.entries.lastIndex,
                                    dayHeader = false,
                                    preview = snippets[i]?.let { highlighted(it, if (entry.hasTitle) entry.title else "") },
                                ) {
                                    keyboard?.hide()
                                    onOpenEntry(entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A filled, rounded search field under the header, as in Vink. */
@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, focus: FocusRequester, onSearch: () -> Unit) {
    val colors = LocalJournalColors.current
    // Keeps the cursor; text set from outside (a tag chip, clear) puts the cursor at the end.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    TextField(
        value = field,
        onValueChange = {
            field = it
            onChange(it.text)
        },
        placeholder = { Text("Words, \"phrases\" or #tags") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.chip,
            unfocusedContainerColor = colors.chip,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            .focusRequester(focus),
    )
}

/** `#tag 12` as a filled pill. */
@Composable
private fun TagCount(tag: String, count: Int, onClick: () -> Unit) {
    val colors = LocalJournalColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = colors.chip, contentColor = colors.body) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("#$tag", style = MaterialTheme.typography.labelLarge)
            Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}

/**
 * The snippet on one line, with the matched words in bold on a light background. A snippet from
 * the start of the text begins with the [title], which the row already shows, so that is cut;
 * null if nothing is left (the row then shows its normal preview).
 */
@Composable
private fun highlighted(snippet: String, title: String): AnnotatedString? {
    val style = SpanStyle(
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        background = MaterialTheme.colorScheme.primaryContainer,
    )
    val flat = withoutTitle(snippet.replace(Regex("""\s+"""), " ").trim(), title.replace(Regex("""\s+"""), " "))
    if (flat.none { it.isLetterOrDigit() }) return null
    return buildAnnotatedString {
        var i = 0
        while (i < flat.length) {
            val start = flat.indexOf(SearchSql.MARK_START, i)
            if (start < 0) {
                append(flat.substring(i))
                break
            }
            append(flat.substring(i, start))
            val end = flat.indexOf(SearchSql.MARK_END, start + 1).let { if (it < 0) flat.length else it }
            withStyle(style) { append(flat.substring(start + 1, end)) }
            i = end + 1
        }
    }
}

/** [snippet] without a leading [title] (compared ignoring the highlight marks). */
private fun withoutTitle(snippet: String, title: String): String {
    if (title.isEmpty()) return snippet
    var matched = 0
    var i = 0
    while (i < snippet.length && matched < title.length) {
        val c = snippet[i]
        if (c != SearchSql.MARK_START && c != SearchSql.MARK_END) {
            if (c != title[matched]) return snippet
            matched++
        }
        i++
    }
    if (matched < title.length) return snippet
    // Keep a mark that closes right after the title out of the remaining text.
    if (i < snippet.length && snippet[i] == SearchSql.MARK_END) i++
    return snippet.substring(i).trimStart()
}
