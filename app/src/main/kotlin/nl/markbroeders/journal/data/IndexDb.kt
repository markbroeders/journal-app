package nl.markbroeders.journal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * One daily note file. Keyed by document id rather than date: a misfiled copy
 * (`2025/2026-01-01.md`) is a separate file with the same date.
 */
@Entity(tableName = "notes", indices = [Index("date")])
data class NoteRow(
    @PrimaryKey val documentId: String,
    val date: LocalDate,
    val lastModified: Long,
    val size: Long,
    val starred: Boolean,
    val mood: Int?,
    /** Frontmatter tags; inline `#tags` are on the entries. */
    val tags: List<String>,
    val entryCount: Int,
    /** The file couldn't be read; it is retried on the next scan. */
    val readFailed: Boolean,
)

/** One entry of a note, in timeline order ([position] 0 is the newest). */
@Entity(
    tableName = "entries",
    primaryKeys = ["documentId", "position"],
    foreignKeys = [
        ForeignKey(NoteRow::class, ["documentId"], ["documentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("date")],
)
data class EntryRow(
    val documentId: String,
    val position: Int,
    val date: LocalDate,
    /** Null for the untimed entry. */
    val time: LocalTime?,
    val ordinal: Int,
    val title: String,
    /** Snippet for list previews. */
    val preview: String,
    val text: String,
    val location: String?,
    val weather: String?,
    val mood: Int?,
    val tags: List<String>,
    val mediaCount: Int,
    val raw: String,
)

/** Which vault and journal folder the index was built from. */
@Entity(tableName = "meta")
data class MetaRow(@PrimaryKey val key: String, val value: String)

data class NoteWithEntries(
    @Embedded val note: NoteRow,
    @Relation(parentColumn = "documentId", entityColumn = "documentId")
    val entries: List<EntryRow>,
)

@Dao
interface IndexDao {

    @Query("SELECT documentId, lastModified, size, readFailed FROM notes")
    suspend fun stamps(): List<StampRow>

    /** Notes with entries, newest first, with their entries. */
    @Transaction
    @Query("SELECT * FROM notes WHERE entryCount > 0 ORDER BY date DESC, documentId LIMIT :limit")
    fun observeDays(limit: Int): Flow<List<NoteWithEntries>>

    @Query("SELECT COUNT(*) FROM notes WHERE readFailed")
    fun observeUnreadable(): Flow<Int>

    @Upsert
    suspend fun upsertNotes(notes: List<NoteRow>)

    @Insert
    suspend fun insertEntries(entries: List<EntryRow>)

    @Query("DELETE FROM entries WHERE documentId IN (:ids)")
    suspend fun deleteEntries(ids: List<String>)

    @Query("DELETE FROM notes WHERE documentId IN (:ids)")
    suspend fun deleteNotes(ids: List<String>)

    @Query("UPDATE notes SET readFailed = 1 WHERE documentId IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    /** Replaces the rows of the given notes (and their entries) in one transaction. */
    @Transaction
    suspend fun replace(notes: List<NoteRow>, entries: List<EntryRow>) {
        notes.map { it.documentId }.chunked(MAX_ARGS).forEach { deleteEntries(it) }
        upsertNotes(notes)
        insertEntries(entries)
    }

    @Transaction
    suspend fun remove(ids: Collection<String>) {
        ids.chunked(MAX_ARGS).forEach { deleteNotes(it) }
    }

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Upsert
    suspend fun setMeta(row: MetaRow)

    /** Empties the index and records which vault it now belongs to. */
    @Transaction
    suspend fun reset(source: String) {
        clearNotes()
        setMeta(MetaRow(SOURCE_KEY, source))
    }

    companion object {
        const val SOURCE_KEY = "source"

        // Stays well under SQLite's limit on bound parameters.
        private const val MAX_ARGS = 500
    }
}

data class StampRow(val documentId: String, val lastModified: Long, val size: Long, val readFailed: Boolean)

class IndexConverters {
    @TypeConverter fun dateToString(date: LocalDate?): String? = date?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun timeToString(time: LocalTime?): String? = time?.toString()
    @TypeConverter fun stringToTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    // Tags never contain a newline.
    @TypeConverter fun tagsToString(tags: List<String>?): String? = tags?.joinToString("\n")
    @TypeConverter fun stringToTags(value: String?): List<String>? =
        value?.let { if (it.isEmpty()) emptyList() else it.split('\n') }
}

/**
 * Cache of the journal for fast startup and (later) calendar and search. The markdown files are
 * the source of truth: the index can be dropped and rebuilt by [VaultScanner] at any time, so a
 * schema change just recreates it.
 */
@Database(entities = [NoteRow::class, EntryRow::class, MetaRow::class], version = 1, exportSchema = false)
@TypeConverters(IndexConverters::class)
abstract class IndexDb : RoomDatabase() {
    abstract fun dao(): IndexDao

    companion object {
        fun create(context: Context): IndexDb =
            Room.databaseBuilder(context, IndexDb::class.java, "index.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

fun TimelineDay.toRows(documentId: String, lastModified: Long, size: Long): Pair<NoteRow, List<EntryRow>> {
    val note = NoteRow(documentId, date, lastModified, size, starred, mood, tags, entries.size, readFailed = false)
    val rows = entries.mapIndexed { i, e ->
        EntryRow(
            documentId, i, e.date, e.key.time, e.key.ordinal, e.title, e.preview, e.text,
            e.location, e.weather, e.mood, e.tags, e.mediaCount, e.raw,
        )
    }
    return note to rows
}

fun NoteWithEntries.toTimelineDay(): TimelineDay = TimelineDay(
    date = note.date,
    starred = note.starred,
    mood = note.mood,
    tags = note.tags,
    entries = entries.sortedBy { it.position }.map { e ->
        TimelineEntry(
            date = e.date,
            key = EntryKey(e.time, e.ordinal),
            title = e.title,
            preview = e.preview,
            text = e.text,
            location = e.location,
            weather = e.weather,
            mood = e.mood,
            tags = e.tags,
            mediaCount = e.mediaCount,
            raw = e.raw,
        )
    },
)
