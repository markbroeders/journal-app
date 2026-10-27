package nl.markbroeders.journal.data

import java.time.LocalDate

/** What the index knows about a note file, to tell whether it changed on disk. */
data class IndexStamp(val lastModified: Long, val size: Long, val readFailed: Boolean = false)

/** A daily note file as listed on disk, identified by its document id. */
data class ListedNote(val id: String, val date: LocalDate, val lastModified: Long, val size: Long)

/** The work for one rescan: files to (re-)read, newest first, and index rows to remove. */
data class IndexPlan(val toRead: List<ListedNote>, val toRemove: Set<String>) {
    companion object {
        /**
         * Compares the [indexed] stamps (by document id) with the files [onDisk]. A file is read
         * again when it is new, its last-modified time or size changed, or it couldn't be read
         * last time. Rows for files that are gone are removed.
         */
        fun compute(indexed: Map<String, IndexStamp>, onDisk: List<ListedNote>): IndexPlan {
            val toRead = onDisk
                .filter { file ->
                    val stamp = indexed[file.id]
                    stamp == null || stamp.readFailed ||
                        stamp.lastModified != file.lastModified || stamp.size != file.size
                }
                .sortedByDescending { it.date }
            val ids = onDisk.mapTo(HashSet()) { it.id }
            return IndexPlan(toRead, indexed.keys - ids)
        }
    }
}
