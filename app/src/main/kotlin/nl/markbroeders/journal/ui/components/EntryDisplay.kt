package nl.markbroeders.journal.ui.components

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import nl.markbroeders.journal.data.EntryKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Picks an icon from a free-text weather description (`11 °C, Light rain`), English or Dutch. */
fun weatherIcon(weather: String): ImageVector {
    val w = weather.lowercase()
    return when {
        "thunder" in w || "onweer" in w -> Icons.Outlined.Thunderstorm
        "snow" in w || "sneeuw" in w || "hagel" in w -> Icons.Outlined.AcUnit
        "rain" in w || "drizzle" in w || "shower" in w || "regen" in w || "motregen" in w || "bui" in w ->
            Icons.Outlined.Umbrella
        "partly" in w || "half" in w -> Icons.Outlined.WbCloudy
        "cloud" in w || "overcast" in w || "fog" in w || "bewolkt" in w || "mist" in w -> Icons.Outlined.Cloud
        "sun" in w || "clear" in w || "zon" in w || "helder" in w -> Icons.Outlined.WbSunny
        else -> Icons.Outlined.Thermostat
    }
}

fun moodEmoji(mood: Int): String = when (mood) {
    1 -> "😞"
    2 -> "🙁"
    3 -> "😐"
    4 -> "🙂"
    else -> "😄"
}

/** `08:42`, or "Day note" for the untimed part of a note. */
fun timeLabel(key: EntryKey): String = key.time?.toString() ?: "Day note"

private fun locale(): Locale = Locale.getDefault()

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(locale()) }

/** `MON` / `MA` style weekday for the timeline's date column. */
fun weekdayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale())
    .trimEnd('.').uppercase(locale())

/** `October 2026` month header. */
fun monthYear(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale())).capitalized()

/** `27 October` / `October 27`: day and month in the locale's order, no year. */
fun dayAndMonth(date: LocalDate): String {
    val pattern = DateFormat.getBestDateTimePattern(locale(), "dMMMM")
    return date.format(DateTimeFormatter.ofPattern(pattern, locale()))
}

/** `Monday 26 October 2026`, with Today / Yesterday where they apply. */
fun longDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val full = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale())).capitalized()
    return when (date) {
        today -> "Today · $full"
        today.minusDays(1) -> "Yesterday · $full"
        else -> full
    }
}
