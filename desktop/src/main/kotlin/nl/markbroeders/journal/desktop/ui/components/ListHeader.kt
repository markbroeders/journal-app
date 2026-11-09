package nl.markbroeders.journal.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.desktop.ui.theme.JournalType
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors
import java.util.Locale

/**
 * A section label in a list (a month, a date, a year) in small capitals over a hairline, on the
 * page's background so it can stick. [inset] is the space on both sides.
 */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier, inset: Dp = 20.dp) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text.uppercase(Locale.getDefault()),
            style = JournalType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = inset, end = inset, top = 20.dp, bottom = 6.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = inset), color = LocalJournalColors.current.line)
    }
}
