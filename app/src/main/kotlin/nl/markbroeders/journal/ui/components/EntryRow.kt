package nl.markbroeders.journal.ui.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.media.MediaEntry
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import java.time.LocalDate

private val CardRadius = 16.dp
private val CardMargin = 12.dp

/**
 * One entry as part of its day's card: entries of a day are separate list items that together
 * draw one rounded card ([firstOfDay] rounds the top, [lastOfDay] the bottom and leaves a gap).
 * With [dayHeader] the first entry carries the day's date, mood and star; lists that already
 * group by day under their own headers leave it off. [preview] replaces the entry's own preview,
 * e.g. with a search snippet.
 */
@Composable
fun EntryRow(
    day: TimelineDay,
    entry: TimelineEntry,
    firstOfDay: Boolean,
    lastOfDay: Boolean,
    dayHeader: Boolean = true,
    preview: AnnotatedString? = null,
    onClick: () -> Unit,
) {
    val top = if (firstOfDay) CardRadius else 0.dp
    val bottom = if (lastOfDay) CardRadius else 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = CardMargin, end = CardMargin, bottom = if (lastOfDay) 12.dp else 0.dp)
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(LocalJournalColors.current.card),
    ) {
        if (firstOfDay && dayHeader) DayHeader(day)
        if (!firstOfDay) {
            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        EntryContent(entry, preview, onClick)
    }
}

@Composable
private fun DayHeader(day: TimelineDay) {
    val today = day.date == LocalDate.now()
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            weekdayLong(day.date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        day.mood?.let { Text(moodEmoji(it), style = MaterialTheme.typography.titleMedium) }
        if (day.starred) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.Star,
                contentDescription = "Starred day",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EntryContent(entry: TimelineEntry, preview: AnnotatedString?, onClick: () -> Unit) {
    val photo = LocalEntryPhotos.current.forEntry(entry.date, entry.key)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (entry.hasTitle) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val previewText = preview ?: AnnotatedString(entry.preview)
                if (previewText.isNotEmpty()) {
                    Text(
                        previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                // No title: the text as it runs, from its first line.
                val text = preview ?: AnnotatedString(listOf(entry.title, entry.preview).filter { it.isNotEmpty() }.joinToString(" "))
                if (text.isNotEmpty()) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
private fun Thumbnail(photo: MediaEntry, more: Int) {
    Box(
        Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
                style = MaterialTheme.typography.labelSmall,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(timeLabel(entry.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        entry.mood?.let {
            Text(moodEmoji(it), style = MaterialTheme.typography.labelMedium)
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
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
