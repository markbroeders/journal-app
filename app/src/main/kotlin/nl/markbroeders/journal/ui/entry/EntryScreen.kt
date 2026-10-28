package nl.markbroeders.journal.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import nl.markbroeders.journal.ui.media.MediaEntry
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.MarkdownView
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.components.moodEmoji
import nl.markbroeders.journal.ui.components.timeLabel
import nl.markbroeders.journal.ui.components.weatherIcon
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import nl.markbroeders.journal.ui.timeline.Deletion

/** One entry, read-only, with edit and delete actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    day: TimelineDay,
    entry: TimelineEntry,
    deletion: Deletion?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (deleteBlankFile: Boolean?) -> Unit,
    onDismissDeletion: () -> Unit,
    /** The entry's photos and videos that resolve to a file; placed where the text embeds them. */
    photos: List<MediaEntry> = emptyList(),
    onOpenPhoto: (MediaEntry) -> Unit = {},
    /** A link to a daily note was tapped. */
    onOpenDate: (LocalDate) -> Unit = {},
    /** A `#tag` was tapped (in the text or the tag chips). */
    onTag: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete entry") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    longDate(entry.date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (day.starred) {
                    Icon(Icons.Rounded.Star, contentDescription = "Starred day", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(timeLabel(entry.key), style = MaterialTheme.typography.headlineSmall)
                (entry.mood ?: day.mood)?.let { Text(moodEmoji(it), style = MaterialTheme.typography.headlineSmall) }
            }

            val details = buildList {
                entry.location?.let { add(Icons.Outlined.Place to it) }
                entry.weather?.let { add(weatherIcon(it) to it) }
                if (entry.mediaCount > 0) {
                    add(Icons.Outlined.PhotoCamera to "${entry.mediaCount} ${if (entry.mediaCount == 1) "photo" else "photos"}")
                }
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    details.forEach { (icon, text) -> Detail(icon, text) }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            if (entry.body.isNotBlank()) {
                val byName = remember(photos) { photos.associateBy { it.fileName.lowercase() } }
                val resolve = remember(byName) { { name: String -> byName[name.lowercase()] } }
                SelectionContainer {
                    MarkdownView(
                        entry.body,
                        resolveMedia = resolve,
                        onOpenPhoto = onOpenPhoto,
                        onOpenDate = onOpenDate,
                        onTag = onTag,
                    )
                }
            } else {
                Text(
                    "No text.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val tags = (day.tags + entry.tags).distinctBy { it.lowercase() }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag -> TagChip(tag) { onTag(tag) } }
                }
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
                    onDelete(null)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    when (deletion) {
        is Deletion.AskDeleteFile -> AlertDialog(
            onDismissRequest = onDismissDeletion,
            title = { Text("Delete the note file too?") },
            text = { Text("This was the last entry of ${entry.date}. The note file would be empty. Delete the file as well?") },
            confirmButton = { TextButton(onClick = { onDelete(true) }) { Text("Delete file") } },
            dismissButton = { TextButton(onClick = { onDelete(false) }) { Text("Keep empty file") } },
        )
        is Deletion.Failed -> AlertDialog(
            onDismissRequest = onDismissDeletion,
            title = { Text("Not deleted") },
            text = { Text(deletion.message) },
            confirmButton = { TextButton(onClick = onDismissDeletion) { Text("OK") } },
        )
        Deletion.InProgress, null -> {}
    }
}

@Composable
private fun Detail(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TagChip(tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text("#$tag", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}
