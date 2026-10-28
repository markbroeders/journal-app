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

    // Leftover permissions (e.g. a folder picked in settings but not saved) are released once per
    // start. Not on every evaluation: this flow restarts when the app returns from the folder
    // picker, and would then release the folder just picked (setup releases the rest on save).
    private var releasedLeftovers = false

    val state: StateFlow<AppState> = container.settings.vault
        .map { settings ->
            val access = container.vaultAccess
            when {
                settings == null -> AppState.NeedsSetup(null)
                !access.hasPermission(settings.vaultUri) -> AppState.NeedsSetup(settings)
                else -> access.root(settings.vaultUri)
                    ?.let { root ->
                        if (!releasedLeftovers) {
                            releasedLeftovers = true
                            access.releaseOtherPermissions(settings.vaultUri)
                        }
                        AppState.Ready(settings, root.name)
                    }
                    ?: AppState.NeedsSetup(settings)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)
}
