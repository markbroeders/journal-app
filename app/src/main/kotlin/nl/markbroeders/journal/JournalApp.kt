package nl.markbroeders.journal

import android.app.Application
import nl.markbroeders.journal.data.JournalRepository
import nl.markbroeders.journal.data.SettingsStore
import nl.markbroeders.journal.data.VaultAccess

class JournalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container; one instance per process. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val vaultAccess = VaultAccess(app)
    val journal = JournalRepository(vaultAccess)
}
