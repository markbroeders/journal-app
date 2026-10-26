package nl.markbroeders.journal.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.components.moodEmoji
import nl.markbroeders.journal.ui.components.weatherIcon
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Full-screen Markdown editor. Back and the check mark both save; nothing is lost by leaving.
 * [onClosed] is called once the entry is saved (or there was nothing to save).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onClosed: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { viewModel.saveAndClose() }

    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var editTags by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = viewModel::saveAndClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and close")
                    }
                },
                title = {
                    Column(
                        Modifier.clickable(enabled = state.isNew) { pickDate = true },
                    ) {
                        Text(
                            longDate(state.date),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.time?.toString() ?: "Day note",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable(enabled = state.isNew) { pickTime = true },
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::saveAndClose, enabled = !state.saving) {
                        Icon(Icons.Outlined.Check, contentDescription = "Done")
                    }
                },
                colors = journalTopBarColors(),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.missing -> Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "This entry no longer exists in the note. It may have been changed or removed in Obsidian.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> BodyField(
                    text = state.body,
                    onChange = viewModel::onBodyChange,
                    autoFocus = state.isNew,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Toolbar(
                state = state,
                onMood = viewModel::onMoodChange,
                onStar = { viewModel.onStarredChange(!state.starred) },
                onTags = { editTags = true },
            )
        }
    }

    if (pickDate) {
        DateDialog(state.date, onDismiss = { pickDate = false }) {
            viewModel.onDateChange(it)
            pickDate = false
        }
    }
    if (pickTime && state.time != null) {
        TimeDialog(state.time!!, onDismiss = { pickTime = false }) {
            viewModel.onTimeChange(it)
            pickTime = false
        }
    }
    if (editTags) {
        TagsDialog(state.tags, onDismiss = { editTags = false }) {
            viewModel.onTagsChange(it)
            editTags = false
        }
    }
    if (state.conflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Entry changed elsewhere") },
            text = {
                Text(
                    "This entry was changed in the file while you were editing (in Obsidian or by sync). " +
                        "Keep your version, or discard your changes and keep the other one?",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.saveAndClose(force = true) }) { Text("Keep mine") } },
            dismissButton = { TextButton(onClick = viewModel::discard) { Text("Discard mine") } },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Couldn't save") },
            text = { Text("$error\n\nYour text is still here. Try again, or leave without saving.") },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Keep editing") } },
            dismissButton = { TextButton(onClick = viewModel::discard) { Text("Discard my changes") } },
        )
    }
}

@Composable
private fun BodyField(text: String, onChange: (String) -> Unit, autoFocus: Boolean, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        "Start writing…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun Toolbar(state: EditorUiState, onMood: (Int?) -> Unit, onStar: () -> Unit, onTags: () -> Unit) {
    var moodMenu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { moodMenu = true }) {
                val mood = state.mood
                if (mood != null) {
                    Text(moodEmoji(mood), style = MaterialTheme.typography.titleLarge)
                } else {
                    Icon(Icons.Outlined.SentimentSatisfied, contentDescription = "Mood")
                }
            }
            DropdownMenu(expanded = moodMenu, onDismissRequest = { moodMenu = false }) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    (1..5).forEach { m ->
                        TextButton(onClick = { onMood(m); moodMenu = false }) {
                            Text(moodEmoji(m), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (state.mood != null) {
                    DropdownMenuItem(text = { Text("No mood") }, onClick = { onMood(null); moodMenu = false })
                }
            }
        }
        IconButton(onClick = onStar) {
            if (state.starred) {
                Icon(Icons.Rounded.Star, contentDescription = "Unstar day", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Rounded.StarBorder, contentDescription = "Star day")
            }
        }
        IconButton(onClick = onTags) {
            Icon(
                Icons.Outlined.Sell,
                contentDescription = "Day tags",
                tint = if (state.tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        if (state.tags.isNotEmpty()) {
            Text(
                state.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        // Location and weather are kept as they are; editing them comes with those services.
        val info = state.location?.let { Icons.Outlined.Place to it.substringBefore('(').trim() }
            ?: state.weather?.let { weatherIcon(it) to it.substringBefore(',').trim() }
        info?.let { (icon, text) ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = picker)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = picker) },
    )
}

@Composable
private fun TagsDialog(tags: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf(tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Saved in the note's frontmatter. #tags you type in the text work too.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("journal, family") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
