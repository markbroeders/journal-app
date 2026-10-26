package nl.markbroeders.journal.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The chosen vault (SAF tree URI of the vault root) and the journal folder inside it. */
data class VaultSettings(val vaultUri: Uri, val journalFolder: String)

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

    private companion object {
        val VAULT_URI = stringPreferencesKey("vault_uri")
        val JOURNAL_FOLDER = stringPreferencesKey("journal_folder")
    }
}
