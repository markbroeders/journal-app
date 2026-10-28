package nl.markbroeders.journal.ui.components

import androidx.compose.runtime.compositionLocalOf
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.ui.media.MediaEntry
import java.time.LocalDate

/**
 * The first resolvable photo or video of each entry and of each day, for thumbnails in lists and
 * the calendar. [media] is in the media grid's order: within a day, in file order.
 */
class EntryPhotos(media: List<MediaEntry>) {
    private val byEntry = media.groupBy { it.date to it.key }.mapValues { (_, items) -> items.first() }
    private val byDay = media.groupBy { it.date }.mapValues { (_, items) -> items.first() }

    fun forEntry(date: LocalDate, key: EntryKey): MediaEntry? = byEntry[date to key]

    fun forDay(date: LocalDate): MediaEntry? = byDay[date]
}

val LocalEntryPhotos = compositionLocalOf { EntryPhotos(emptyList()) }
