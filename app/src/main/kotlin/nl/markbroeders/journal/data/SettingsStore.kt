package nl.markbroeders.journal.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** The chosen vault (SAF tree URI of the vault root) and the journal folder inside it. */
data class VaultSettings(val vaultUri: Uri, val journalFolder: String)

/** The daily reminder: a notification at [time] on days without an entry yet. */
data class ReminderSettings(val enabled: Boolean, val time: LocalTime)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** [serif]: entry text (the entry view and the editor) in the serif reading font. */
data class Appearance(val theme: ThemeMode = ThemeMode.SYSTEM, val serif: Boolean = true)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val vault: Flow<VaultSettings?> = context.dataStore.data.map { prefs ->
        prefs[VAULT_URI]?.let { uri ->
            VaultSettings(Uri.parse(uri), prefs[JOURNAL_FOLDER] ?: VaultPaths.DEFAULT_JOURNAL_FOLDER)
        }
    }

    suspend fun saveVault(settings: VaultSettings) {
        context.dataStore.edit { prefs ->
            prefs[VAULT_URI] = settings.vaultUri.toString()
            prefs[JOURNAL_FOLDER] = settings.journalFolder
        }
    }

    /** Whether opening the app needs biometrics or the device PIN/pattern (see AppLock). */
    val appLock: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[APP_LOCK] ?: false }

    suspend fun setAppLock(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[APP_LOCK] = enabled }
    }

    val reminder: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[REMINDER] ?: false,
            time = prefs[REMINDER_MINUTE]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: Reminder.DEFAULT_TIME,
        )
    }

    suspend fun setReminder(settings: ReminderSettings) {
        context.dataStore.edit { prefs ->
            prefs[REMINDER] = settings.enabled
            prefs[REMINDER_MINUTE] = settings.time.hour * 60 + settings.time.minute
        }
    }

    val appearance: Flow<Appearance> = context.dataStore.data.map { prefs ->
        Appearance(
            theme = prefs[THEME]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM,
            serif = prefs[SERIF] ?: true,
        )
    }

    suspend fun setAppearance(appearance: Appearance) {
        context.dataStore.edit { prefs ->
            prefs[THEME] = appearance.theme.name
            prefs[SERIF] = appearance.serif
        }
    }

    private companion object {
        val VAULT_URI = stringPreferencesKey("vault_uri")
        val JOURNAL_FOLDER = stringPreferencesKey("journal_folder")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val REMINDER = booleanPreferencesKey("reminder")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val THEME = stringPreferencesKey("theme")
        val SERIF = booleanPreferencesKey("serif")
    }
}
