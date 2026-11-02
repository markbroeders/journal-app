package nl.markbroeders.journal.ui.components

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
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

/** A face for mood 1–5, drawn in one colour like the other icons (no emoji). */
fun moodIcon(mood: Int): ImageVector = when (mood) {
    1 -> Icons.Outlined.SentimentVeryDissatisfied
    2 -> Icons.Outlined.SentimentDissatisfied
    3 -> Icons.Outlined.SentimentNeutral
    4 -> Icons.Outlined.SentimentSatisfied
    else -> Icons.Outlined.SentimentVerySatisfied
}

fun moodName(mood: Int): String = when (mood) {
    1 -> "Awful"
    2 -> "Bad"
    3 -> "Okay"
    4 -> "Good"
    else -> "Great"
}

/** `08:42`, or "Day note" for the untimed part of a note. */
fun timeLabel(key: EntryKey): String = key.time?.toString() ?: "Day note"

private fun locale(): Locale = Locale.getDefault()

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(locale()) }

/** `WED` under a day's number in the timeline, or `TODAY`. */
fun weekdayShort(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (date == today) {
        "TODAY"
    } else {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale()).trimEnd('.').uppercase(locale())
    }

/** `October 2026` month header. */
fun monthYear(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale())).capitalized()

/** `27 October` / `October 27`: day and month in the locale's order, no year. */
fun dayAndMonth(date: LocalDate): String {
    val pattern = DateFormat.getBestDateTimePattern(locale(), "dMMMM")
    return date.format(DateTimeFormatter.ofPattern(pattern, locale()))
}

/** `Wednesday 28 October`: weekday, day and month in the locale's order, no year. */
fun dayAndMonthWithWeekday(date: LocalDate): String {
    val pattern = DateFormat.getBestDateTimePattern(locale(), "EEEEdMMMM")
    return date.format(DateTimeFormatter.ofPattern(pattern, locale())).capitalized()
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
