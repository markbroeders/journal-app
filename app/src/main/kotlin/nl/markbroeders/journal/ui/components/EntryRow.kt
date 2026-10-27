package nl.markbroeders.journal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import java.time.LocalDate

private val DateColumnWidth = 44.dp

/**
 * Day One-style row: date column (first entry of the day only), bold title, grey preview, meta
 * line. Without [dateColumn] (a list of one day's entries) the row starts at the title.
 */
@Composable
fun EntryRow(
    day: TimelineDay,
    entry: TimelineEntry,
    firstOfDay: Boolean,
    lastOfDay: Boolean,
    dateColumn: Boolean = true,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        if (dateColumn) {
            Column(Modifier.width(DateColumnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                if (firstOfDay) {
                    Text(
                        weekdayShort(entry.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.date == today) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${entry.date.dayOfMonth}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (entry.date == today) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (day.starred || day.mood != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            day.mood?.let { Text(moodEmoji(it), style = MaterialTheme.typography.labelMedium) }
                            if (day.starred) {
                                Icon(
                                    Icons.Rounded.Star,
                                    contentDescription = "Starred day",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (entry.title.isNotEmpty()) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.preview.isNotEmpty()) {
                Text(
                    entry.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MetaLine(entry)
        }
    }
    if (!lastOfDay) {
        val inset = if (dateColumn) 16.dp + DateColumnWidth + 16.dp else 16.dp
        HorizontalDivider(Modifier.padding(start = inset), color = MaterialTheme.colorScheme.outlineVariant)
    } else {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MetaLine(entry: TimelineEntry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(timeLabel(entry.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        entry.mood?.let {
            Text(moodEmoji(it), style = MaterialTheme.typography.labelMedium)
        }
        if (entry.mediaCount > 0) MetaItem(Icons.Outlined.PhotoCamera, "${entry.mediaCount}")
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
