package nl.markbroeders.journal.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

/**
 * One entry of a note, in timeline order ([position] 0 is the newest). The explicit [id] is the
 * rowid that [EntryFts] refers to; an implicit rowid could be renumbered by a `VACUUM`.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(NoteRow::class, ["documentId"], ["documentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("documentId", "position", unique = true), Index("date")],
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
    /** Media embed targets, in order (see [MediaRow] for lookups). */
    val media: List<String>,
    /** Where each media embed sits in [text] ([TimelineEntry.mediaOffsets]). */
    val mediaOffsets: List<Int>,
    val raw: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/**
 * Full-text index over the entries' plain text and location. Room keeps it in step with
 * `entries` through triggers. unicode61 folds case and diacritics, so `cafe` finds `café`.
 */
@Fts4(contentEntity = EntryRow::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "entries_fts")
data class EntryFts(val text: String, val location: String?)

/** An entry found by a search, with its day's metadata and a highlighted [snippet]. */
data class SearchHit(
    @Embedded val entry: EntryRow,
    val dayStarred: Boolean,
    val dayMood: Int?,
    val dayTags: List<String>,
    /** Matched text with [SearchSql.MARK_START]/[SearchSql.MARK_END] around the matches. */
    val snippet: String?,
)

/**
 * One media embed of an entry, by the file name it refers to; joined with [AttachmentRow] to
 * find the file. Obsidian resolves `![[name.jpg]]` by file name, so the folder doesn't matter.
 */
@Entity(
    tableName = "media",
    primaryKeys = ["documentId", "position", "mediaIndex"],
    foreignKeys = [
        ForeignKey(NoteRow::class, ["documentId"], ["documentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fileName")],
)
data class MediaRow(
    val documentId: String,
    /** The entry's [EntryRow.position]. */
    val position: Int,
    val mediaIndex: Int,
    val date: LocalDate,
    val time: LocalTime?,
    val ordinal: Int,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val fileName: String,
)

/** A media file found in the attachment folders. The first one found wins for a name. */
@Entity(tableName = "attachments")
data class AttachmentRow(
    @PrimaryKey @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val uri: String,
    val lastModified: Long,
)

/** A media embed with the file it resolves to, for the media grid. */
data class MediaItem(
    val date: LocalDate,
    val time: LocalTime?,
    val ordinal: Int,
    val fileName: String,
    val uri: String,
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

    /** The notes for one day that have entries (normally one), with their entries. */
    @Transaction
    @Query("SELECT * FROM notes WHERE date = :date AND entryCount > 0 ORDER BY documentId")
    fun observeDay(date: LocalDate): Flow<List<NoteWithEntries>>

    /** Days between [from] and [to] (inclusive) that have entries. */
    @Query(
        "SELECT date, SUM(entryCount) AS entries, MAX(starred) AS starred FROM notes " +
            "WHERE entryCount > 0 AND date BETWEEN :from AND :to GROUP BY date",
    )
    fun observeMarkedDays(from: LocalDate, to: LocalDate): Flow<List<DayMarkRow>>

    /**
     * Notes with entries from before [before] whose date ends in one of [monthDays] (`MM-DD`),
     * newest first: the same day in earlier years.
     */
    @Transaction
    @Query(
        "SELECT * FROM notes WHERE entryCount > 0 AND date < :before AND substr(date, 6) IN (:monthDays) " +
            "ORDER BY date DESC, documentId",
    )
    fun observeOnThisDay(monthDays: List<String>, before: LocalDate): Flow<List<NoteWithEntries>>

    /** Every media embed that resolves to a file, newest entry first, in order within an entry. */
    @Query(
        "SELECT m.date, m.time, m.ordinal, m.fileName, a.uri FROM media m " +
            "JOIN attachments a ON a.name = m.fileName " +
            "ORDER BY m.date DESC, m.documentId, m.position, m.mediaIndex",
    )
    fun observeMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM attachments")
    suspend fun attachments(): List<AttachmentRow>

    @Query("DELETE FROM attachments")
    suspend fun clearAttachments()

    @Insert
    suspend fun insertAttachments(rows: List<AttachmentRow>)

    @Transaction
    suspend fun replaceAttachments(rows: List<AttachmentRow>) {
        clearAttachments()
        insertAttachments(rows)
    }

    @RawQuery(observedEntities = [EntryRow::class, NoteRow::class])
    fun search(query: SupportSQLiteQuery): Flow<List<SearchHit>>

    /** The tag lists of all entries and of all days with entries, newline-joined. */
    @Query(
        "SELECT tags FROM entries WHERE tags != '' " +
            "UNION ALL SELECT tags FROM notes WHERE entryCount > 0 AND tags != ''",
    )
    fun observeTagLists(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM notes WHERE readFailed")
    fun observeUnreadable(): Flow<Int>

    @Upsert
    suspend fun upsertNotes(notes: List<NoteRow>)

    @Insert
    suspend fun insertEntries(entries: List<EntryRow>)

    @Insert
    suspend fun insertMedia(media: List<MediaRow>)

    @Query("DELETE FROM entries WHERE documentId IN (:ids)")
    suspend fun deleteEntries(ids: List<String>)

    @Query("DELETE FROM media WHERE documentId IN (:ids)")
    suspend fun deleteMedia(ids: List<String>)

    @Query("DELETE FROM notes WHERE documentId IN (:ids)")
    suspend fun deleteNotes(ids: List<String>)

    @Query("UPDATE notes SET readFailed = 1 WHERE documentId IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM media")
    suspend fun clearMedia()

    /** Replaces the rows of the given notes (and their entries) in one transaction. */
    @Transaction
    suspend fun replace(notes: List<NoteRow>, entries: List<EntryRow>) {
        notes.map { it.documentId }.chunked(MAX_ARGS).forEach {
            deleteEntries(it)
            deleteMedia(it)
        }
        upsertNotes(notes)
        insertEntries(entries)
        insertMedia(entries.flatMap { it.mediaRows() })
    }

    @Transaction
    suspend fun remove(ids: Collection<String>) {
        ids.chunked(MAX_ARGS).forEach {
            deleteEntries(it)
            deleteMedia(it)
            deleteNotes(it)
        }
    }

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Upsert
    suspend fun setMeta(row: MetaRow)

    /** Empties the index and records which vault it now belongs to. */
    @Transaction
    suspend fun reset(source: String) {
        clearMedia()
        clearEntries()
        clearNotes()
        clearAttachments()
        setMeta(MetaRow(SOURCE_KEY, source))
    }

    companion object {
        const val SOURCE_KEY = "source"

        // Stays well under SQLite's limit on bound parameters.
        private const val MAX_ARGS = 500
    }
}

data class DayMarkRow(val date: LocalDate, val entries: Int, val starred: Boolean)

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

    @TypeConverter fun intsToString(ints: List<Int>?): String? = ints?.joinToString(",")
    @TypeConverter fun stringToInts(value: String?): List<Int>? =
        value?.let { if (it.isEmpty()) emptyList() else it.split(',').map(String::toInt) }
}

/**
 * Cache of the journal for fast startup and (later) calendar and search. The markdown files are
 * the source of truth: the index can be dropped and rebuilt by [VaultScanner] at any time, so a
 * schema change just recreates it.
 */
@Database(
    entities = [
        NoteRow::class, EntryRow::class, EntryFts::class, MediaRow::class, AttachmentRow::class, MetaRow::class,
    ],
    version = 4,
    exportSchema = false,
)
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
            e.location, e.weather, e.mood, e.tags, e.media, e.mediaOffsets, e.raw,
        )
    }
    return note to rows
}

private fun EntryRow.mediaRows(): List<MediaRow> = media.mapIndexedNotNull { i, target ->
    Embeds.fileName(target)?.let { MediaRow(documentId, position, i, date, time, ordinal, it) }
}

fun NoteWithEntries.toTimelineDay(): TimelineDay = TimelineDay(
    date = note.date,
    starred = note.starred,
    mood = note.mood,
    tags = note.tags,
    entries = entries.sortedBy { it.position }.map { it.toTimelineEntry() },
)

fun EntryRow.toTimelineEntry(): TimelineEntry = TimelineEntry(
    date = date,
    key = EntryKey(time, ordinal),
    title = title,
    preview = preview,
    text = text,
    location = location,
    weather = weather,
    mood = mood,
    tags = tags,
    media = media,
    mediaOffsets = mediaOffsets,
    raw = raw,
)

/** Builds the [IndexDao.search] query for a [SearchQuery]: newest entries first. */
object SearchSql {
    const val MARK_START = '\uE000'
    const val MARK_END = '\uE001'

    fun build(query: SearchQuery, limit: Int): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val match = query.matchExpression()
        val sql = StringBuilder("SELECT e.*, n.starred AS dayStarred, n.mood AS dayMood, n.tags AS dayTags, ")
        if (match != null) {
            sql.append("snippet(entries_fts, '$MARK_START', '$MARK_END', '…', -1, 20) AS snippet ")
            sql.append("FROM entries_fts JOIN entries e ON e.id = entries_fts.docid ")
        } else {
            sql.append("NULL AS snippet FROM entries e ")
        }
        sql.append("JOIN notes n ON n.documentId = e.documentId WHERE 1 ")
        if (match != null) {
            sql.append("AND entries_fts MATCH ? ")
            args += match
        }
        for (tag in query.tags) {
            val conditions = listOf("e.tags", "n.tags").flatMap { column ->
                query.tagPatterns(tag).map { pattern ->
                    args += pattern
                    "(char(10) || $column || char(10)) LIKE ? ESCAPE '\\'"
                }
            }
            sql.append("AND (${conditions.joinToString(" OR ")}) ")
        }
        sql.append("ORDER BY e.date DESC, e.documentId, e.position LIMIT ?")
        args += limit
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
