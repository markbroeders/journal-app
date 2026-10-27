package nl.markbroeders.journal.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.SearchHit
import nl.markbroeders.journal.data.SearchQuery
import nl.markbroeders.journal.data.SearchSql
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.toTimelineEntry

/** One day's matching entries, with each entry's highlighted snippet (null for a tag-only search). */
data class SearchDay(val day: TimelineDay, val snippets: List<String?>)

data class SearchResults(val query: SearchQuery, val days: List<SearchDay>, val count: Int, val limited: Boolean)

/** A tag with the number of entries and days it's on, for the suggestions. */
data class TagCount(val tag: String, val count: Int)

/** Full-text and tag search over the index; results follow the index as it changes. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(container: AppContainer) : ViewModel() {

    private val dao = container.index.dao()

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    /** Results for the current text; null while the text is empty or the first results load. */
    val results: StateFlow<SearchResults?> = _text
        .debounce { if (it.isBlank()) 0 else 250 }
        .map(SearchQuery::parse)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isEmpty) {
                flowOf(null)
            } else {
                dao.search(SearchSql.build(query, LIMIT + 1)).map { hits -> results(query, hits) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** All tags, most used first, spelled as they first occur. */
    val tags: StateFlow<List<TagCount>> = dao.observeTagLists()
        .map { lists ->
            lists.flatMap { it.split('\n') }
                .groupBy { it.lowercase() }
                .map { (_, spellings) -> TagCount(spellings.first(), spellings.size) }
                .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.lowercase() })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setText(text: String) {
        _text.value = text
    }

    /** Adds `#tag` to the search, unless it's already there. */
    fun addTag(tag: String) {
        val current = _text.value
        if (SearchQuery.parse(current).tags.any { it.equals(tag, ignoreCase = true) }) return
        _text.value = (current.trimEnd() + " #$tag ").trimStart()
    }

    private fun results(query: SearchQuery, hits: List<SearchHit>): SearchResults {
        val shown = hits.take(LIMIT)
        // Hits are ordered by date and file, so a day's entries are consecutive.
        val days = mutableListOf<SearchDay>()
        var i = 0
        while (i < shown.size) {
            val first = shown[i]
            var end = i
            while (end < shown.size && shown[end].entry.documentId == first.entry.documentId) end++
            val group = shown.subList(i, end)
            val day = TimelineDay(
                date = first.entry.date,
                starred = first.dayStarred,
                mood = first.dayMood,
                tags = first.dayTags,
                entries = group.map { it.entry.toTimelineEntry() },
            )
            days += SearchDay(day, group.map { it.snippet })
            i = end
        }
        return SearchResults(query, days, shown.size, limited = hits.size > LIMIT)
    }

    private companion object {
        const val LIMIT = 300
    }
}
