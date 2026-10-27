package nl.markbroeders.journal.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.EntryRow
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.components.monthYear
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

// Months the pager can show: far enough back for any journal, a year ahead for planned entries.
private val FirstMonth = YearMonth.of(1900, 1)

private fun pageOf(month: YearMonth): Int = ChronoUnit.MONTHS.between(FirstMonth, month).toInt()

private fun monthOf(page: Int): YearMonth = FirstMonth.plusMonths(page.toLong())

/** Month grid with markers on days that have entries; the selected day's entries below it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenEntry: (TimelineEntry) -> Unit,
    onNewEntry: (LocalDate) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val pager = rememberPagerState(initialPage = pageOf(YearMonth.from(selected))) {
        pageOf(YearMonth.from(today).plusYears(1)) + 1
    }
    val scope = rememberCoroutineScope()
    // Follow the selection to its month (e.g. after "Today"); swiping alone keeps the selection.
    LaunchedEffect(selected) {
        val page = pageOf(YearMonth.from(selected))
        if (pager.currentPage != page) pager.animateScrollToPage(page)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                actions = {
                    IconButton(onClick = { viewModel.select(LocalDate.now()) }) {
                        Icon(Icons.Outlined.Today, contentDescription = "Today")
                    }
                },
                colors = journalTopBarColors(),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNewEntry(selected) },
                shape = CircleShape,
                containerColor = LocalJournalColors.current.header,
                contentColor = LocalJournalColors.current.onHeader,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New entry on this day")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            // Room for the floating + button.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "month") {
                val month = monthOf(pager.currentPage)
                MonthNavigation(
                    month,
                    onPrevious = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    canGoNext = pager.currentPage < pager.pageCount - 1,
                )
                WeekdayLabels()
                HorizontalPager(pager, beyondViewportPageCount = 1, key = { it }) { page ->
                    val pageMonth = monthOf(page)
                    val marks by remember(pageMonth) { viewModel.marks(pageMonth) }
                        .collectAsStateWithLifecycle(emptyMap())
                    MonthGrid(pageMonth, marks, selected, today, onSelect = viewModel::select)
                }
            }
            item(key = "day-header") {
                Text(
                    longDate(selected, today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // Nothing until the selected day has loaded, so switching days doesn't flash "no entries".
            val current = selectedDay?.takeIf { it.date == selected }
            val day = current?.day
            when {
                current == null -> {}
                day == null -> item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "No entries on this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onNewEntry(selected) }) { Text("Write about this day") }
                    }
                }
                else -> day.entries.forEachIndexed { i, entry ->
                    item(key = "entry-${entry.date}-${entry.key.time}-${entry.key.ordinal}") {
                        EntryRow(
                            day, entry,
                            firstOfDay = i == 0,
                            lastOfDay = i == day.entries.lastIndex,
                            dateColumn = false,
                        ) { onOpenEntry(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigation(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            monthYear(month.atDay(1)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

private fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

@Composable
private fun WeekdayLabels() {
    val first = firstDayOfWeek()
    Row(Modifier.padding(horizontal = 8.dp)) {
        for (i in 0L until 7L) {
            Text(
                first.plus(i).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** Always six rows, so the height doesn't jump between months. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    marks: Map<LocalDate, DayMark>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDayOfWeek().value + 7) % 7
    Column(Modifier.padding(horizontal = 8.dp)) {
        for (week in 0 until 6) {
            Row {
                for (col in 0 until 7) {
                    val dayOfMonth = week * 7 + col - offset + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayOfMonth)
                            DayCell(date, marks[date], date == selected, date == today) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A day number in a circle: light blue when the day has entries, solid header blue when
 * selected, and a blue ring for today.
 */
@Composable
private fun DayCell(date: LocalDate, mark: DayMark?, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val header = LocalJournalColors.current
    val (background, content) = when {
        selected -> header.header to header.onHeader
        mark != null -> colors.primaryContainer to colors.onPrimaryContainer
        today -> colors.surface to colors.primary
        else -> colors.surface to colors.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(background)
                .then(if (today && !selected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (mark != null || today || selected) FontWeight.SemiBold else FontWeight.Normal,
                color = content,
            )
        }
    }
}
