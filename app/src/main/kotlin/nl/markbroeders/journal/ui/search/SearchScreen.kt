package nl.markbroeders.journal.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors

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
            TopAppBar(
                title = { SearchField(text, viewModel::setText, focus, onSearch = { keyboard?.hide() }) },
                colors = journalTopBarColors(),
            )
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
                        item(key = "tags-header") { SectionHeader("Tags") }
                        item(key = "tags") {
                            FlowRow(
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (tag in tags) {
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.addTag(tag.tag)
                                            focus.requestFocus()
                                        },
                                        label = { Text("#${tag.tag}  ${tag.count}") },
                                    )
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
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    for ((day, snippets) in current.days) {
                        item(key = "day-${day.date}-${day.entries.first().key}") { SectionHeader(longDate(day.date)) }
                        day.entries.forEachIndexed { i, entry ->
                            item(key = "entry-${entry.date}-${entry.key.time}-${entry.key.ordinal}") {
                                EntryRow(
                                    day, entry,
                                    firstOfDay = i == 0,
                                    lastOfDay = i == day.entries.lastIndex,
                                    dateColumn = false,
                                    preview = snippets[i]?.let { highlighted(it, entry.title) },
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

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, focus: FocusRequester, onSearch: () -> Unit) {
    val onHeader = LocalJournalColors.current.onHeader
    // Keeps the cursor; text set from outside (a tag chip, clear) puts the cursor at the end.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    TextField(
        value = field,
        onValueChange = {
            field = it
            onChange(it.text)
        },
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = onHeader,
            unfocusedTextColor = onHeader,
            cursorColor = onHeader,
            focusedPlaceholderColor = onHeader.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = onHeader.copy(alpha = 0.7f),
            focusedLeadingIconColor = onHeader,
            unfocusedLeadingIconColor = onHeader,
            focusedTrailingIconColor = onHeader,
            unfocusedTrailingIconColor = onHeader,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
            .focusRequester(focus),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
