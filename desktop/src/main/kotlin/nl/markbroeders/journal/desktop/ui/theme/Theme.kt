package nl.markbroeders.journal.desktop.ui.theme

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// One fixed journal blue instead of wallpaper-based dynamic colour, so the app has its own look.
// White pages with Vink's grey text ramp (ink, body, muted, faint); blue is only an accent.
private val Ink = Color(0xFF1D2026)
private val Blue = Color(0xFF1677B5)
private val BlueLight = Color(0xFF7CC8F2)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F1FA),
    onPrimaryContainer = Color(0xFF0B3550),
    secondary = Color(0xFF5F6672),
    secondaryContainer = Color(0xFFE3F1FA),
    onSecondaryContainer = Color(0xFF0B3550),
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF5F6672),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8F9),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFEBEDF0),
    surfaceContainerHighest = Color(0xFFE7E9EC),
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFFE3E5E8),
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1D3A4D),
    onPrimaryContainer = Color(0xFFD6ECFA),
    secondary = Color(0xFF9AA1AD),
    secondaryContainer = Color(0xFF1D3A4D),
    onSecondaryContainer = Color(0xFFD6ECFA),
    background = Color(0xFF16181D),
    onBackground = Color(0xFFE8EAEE),
    surface = Color(0xFF16181D),
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = Color(0xFF1D2026),
    onSurfaceVariant = Color(0xFF9AA1AD),
    surfaceContainerLowest = Color(0xFF111317),
    surfaceContainerLow = Color(0xFF1A1C21),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF262A31),
    surfaceContainerHighest = Color(0xFF2A2E36),
    outline = Color(0xFF7A818D),
    outlineVariant = Color(0xFF2E323A),
)

/**
 * Colours outside the Material scheme, after Vink: the text ramp ([body] between onSurface and
 * the muted onSurfaceVariant, [faint] for decoration only), [line] for hairlines, [ground] behind
 * grouped cards (settings), [chip] for small filled chips, and one colour per section: [amber]
 * (On This Day, stars, moods; [amberText] on [amberSoft]), [pink] (calendar), [teal], [violet].
 */
@Immutable
data class JournalColors(
    val body: Color,
    val faint: Color,
    val line: Color,
    val ground: Color,
    val card: Color,
    val chip: Color,
    val tagBorder: Color,
    val amber: Color,
    val amberText: Color,
    val amberSoft: Color,
    val pink: Color,
    val teal: Color,
    val violet: Color,
)

private val LightJournalColors = JournalColors(
    body = Color(0xFF4B5260),
    faint = Color(0xFF8A919C),
    line = Color(0xFFE3E5E8),
    ground = Color(0xFFF3F4F6),
    card = Color.White,
    chip = Color(0xFFF3F4F6),
    tagBorder = Color(0xFFC9CDD3),
    amber = Color(0xFFD97706),
    amberText = Color(0xFFB45309),
    amberSoft = Color(0xFFFFF7ED),
    pink = Color(0xFFDB2777),
    teal = Color(0xFF0D9488),
    violet = Color(0xFF7C3AED),
)

private val DarkJournalColors = JournalColors(
    body = Color(0xFFC3C8D0),
    faint = Color(0xFF7A818D),
    line = Color(0xFF2E323A),
    ground = Color(0xFF0F1114),
    card = Color(0xFF1D2026),
    chip = Color(0xFF262A31),
    tagBorder = Color(0xFF3A3F49),
    amber = Color(0xFFFBBF24),
    amberText = Color(0xFFFCD34D),
    amberSoft = Color(0xFF2A2114),
    pink = Color(0xFFF472B6),
    teal = Color(0xFF2DD4BF),
    violet = Color(0xFFA78BFA),
)

val LocalJournalColors = staticCompositionLocalOf { LightJournalColors }

/**
 * Literata for entry text: variable fonts (weight 400–700; optical size fixed at 12 and subset to
 * Latin to keep them small), one face per weight used.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Literata = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).flatMap { weight ->
        listOf(
            Font("literata.ttf", weight, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))),
            Font("literata_italic.ttf", weight, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))),
        )
    },
)

/** The font family for entry text (entry view and editor): Literata, or the UI font when off. */
val LocalReadingFont = staticCompositionLocalOf<FontFamily?> { null }

/** [this] style in the reading font. */
@Composable
fun TextStyle.reading(): TextStyle = LocalReadingFont.current?.let { copy(fontFamily = it) } ?: this

private val JournalTypography = Typography().let { base ->
    base.copy(
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 27.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    )
}

/** Text styles outside the Material scale, after Vink. */
object JournalType {
    /** Screen titles: Journal, Calendar. */
    val title = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)

    /** Section labels, shown in capitals: OCTOBER 2026, MOOD. */
    val label = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)

    /** Entry previews in lists (in the reading font). */
    val preview = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp)

    /** Small meta text: times, places, chips. */
    val meta = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun JournalTheme(dark: Boolean = false, serif: Boolean = true, content: @Composable () -> Unit) {
    val journalColors = if (dark) DarkJournalColors else LightJournalColors
    CompositionLocalProvider(
        LocalJournalColors provides journalColors,
        LocalReadingFont provides if (serif) Literata else null,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = JournalTypography,
            content = content,
        )
    }
}

/** Top bar colours, shared by all top bars: white like the page, muted icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun journalTopBarColors(): TopAppBarColors {
    val colors = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = colors.surface,
        scrolledContainerColor = colors.surface,
        titleContentColor = colors.onSurface,
        navigationIconContentColor = colors.onSurface,
        actionIconContentColor = colors.onSurfaceVariant,
    )
}
