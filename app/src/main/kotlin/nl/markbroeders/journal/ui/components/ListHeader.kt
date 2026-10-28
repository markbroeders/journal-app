package nl.markbroeders.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.markbroeders.journal.ui.theme.LocalJournalColors

/** A heading between day cards (a month, a date, a year), on the list's ground so it can stick. */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(LocalJournalColors.current.ground)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
    )
}
