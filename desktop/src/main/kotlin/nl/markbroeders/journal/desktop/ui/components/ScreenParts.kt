package nl.markbroeders.journal.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.desktop.ui.theme.JournalType
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors

/**
 * A tab's header, like Vink's lists: a big bold title on the page with the section's [icon] in
 * its colour beside it, an optional [subtitle] below and [actions] on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    icon: ImageVector,
    tint: Color,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 3.dp).size(26.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(title, style = JournalType.title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** The round + button for a new entry. */
@Composable
fun NewEntryButton(onClick: () -> Unit, contentDescription: String = "New entry") {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = contentDescription)
    }
}

/** A small chip with an icon: an entry's time, mood, place or photos. */
@Composable
fun MetaChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    background: Color = LocalJournalColors.current.chip,
    content: Color = LocalJournalColors.current.body,
    iconTint: Color = content,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
        Text(text, style = JournalType.meta, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A `#tag` as an outlined pill. */
@Composable
fun TagChip(tag: String, onClick: () -> Unit) {
    val colors = LocalJournalColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = colors.body,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.tagBorder),
    ) {
        Text("#$tag", style = JournalType.meta, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
