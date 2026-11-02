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
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.MarkdownView
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.components.MetaChip
import nl.markbroeders.journal.ui.components.TagChip
import nl.markbroeders.journal.ui.components.moodIcon
import nl.markbroeders.journal.ui.components.moodName
import nl.markbroeders.journal.ui.components.timeLabel
import nl.markbroeders.journal.ui.components.weatherIcon
import nl.markbroeders.journal.ui.theme.JournalType
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.util.Locale
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
            val colors = LocalJournalColors.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    longDate(entry.date).uppercase(Locale.getDefault()),
                    style = JournalType.label,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (day.starred) {
                    Icon(Icons.Rounded.Star, contentDescription = "Starred day", tint = colors.amber, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
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
            HorizontalDivider(color = LocalJournalColors.current.line)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
