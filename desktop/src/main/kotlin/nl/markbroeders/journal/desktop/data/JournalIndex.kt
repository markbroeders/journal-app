package nl.markbroeders.journal.desktop.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.markbroeders.journal.data.Embeds
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.Timeline
import nl.markbroeders.journal.data.TimelineDay
import nl.markbroeders.journal.data.TimelineEntry
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger

/** A photo or video file an embed resolves to. */
data class MediaFile(val fileName: String, val path: Path) {
    val isVideo: Boolean get() = Embeds.isVideo(fileName)
}

data class IndexState(
    val settings: VaultSettings? = null,
    /** True until the first scan of the current vault is done. */
    val loading: Boolean = true,
    val folderMissing: Boolean = false,
    /** Every day with entries, newest first. */
    val days: List<TimelineDay> = emptyList(),
    /** Media files by lowercase name, the way Obsidian resolves `![[name.jpg]]`. */
    val attachments: Map<String, MediaFile> = emptyMap(),
    /** Files that couldn't be read in the last scan. */
    val failed: List<Path> = emptyList(),
) {
    private val byDate: Map<LocalDate, TimelineDay> by lazy { days.associateBy { it.date } }

    fun day(date: LocalDate): TimelineDay? = byDate[date]

    fun entry(date: LocalDate, key: EntryKey): TimelineEntry? = day(date)?.entries?.firstOrNull { it.key == key }

    fun resolve(target: String): MediaFile? = Embeds.fileName(target)?.let { attachments[it.lowercase()] }

    /** The first resolvable photo or video of [entry], for thumbnails. */
    fun firstMedia(entry: TimelineEntry): MediaFile? = entry.media.firstNotNullOfOrNull(::resolve)
}

/**
 * The journal in memory: every daily note parsed into timeline days, plus the attachment files.
 * A scan lists the folder and re-reads only files whose modification time or size changed. Scans
 * run after [refresh] and whenever something changes in the watched folders (the journal and year
 * folders and the attachment folders), so edits made in Obsidian or arriving through sync show up
 * by themselves. The files stay the only source of truth: nothing is stored besides them.
 */
class JournalIndex(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(IndexState(loading = false))
    val state: StateFlow<IndexState> = _state.asStateFlow()

    @Volatile
    private var session: Session? = null

    var files: JournalFiles? = null
        private set

    /** Switches to [settings] (or to no vault), dropping everything known about the previous one. */
    fun open(settings: VaultSettings?) {
        session?.close()
        session = null
        files = settings?.let(::JournalFiles)
        _state.value = IndexState(settings = settings, loading = settings != null)
        // Record the session before it starts: a scan drops its result if its session isn't current.
        if (settings != null) Session(files!!).also { session = it }.start()
    }

    /** Scans again soon (several requests close together give one scan). */
    fun refresh() {
        session?.requests?.trySend(Unit)
    }

    private inner class Session(val files: JournalFiles) {
        val requests = Channel<Unit>(Channel.CONFLATED)
        private val cache = HashMap<Path, Cached>()
        private val watcher: WatchService? = runCatching { FileSystems.getDefault().newWatchService() }.getOrNull()
        private val watched = HashMap<Path, WatchKey>()
        private var job: Job? = null
        private var watchThread: Thread? = null

        fun start() {
            job = scope.launch(Dispatchers.IO) {
                requests.trySend(Unit)
                for (request in requests) {
                    scan()
                    // Let a burst of file events (a sync, a save) settle before the next scan.
                    delay(SETTLE_MS)
                }
            }
            watchThread = watcher?.let { service ->
                Thread({ watch(service) }, "journal-watcher").apply {
                    isDaemon = true
                    start()
                }
            }
        }

        fun close() {
            job?.cancel()
            requests.close()
            runCatching { watcher?.close() }
        }

        private fun scan() {
            val listing = try {
                files.listNotes()
            } catch (e: Exception) {
                log.log(Level.WARNING, "Listing the journal failed", e)
                return
            }
            if (!isCurrent()) return
            when (listing) {
                NoteListing.FolderMissing -> {
                    cache.clear()
                    watch(listOf(files.settings.vault))
                    _state.update { it.copy(loading = false, folderMissing = true, days = emptyList()) }
                }
                is NoteListing.Files -> {
                    val failed = mutableListOf<Path>()
                    val seen = HashSet<Path>()
                    val days = listing.files.mapNotNull { file ->
                        seen.add(file.path)
                        val cached = cache[file.path]
                        if (cached != null && cached.stamp == file.stamp && cached.date == file.date) return@mapNotNull cached.day
                        try {
                            val day = Timeline.day(files.read(file), file.date)
                            cache[file.path] = Cached(file.date, file.stamp, day)
                            day
                        } catch (e: Exception) {
                            log.log(Level.WARNING, "Reading ${file.path} failed", e)
                            cache.remove(file.path)
                            failed.add(file.path)
                            null
                        }
                    }
                    cache.keys.retainAll(seen)
                    val (attachments, attachmentDirs) = try {
                        files.listAttachments(listing.yearDirs)
                    } catch (e: Exception) {
                        log.log(Level.WARNING, "Listing attachments failed", e)
                        emptyMap<String, Path>() to emptyList()
                    }
                    watch(listOf(files.settings.journalDir) + listing.yearDirs + attachmentDirs)
                    if (!isCurrent()) return
                    _state.update {
                        it.copy(
                            loading = false,
                            folderMissing = false,
                            days = days,
                            attachments = attachments.mapValues { (_, path) -> MediaFile(path.fileName.toString(), path) },
                            failed = failed,
                        )
                    }
                }
            }
        }

        private fun isCurrent() = session === this

        /** Watches [dirs] (and stops watching folders that are gone from the list). */
        @Synchronized
        private fun watch(dirs: List<Path>) {
            val service = watcher ?: return
            val wanted = dirs.map { it.toAbsolutePath().normalize() }.toSet()
            watched.keys.filter { it !in wanted }.forEach { watched.remove(it)?.cancel() }
            for (dir in wanted) {
                if (dir in watched) continue
                try {
                    watched[dir] = dir.register(
                        service,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                    )
                } catch (e: Exception) {
                    log.log(Level.FINE, "Can't watch $dir", e)
                }
            }
        }

        private fun watch(service: WatchService) {
            try {
                while (true) {
                    val key = service.take()
                    val events = key.pollEvents()
                    key.reset()
                    // Our own temp files come and go during every save; the rename that follows counts.
                    val relevant = events.any { event ->
                        val name = (event.context() as? Path)?.fileName?.toString() ?: return@any true
                        !name.endsWith(".tmp")
                    }
                    if (relevant) requests.trySend(Unit)
                }
            } catch (_: ClosedWatchServiceException) {
            } catch (_: InterruptedException) {
            }
        }
    }

    private data class Cached(val date: LocalDate, val stamp: FileStamp, val day: TimelineDay?)

    private companion object {
        val log: Logger = Logger.getLogger("JournalIndex")
        const val SETTLE_MS = 300L
    }
}
