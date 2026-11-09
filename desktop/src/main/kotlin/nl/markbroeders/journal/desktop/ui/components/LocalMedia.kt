package nl.markbroeders.journal.desktop.ui.components

import androidx.compose.runtime.compositionLocalOf
import nl.markbroeders.journal.desktop.data.IndexState

/** The current index, for resolving photo embeds to files (thumbnails, the entry view). */
val LocalMedia = compositionLocalOf { IndexState() }
