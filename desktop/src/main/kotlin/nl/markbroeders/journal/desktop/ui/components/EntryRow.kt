package nl.markbroeders.journal.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.desktop.data.MediaFile
import nl.markbroeders.journal.desktop.ui.theme.JournalType
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors
import nl.markbroeders.journal.desktop.ui.theme.reading
import java.time.LocalDate

private val DateColumnWidth = 44.dp

/**
 * One entry in a list, on the page with hairlines between days (no cards). Entries of a day are
 * separate list items: [firstOfDay] and [lastOfDay] place the spacing and the line after the day.
 * With [dayHeader] (the timeline) the first entry carries the day's number and weekday in a column
 * on the left, with the day's mood and star; lists that already group by day under their own
 * headers leave it off. [preview] replaces the entry's own preview, e.g. with a search snippet.
 */
@Composable
fun EntryRow(
    day: TimelineDay,
    entry: TimelineEntry,
    firstOfDay: Boolean,
    lastOfDay: Boolean,
    dayHeader: Boolean = true,
    preview: AnnotatedString? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val line = LocalJournalColors.current.line
    Column(Modifier.fillMaxWidth()) {
        if (!dayHeader && !firstOfDay) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = line)
        Row(
            Modifier.padding(
                start = if (dayHeader) 20.dp else 12.dp,
                end = 12.dp,
                top = if (firstOfDay) 8.dp else 0.dp,
                bottom = if (lastOfDay) 8.dp else 0.dp,
            ),
        ) {
            if (dayHeader) {
                Box(Modifier.width(DateColumnWidth).padding(top = 8.dp)) {
                    if (firstOfDay) DateColumn(day)
                }
                Spacer(Modifier.width(6.dp))
            }
            EntryContent(entry, preview, selected, onClick, Modifier.weight(1f))
        }
        if (dayHeader && lastOfDay) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = line)
    }
}

/** `28` over `TODAY` (in blue) or `TUE`, with the day's mood and star below. */
@Composable
private fun DateColumn(day: TimelineDay) {
    val today = day.date == LocalDate.now()
    val accent = MaterialTheme.colorScheme.primary
    val amber = LocalJournalColors.current.amber
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${day.date.dayOfMonth}",
            style = JournalType.title.copy(lineHeight = 30.sp),
            color = if (today) accent else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            weekdayShort(day.date),
            style = JournalType.label,
            color = if (today) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (day.mood != null || day.starred) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                day.mood?.let {
                    Icon(moodIcon(it), contentDescription = "Day mood: ${moodName(it)}", tint = amber, modifier = Modifier.size(16.dp))
                }
                if (day.starred) {
                    Icon(Icons.Rounded.Star, contentDescription = "Starred day", tint = amber, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun EntryContent(entry: TimelineEntry, preview: AnnotatedString?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val photo = LocalMedia.current.firstMedia(entry)
    val text = JournalType.preview.reading()
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (entry.hasTitle) {
                Text(
                    entry.title,
                    style = text.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val previewText = preview ?: AnnotatedString(entry.preview)
                if (previewText.isNotEmpty()) {
                    Text(
                        previewText,
                        style = text,
                        color = LocalJournalColors.current.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                // No title: the text as it runs, from its first line.
                val running = preview ?: AnnotatedString(listOf(entry.title, entry.preview).filter { it.isNotEmpty() }.joinToString(" "))
                if (running.isNotEmpty()) {
                    Text(running, style = text, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            MetaLine(entry, showMediaCount = photo == null)
        }
        if (photo != null) {
            Spacer(Modifier.width(12.dp))
            Thumbnail(photo, more = entry.mediaCount - 1)
        }
    }
}

@Composable
private fun Thumbnail(photo: MediaFile, more: Int) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (!photo.isVideo) {
            AsyncImage(
                model = photo.path.toFile(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (photo.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(2.dp),
            )
        }
        if (more > 0) {
            Text(
                "+$more",
                style = JournalType.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MetaLine(entry: TimelineEntry, showMediaCount: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(timeLabel(entry.key), style = JournalType.meta, color = muted)
        entry.mood?.let {
            Icon(moodIcon(it), contentDescription = "Mood: ${moodName(it)}", tint = LocalJournalColors.current.amber, modifier = Modifier.size(15.dp))
        }
        entry.tags.firstOrNull()?.let {
            Text(
                "#$it",
                style = JournalType.meta.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (showMediaCount && entry.mediaCount > 0) MetaItem(Icons.Outlined.PhotoCamera, "${entry.mediaCount}")
        entry.weather?.let { MetaItem(weatherIcon(it), it.substringBefore(',').trim()) }
        entry.location?.let {
            MetaItem(Icons.Outlined.Place, it.substringBefore('(').trim(), Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = JournalType.meta,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
