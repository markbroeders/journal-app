package nl.markbroeders.journal.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The window icon: an open book in white on the journal blue. */
val AppIcon: ImageVector = ImageVector.Builder("Journal", 64.dp, 64.dp, 24f, 24f)
    .addPath(addPathNodes("M5 1h14a4 4 0 0 1 4 4v14a4 4 0 0 1-4 4H5a4 4 0 0 1-4-4V5a4 4 0 0 1 4-4z"), fill = SolidColor(Color(0xFF1677B5)))
    .addPath(addPathNodes("M5.5 7c2.2-.8 4.5-.6 6 .6v10c-1.5-1-3.8-1.3-6-.6z"), fill = SolidColor(Color.White))
    .addPath(addPathNodes("M18.5 7c-2.2-.8-4.5-.6-6 .6v10c1.5-1 3.8-1.3 6-.6z"), fill = SolidColor(Color.White))
    .build()
