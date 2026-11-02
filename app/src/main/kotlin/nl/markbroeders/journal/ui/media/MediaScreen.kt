package nl.markbroeders.journal.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import nl.markbroeders.journal.ui.components.monthYear
import nl.markbroeders.journal.ui.components.ListHeader
import nl.markbroeders.journal.ui.components.ScreenHeader
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import java.time.YearMonth

/** Photos and videos from all entries, newest first, by month. */
@Composable
fun MediaScreen(
    viewModel: MediaViewModel,
    onOpen: (MediaEntry) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { ScreenHeader("Media", Icons.Outlined.PhotoLibrary, LocalJournalColors.current.violet) },
        bottomBar = bottomBar,
    ) { padding ->
        val items = media
        when {
            items == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Text(
                "No photos yet. Photos embedded in entries (like ![[photo.jpg]]) show up here.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                var month: YearMonth? = null
                items.forEachIndexed { i, item ->
                    val itemMonth = YearMonth.from(item.date)
                    if (itemMonth != month) {
                        month = itemMonth
                        item(key = "month-$itemMonth", span = { GridItemSpan(maxLineSpan) }) {
                            ListHeader(monthYear(item.date), Modifier.padding(bottom = 8.dp), inset = 4.dp)
                        }
                    }
                    // The same file can be embedded twice; the position keeps keys unique.
                    item(key = "media-$i-${item.uri}") {
                        Thumbnail(item, Modifier.clickable { onOpen(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(item: MediaEntry, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(4.dp),
            )
        }
    }
}
