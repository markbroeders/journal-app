package nl.markbroeders.journal

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import nl.markbroeders.journal.data.IndexDb
import nl.markbroeders.journal.data.JournalRepository
import nl.markbroeders.journal.data.PhotoImporter
import nl.markbroeders.journal.data.SettingsStore
import nl.markbroeders.journal.data.VaultAccess
import nl.markbroeders.journal.lock.AppLock
import nl.markbroeders.journal.reminder.DailyReminder
import nl.markbroeders.journal.sync.ScanWorker
import nl.markbroeders.journal.sync.VaultScanner

class JournalApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ScanWorker.schedule(this)
        // Also restores the alarm after a force stop (which cancels it) once the app is opened.
        MainScope().launch {
            container.settings.reminder.distinctUntilChanged().collect { DailyReminder.schedule(this@JournalApp, it) }
        }
    }

    /** Thumbnails for the media grid; videos show their first frame. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}

/** Manual dependency container; one instance per process. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val lock = AppLock()
    val vaultAccess = VaultAccess(app)
    val journal = JournalRepository(vaultAccess)
    val index = IndexDb.create(app)
    val scanner = VaultScanner(journal, vaultAccess, index)
    val photos = PhotoImporter(app, vaultAccess, index.dao())
}
