package nl.markbroeders.journal.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.TooltipArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.desktop.Platform
import nl.markbroeders.journal.desktop.data.IndexState
import nl.markbroeders.journal.desktop.ui.components.MarkdownView
import nl.markbroeders.journal.desktop.ui.components.MetaChip
import nl.markbroeders.journal.desktop.ui.components.TagChip
import nl.markbroeders.journal.desktop.ui.components.longDate
import nl.markbroeders.journal.desktop.ui.components.moodIcon
import nl.markbroeders.journal.desktop.ui.components.moodName
import nl.markbroeders.journal.desktop.ui.components.timeLabel
import nl.markbroeders.journal.desktop.ui.components.weatherIcon
import nl.markbroeders.journal.desktop.ui.editor.TextColumnWidth
import nl.markbroeders.journal.desktop.ui.theme.JournalType
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors
import java.time.LocalDate

/** One entry, formatted, with Edit, Delete and Open in Obsidian. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryPane(controller: AppController, state: IndexState, date: LocalDate, key: EntryKey) {
    val day = state.day(date)
    val entry = state.entry(date, key)
    if (day == null || entry == null) {
        if (state.loading) return
        Placeholder("This entry is no longer in the note. It may have been changed or removed in Obsidian.")
        return
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = LocalJournalColors.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                longDate(entry.date),
                style = JournalType.title,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (day.starred) {
                Icon(Icons.Rounded.Star, contentDescription = "Starred day", tint = colors.amber, modifier = Modifier.padding(end = 8.dp).size(22.dp))
            }
            Action(Icons.AutoMirrored.Outlined.OpenInNew, "Open the note in Obsidian") { controller.openInObsidian(date) }
            Action(Icons.Outlined.Delete, "Delete entry") { confirmDelete = true }
            Action(Icons.Outlined.Edit, "Edit (Ctrl+E)") { controller.edit(date, key) }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = TextColumnWidth).fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip(Icons.Outlined.Schedule, timeLabel(entry.key))
                    (entry.mood ?: day.mood)?.let {
                        MetaChip(moodIcon(it), moodName(it), background = colors.amberSoft, content = colors.amberText, iconTint = colors.amber)
                    }
                    entry.location?.let { MetaChip(Icons.Outlined.Place, it) }
                    entry.weather?.let { MetaChip(weatherIcon(it), it) }
                    if (entry.mediaCount > 0) {
                        MetaChip(Icons.Outlined.PhotoCamera, "${entry.mediaCount} ${if (entry.mediaCount == 1) "photo" else "photos"}")
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.line)
                Spacer(Modifier.height(20.dp))
                if (entry.body.isNotBlank()) {
                    SelectionContainer {
                        MarkdownView(
                            entry.body,
                            resolveMedia = state::resolve,
                            onOpenPhoto = { Platform.open(it.path) },
                            onOpenDate = controller::openDate,
                            onTag = controller::searchTag,
                        )
                    }
                } else {
                    Text("No text.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val tags = (day.tags + entry.tags).distinctBy { it.lowercase() }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag -> TagChip(tag) { controller.searchTag(tag) } }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("Only this entry is removed from the day's note. The rest of the note stays as it is.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    controller.delete(date, key, entry.raw)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** What the pane shows with nothing selected. */
@Composable
fun EmptyPane(controller: AppController) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Pick an entry on the left, or write a new one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.newEntry() }) { Text("New entry") }
                OutlinedButton(onClick = { controller.newEntry(LocalDate.now().minusDays(1)) }) { Text("For yesterday") }
            }
            Text(
                "Ctrl+N new entry · Ctrl+S save · Esc cancel · Ctrl+F search · Ctrl+E edit",
                style = JournalType.meta,
                color = LocalJournalColors.current.faint,
            )
        }
    }
}

@Composable
fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipArea(tooltip = { Tooltip(label) }) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Tooltip(text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
