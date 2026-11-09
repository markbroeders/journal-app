package nl.markbroeders.journal.desktop.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The window's root: the theme plus a page-coloured [Surface], which also sets the default text
 * colour (onSurface). Without it text that doesn't set a colour is black, also in dark mode.
 */
@Composable
fun DesktopRoot(dark: Boolean = false, serif: Boolean = true, content: @Composable () -> Unit) {
    JournalTheme(dark = dark, serif = serif) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
