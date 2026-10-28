package nl.markbroeders.journal.ui.onthisday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markbroeders.journal.data.OnThisDay
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.EntryRow
import nl.markbroeders.journal.ui.components.ListHeader
import nl.markbroeders.journal.ui.components.dayAndMonth
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.time.LocalDate

/** Entries from the same day in earlier years, grouped by year, with arrows to step through days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnThisDayScreen(
    viewModel: OnThisDayViewModel,
    onOpenEntry: (TimelineEntry) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val date by viewModel.date.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("On This Day") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (date != today) {
                        IconButton(onClick = { viewModel.show(today) }) {
                            Icon(Icons.Outlined.Today, contentDescription = "Today")
                        }
                    }
                },
                colors = journalTopBarColors(),
            )
        },
        containerColor = LocalJournalColors.current.ground,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "date") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.show(date.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Text(
                        dayAndMonth(date),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.show(date.plusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }
            // Nothing until this date has loaded, so stepping through days doesn't flash "nothing".
            val days = result?.takeIf { it.date == date }?.days
            when {
                days == null -> {}
                days.isEmpty() -> item(key = "empty") {
                    Text(
                        "Nothing written on ${dayAndMonth(date)} in earlier years.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
                else -> for (day in days) {
                    item(key = "year-${day.date}") {
                        ListHeader("${day.date.year} · ${OnThisDay.yearsAgo(day.date, date)}")
                    }
                    day.entries.forEachIndexed { i, entry ->
                        item(key = "entry-${entry.date}-${entry.key.time}-${entry.key.ordinal}") {
                            EntryRow(
                                day, entry,
                                firstOfDay = i == 0,
                                lastOfDay = i == day.entries.lastIndex,
                                dayHeader = false,
                            ) { onOpenEntry(entry) }
                        }
                    }
                }
            }
        }
    }
}
