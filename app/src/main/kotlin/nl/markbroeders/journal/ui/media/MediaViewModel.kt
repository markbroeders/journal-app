package nl.markbroeders.journal.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.Embeds
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.MediaItem
import java.time.LocalDate

/** A photo or video embedded in an entry, with the file it resolves to. */
data class MediaEntry(
    val date: LocalDate,
    val key: EntryKey,
    val fileName: String,
    val uri: String,
) {
    val isVideo: Boolean get() = Embeds.isVideo(fileName)
}

/** Every resolvable media embed across the journal, newest first; null while loading. */
class MediaViewModel(container: AppContainer) : ViewModel() {

    val media: StateFlow<List<MediaEntry>?> = container.index.dao().observeMedia()
        .map { items -> items.map { it.toEntry() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun MediaItem.toEntry() = MediaEntry(date, EntryKey(time, ordinal), fileName, uri)
}
