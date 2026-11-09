package nl.markbroeders.journal.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.markbroeders.journal.data.VaultPaths
import java.nio.file.Path
import java.util.prefs.Preferences

enum class Appearance { System, Light, Dark }

data class DesktopSettings(
    val vault: Path? = null,
    val journalFolder: String = VaultPaths.DEFAULT_JOURNAL_FOLDER,
    val appearance: Appearance = Appearance.System,
    val serif: Boolean = true,
    /** Width of the timeline column, in dp. */
    val sidebarWidth: Float = DEFAULT_SIDEBAR_WIDTH,
) {
    val vaultSettings: VaultSettings? get() = vault?.let { VaultSettings(it, journalFolder) }
}

const val DEFAULT_SIDEBAR_WIDTH = 400f

/** The app's own settings (not the journal's), in the user's Java preferences (`~/.java/.userPrefs`). */
class SettingsStore(private val prefs: Preferences = Preferences.userRoot().node("nl/markbroeders/journal")) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        val next = transform(_settings.value)
        if (next.vault != null) prefs.put(KEY_VAULT, next.vault.toString()) else prefs.remove(KEY_VAULT)
        prefs.put(KEY_JOURNAL, next.journalFolder)
        prefs.put(KEY_APPEARANCE, next.appearance.name)
        prefs.putBoolean(KEY_SERIF, next.serif)
        prefs.putFloat(KEY_SIDEBAR_WIDTH, next.sidebarWidth)
        runCatching { prefs.flush() }
        _settings.value = next
    }

    private fun read() = DesktopSettings(
        vault = prefs.get(KEY_VAULT, null)?.let { Path.of(it) },
        journalFolder = prefs.get(KEY_JOURNAL, null)?.let(VaultPaths::normalizeFolder) ?: VaultPaths.DEFAULT_JOURNAL_FOLDER,
        appearance = prefs.get(KEY_APPEARANCE, null)?.let { v -> Appearance.entries.firstOrNull { it.name == v } } ?: Appearance.System,
        serif = prefs.getBoolean(KEY_SERIF, true),
        sidebarWidth = prefs.getFloat(KEY_SIDEBAR_WIDTH, DEFAULT_SIDEBAR_WIDTH),
    )

    private companion object {
        const val KEY_VAULT = "vault"
        const val KEY_JOURNAL = "journalFolder"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_SERIF = "serif"
        const val KEY_SIDEBAR_WIDTH = "sidebarWidth"
    }
}
