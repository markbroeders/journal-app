package nl.markbroeders.journal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// One fixed journal blue instead of wallpaper-based dynamic colour, so the app has its own look.
private val Blue = Color(0xFF1B7FC4)
private val BlueLight = Color(0xFF7CC6F2)
private val HeaderDark = Color(0xFF173A52)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECFA),
    onPrimaryContainer = Color(0xFF0B3550),
    secondary = Color(0xFF52606D),
    background = Color.White,
    onBackground = Color(0xFF1B1D20),
    surface = Color.White,
    onSurface = Color(0xFF1B1D20),
    surfaceVariant = Color(0xFFF2F4F6),
    onSurfaceVariant = Color(0xFF6A7178),
    surfaceContainer = Color(0xFFF4F6F8),
    surfaceContainerHigh = Color(0xFFEEF1F4),
    outline = Color(0xFF9AA1A8),
    outlineVariant = Color(0xFFE3E6EA),
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF07263A),
    primaryContainer = Color(0xFF1D4B6A),
    onPrimaryContainer = Color(0xFFD6ECFA),
    secondary = Color(0xFFAEB8C2),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF121416),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF1E2226),
    onSurfaceVariant = Color(0xFF9DA4AB),
    surfaceContainer = Color(0xFF1A1D20),
    surfaceContainerHigh = Color(0xFF22262A),
    outline = Color(0xFF6E757C),
    outlineVariant = Color(0xFF2C3136),
)

/** Colours outside the Material scheme: the blue header used on every screen. */
@Immutable
data class JournalColors(val header: Color, val onHeader: Color)

val LocalJournalColors = staticCompositionLocalOf { JournalColors(Blue, Color.White) }

private val JournalTypography = Typography().let { base ->
    base.copy(
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 27.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    )
}

@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val journalColors = if (dark) JournalColors(HeaderDark, Color.White) else JournalColors(Blue, Color.White)
    CompositionLocalProvider(LocalJournalColors provides journalColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = JournalTypography,
            content = content,
        )
    }
}

/** Blue header bar colours, shared by all top bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun journalTopBarColors(): TopAppBarColors {
    val c = LocalJournalColors.current
    return TopAppBarDefaults.topAppBarColors(
        containerColor = c.header,
        scrolledContainerColor = c.header,
        titleContentColor = c.onHeader,
        navigationIconContentColor = c.onHeader,
        actionIconContentColor = c.onHeader,
    )
}
