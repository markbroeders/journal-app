package nl.markbroeders.journal.desktop.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.desktop.ui.components.MetaChip
import nl.markbroeders.journal.desktop.ui.components.dayAndMonth
import nl.markbroeders.journal.desktop.ui.components.longDate
import nl.markbroeders.journal.desktop.ui.components.moodIcon
import nl.markbroeders.journal.desktop.ui.components.moodName
import nl.markbroeders.journal.desktop.ui.components.timeLabel
import nl.markbroeders.journal.desktop.ui.components.weatherIcon
import nl.markbroeders.journal.desktop.ui.theme.JournalType
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors
import nl.markbroeders.journal.desktop.ui.theme.reading
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Width of the text column: about 75 characters of entry text. */
val TextColumnWidth = 720.dp

/**
 * The editor in the right-hand pane: when (new entries), the text, the mood faces and a toolbar
 * with photos, the day's star and tags. Saving and cancelling are in the top bar (Ctrl+S, Esc).
 */
@Composable
fun EditorPane(session: EditorSession, onCancel: () -> Unit, onAddPhotos: () -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var editTags by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(session, onCancel)
        if (session.saving || session.addingPhotos) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = TextColumnWidth).fillMaxWidth().weight(1f)) {
                if (session.isNew) {
                    WhenChips(session.date, session.time, onDate = session::onDateChange, onPickDate = { pickDate = true }, onPickTime = { pickTime = true })
                }
                when {
                    session.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    session.missing -> Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "This entry no longer exists in the note. It may have been changed or removed in Obsidian.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> BodyField(session, Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider(color = LocalJournalColors.current.line)
        Toolbar(session, onAddPhotos = onAddPhotos, onTags = { editTags = true })
    }

    if (pickDate) {
        DateDialog(session.date, onDismiss = { pickDate = false }) {
            pickDate = false
            session.onDateChange(it)
        }
    }
    if (pickTime) {
        TimeDialog(session.time ?: LocalTime.now(), onDismiss = { pickTime = false }) {
            pickTime = false
            session.onTimeChange(it)
        }
    }
    if (editTags) {
        TagsDialog(session.tags, onDismiss = { editTags = false }) {
            editTags = false
            session.onTagsChange(it)
        }
    }
    if (session.conflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Entry changed elsewhere") },
            text = {
                Text(
                    "This entry was changed in the file while you were editing (in Obsidian or by sync). " +
                        "Keep your version, or discard your changes and keep the other one?",
                )
            },
            confirmButton = { TextButton(onClick = { session.saveAndClose(force = true) }) { Text("Keep mine") } },
            dismissButton = { TextButton(onClick = session::discard) { Text("Discard mine") } },
        )
    }
    session.error?.let { error ->
        AlertDialog(
            onDismissRequest = session::dismissError,
            title = { Text("Something went wrong") },
            text = { Text("$error\n\nYour text is still here.") },
            confirmButton = { TextButton(onClick = session::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun TopBar(session: EditorSession, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (session.isNew) "New entry" else "Edit entry",
                style = JournalType.title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!session.isNew) {
                val key = (session.target as EditorTarget.Existing).key
                Text(
                    "${longDate(session.date)} · ${timeLabel(key)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onCancel) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { session.saveAndClose() },
            enabled = !session.saving && !session.loading,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 22.dp),
        ) {
            Text("Save", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BodyField(session: EditorSession, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(session, session.loading) { if (!session.loading) runCatching { focus.requestFocus() } }
    val style = MaterialTheme.typography.bodyLarge.reading()
    BasicTextField(
        value = session.text,
        onValueChange = session::onTextChange,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .testTag("editor-text")
            .padding(vertical = 18.dp),
        decorationBox = { inner ->
            Box {
                if (session.text.text.isEmpty()) {
                    Text("Start writing…", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

/** Today / Yesterday / another date, and the time: the selected chip is tinted blue. */
@Composable
private fun WhenChips(
    date: LocalDate,
    time: LocalTime?,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val today = LocalDate.now()
    val other = date != today && date != today.minusDays(1)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Today", selected = date == today) { onDate(today) }
        Chip("Yesterday", selected = date == today.minusDays(1)) { onDate(today.minusDays(1)) }
        Chip(if (other) dayAndMonth(date) else null, selected = other, icon = Icons.Outlined.CalendarMonth, iconLabel = "Pick a date", onClick = onPickDate)
        if (time != null) Chip(time.toString(), selected = false, icon = Icons.Outlined.Schedule, iconLabel = "Change time", onClick = onPickTime)
    }
}

/** A pill like Vink's date chips: tinted when [selected], outlined otherwise. */
@Composable
private fun Chip(
    label: String?,
    selected: Boolean,
    icon: ImageVector? = null,
    iconLabel: String? = null,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(19.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (selected) accent else LocalJournalColors.current.body,
        border = if (selected) null else BorderStroke(1.dp, LocalJournalColors.current.line),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Row(
            Modifier.padding(horizontal = if (label == null) 11.dp else 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = if (label == null) iconLabel else null, modifier = Modifier.size(17.dp))
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Photos, the mood faces (clicking the chosen one again clears it), the day's star and tags. */
@Composable
private fun Toolbar(session: EditorSession, onAddPhotos: () -> Unit, onTags: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = LocalJournalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAddPhotos, enabled = !session.addingPhotos && !session.loading) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add photos", tint = muted)
        }
        IconButton(onClick = { session.onStarredChange(!session.starred) }) {
            if (session.starred) {
                Icon(Icons.Rounded.Star, contentDescription = "Unstar day", tint = colors.amber)
            } else {
                Icon(Icons.Rounded.StarBorder, contentDescription = "Star day", tint = muted)
            }
        }
        IconButton(onClick = onTags) {
            Icon(Icons.Outlined.Sell, contentDescription = "Day tags", tint = if (session.tags.isEmpty()) muted else MaterialTheme.colorScheme.primary)
        }
        if (session.tags.isNotEmpty()) {
            Text(
                session.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        // Location and weather are kept as they are.
        val info = session.location?.let { Icons.Outlined.Place to it.substringBefore('(').trim() }
            ?: session.weather?.let { weatherIcon(it) to it.substringBefore(',').trim() }
        info?.let { (icon, text) -> MetaChip(icon, text, Modifier.padding(end = 12.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (m in 1..5) {
                val on = session.mood == m
                Surface(
                    onClick = { session.onMoodChange(if (on) null else m) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (on) colors.amberSoft else Color.Transparent,
                    contentColor = if (on) colors.amber else muted,
                    border = if (on) BorderStroke(1.5.dp, colors.amber) else null,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { selected = on },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(moodIcon(m), contentDescription = "Mood: ${moodName(m)}", modifier = Modifier.size(22.dp))
                    }
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
    var text by remember { mutableStateOf(tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Saved in the note's frontmatter. #tags you type in the text work too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("journal, family") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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

