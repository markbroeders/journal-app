package nl.markbroeders.journal.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.toTimelineDay
import java.time.LocalDate
import java.time.YearMonth

/** What a calendar cell shows for a day with entries. */
data class DayMark(val entries: Int, val starred: Boolean)

/** The entries of [date]; [day] is null when it has none. */
data class SelectedDay(val date: LocalDate, val day: TimelineDay?)

/** Month grid over the index, plus the entries of the selected day. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(container: AppContainer) : ViewModel() {

    private val dao = container.index.dao()

    private val _selected = MutableStateFlow(LocalDate.now())
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    /** The selected day's entries; null (or another date) while loading. */
    val selectedDay: StateFlow<SelectedDay?> = _selected
        .flatMapLatest { date ->
            dao.observeDay(date).map { rows -> SelectedDay(date, rows.firstOrNull()?.toTimelineDay()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(date: LocalDate) {
        _selected.value = date
    }

    /** Days of [month] that have entries. */
    fun marks(month: YearMonth): Flow<Map<LocalDate, DayMark>> =
        dao.observeMarkedDays(month.atDay(1), month.atEndOfMonth())
            .map { rows -> rows.associate { it.date to DayMark(it.entries, it.starred) } }
}
