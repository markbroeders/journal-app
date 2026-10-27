package nl.markbroeders.journal.ui.onthisday

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
import nl.markbroeders.journal.data.OnThisDay
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.toTimelineDay
import java.time.LocalDate

/** The days matching [date] in earlier years, newest first. */
data class OnThisDayResult(val date: LocalDate, val days: List<TimelineDay>)

/** Entries written on the same day in earlier years, for the timeline banner and the screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnThisDayViewModel(container: AppContainer) : ViewModel() {

    private val dao = container.index.dao()

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** Matches for [date]; null (or another date) while loading. */
    val result: StateFlow<OnThisDayResult?> = _date
        .flatMapLatest { d -> matches(d).map { OnThisDayResult(d, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shows [date] on the screen. */
    fun show(date: LocalDate) {
        _date.value = date
    }

    fun matches(date: LocalDate): Flow<List<TimelineDay>> =
        dao.observeOnThisDay(OnThisDay.monthDays(date), before = date.withDayOfYear(1))
            .map { rows -> rows.map { it.toTimelineDay() } }
}
