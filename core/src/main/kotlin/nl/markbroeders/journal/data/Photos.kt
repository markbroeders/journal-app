package nl.markbroeders.journal.data

import java.time.LocalDate
import java.time.LocalTime

/** Naming and embedding photos added from the app. */
object Photos {

    /**
     * The next free `YYYY-MM-DD-HHmm-N.jpg` for an entry at [date] [time], skipping names in
     * [taken] (compared case-insensitively, since Android storage and Obsidian links are).
     */
    fun nextName(date: LocalDate, time: LocalTime, taken: Set<String>): String {
        val lower = taken.mapTo(HashSet()) { it.lowercase() }
        val prefix = "%s-%02d%02d".format(date, time.hour, time.minute)
        return generateSequence(1) { it + 1 }
            .map { "$prefix-$it.jpg" }
            .first { it.lowercase() !in lower }
    }

    fun embed(name: String): String = "![[$name]]"

    /**
     * Inserts embeds for [names] at [cursor] in [text], each on its own line and the group as its
     * own paragraph. Returns the new text and where the cursor goes.
     */
    fun insertEmbeds(text: String, cursor: Int, names: List<String>): Pair<String, Int> {
        if (names.isEmpty()) return text to cursor
        val at = cursor.coerceIn(0, text.length)
        val before = text.substring(0, at)
        val after = text.substring(at)
        val lead = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val block = names.joinToString("\n") { embed(it) }
        val trail = when {
            after.startsWith("\n\n") -> ""
            after.startsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val result = before + lead + block + trail + after
        // At the end of the text, continue on a new paragraph below the photos; in the middle,
        // stay right after them.
        val newCursor = if (after.isEmpty()) result.length else before.length + lead.length + block.length
        return result to newCursor
    }
}
