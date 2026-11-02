package nl.markbroeders.journal.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import nl.markbroeders.journal.data.TimelineEntry
import nl.markbroeders.journal.ui.components.EntryRow
import nl.markbroeders.journal.ui.components.ListHeader
import nl.markbroeders.journal.ui.components.LocalEntryPhotos
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.components.monthYear
import nl.markbroeders.journal.ui.components.NewEntryButton
import nl.markbroeders.journal.ui.components.ScreenHeader
import nl.markbroeders.journal.ui.theme.JournalType
import nl.markbroeders.journal.ui.theme.LocalJournalColors
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
            ScreenHeader("Calendar", Icons.Outlined.CalendarMonth, LocalJournalColors.current.pink) {
                Surface(
                    onClick = { viewModel.select(LocalDate.now()) },
                    shape = RoundedCornerShape(16.dp),
                    color = LocalJournalColors.current.chip,
                    contentColor = LocalJournalColors.current.body,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                ) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = { NewEntryButton({ onNewEntry(selected) }, "New entry on this day") },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            // Room for the floating + button.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "month") {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    val month = monthOf(pager.currentPage)
                    val monthMarks by remember(month) { viewModel.marks(month) }.collectAsStateWithLifecycle(emptyMap())
                    MonthNavigation(
                        month,
                        written = monthMarks.size,
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
            }
            item(key = "day-header") { ListHeader(longDate(selected, today)) }
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
                            dayHeader = false,
                        ) { onOpenEntry(entry) }
                    }
                }
            }
        }
    }
}

/** `October 2026` and how many days have entries, with arrows on the right. */
@Composable
private fun MonthNavigation(month: YearMonth, written: Int, onPrevious: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(monthYear(month.atDay(1)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                when (written) {
                    0 -> "Nothing written"
                    1 -> "1 day written"
                    else -> "$written days written"
                },
                style = JournalType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                style = JournalType.label,
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
                            .height(52.dp),
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
 * A day number: bold with a blue dot below when the day has entries, or in the day's first photo
 * (dimmed behind the number). Today is blue with a blue ring, the selected day has a dark ring.
 */
@Composable
private fun DayCell(date: LocalDate, mark: DayMark?, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val photo = if (mark != null) LocalEntryPhotos.current.forDay(date) else null
    val ring = when {
        today -> colors.primary
        selected -> colors.onSurface
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(4.dp) else Modifier.padding(2.dp))
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            Text(
                "${date.dayOfMonth}",
                style = if (photo != null) {
                    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f))
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                },
                fontWeight = if (mark != null || today) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    photo != null -> Color.White
                    today -> colors.primary
                    else -> colors.onSurface
                },
            )
        }
        // The dot keeps its place on every day, so numbers line up.
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(if (mark != null && photo == null) colors.primary else Color.Transparent),
        )
    }
}
