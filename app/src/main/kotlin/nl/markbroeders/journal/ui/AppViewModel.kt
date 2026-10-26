package nl.markbroeders.journal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.VaultSettings

sealed interface AppState {
    data object Loading : AppState

    /** No usable vault. [previous] is set when one was configured but access was lost. */
    data class NeedsSetup(val previous: VaultSettings?) : AppState

    data class Ready(val settings: VaultSettings, val vaultName: String) : AppState
}

/** Decides at startup (and after every settings change) whether the vault is usable. */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<AppState> = container.settings.vault
        .map { settings ->
            val access = container.vaultAccess
            when {
                settings == null -> AppState.NeedsSetup(null)
                !access.hasPermission(settings.vaultUri) -> AppState.NeedsSetup(settings)
                else -> access.root(settings.vaultUri)
                    ?.let { root ->
                        access.releaseOtherPermissions(settings.vaultUri)
                        AppState.Ready(settings, root.name)
                    }
                    ?: AppState.NeedsSetup(settings)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)
}
