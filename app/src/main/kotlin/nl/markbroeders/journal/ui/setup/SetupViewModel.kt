package nl.markbroeders.journal.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markbroeders.journal.AppContainer
import nl.markbroeders.journal.data.VaultPaths
import nl.markbroeders.journal.data.VaultSettings

data class PickedVault(
    val uri: Uri,
    val name: String,
    /** Whether the folder contains `.obsidian`, i.e. is a vault root. */
    val isObsidianVault: Boolean,
    /** Visible top-level folders, offered as journal folder suggestions. */
    val folders: List<String>,
)

sealed interface JournalCheck {
    data object Pending : JournalCheck
    data object Invalid : JournalCheck
    data object Missing : JournalCheck
    data object NotAFolder : JournalCheck
    data object Error : JournalCheck

    /** [path] is the folder's actual (case-correct) vault-relative path. */
    data class Found(val path: String, val years: List<String>, val noteCount: Int) : JournalCheck
}

data class SetupUiState(
    val vault: PickedVault? = null,
    val loadingVault: Boolean = false,
    val vaultError: String? = null,
    val journalInput: String = VaultPaths.DEFAULT_JOURNAL_FOLDER,
    val journalCheck: JournalCheck = JournalCheck.Pending,
    val saving: Boolean = false,
) {
    val canSave: Boolean
        get() = vault != null && !saving &&
            (journalCheck is JournalCheck.Found || journalCheck == JournalCheck.Missing)
}

class SetupViewModel(
    private val container: AppContainer,
    initial: VaultSettings?,
) : ViewModel() {

    private val access = container.vaultAccess
    private val _state = MutableStateFlow(
        SetupUiState(journalInput = initial?.journalFolder ?: VaultPaths.DEFAULT_JOURNAL_FOLDER),
    )
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        if (initial != null && access.hasPermission(initial.vaultUri)) {
            viewModelScope.launch { loadVault(initial.vaultUri) }
        }
    }

    fun onVaultPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                access.takePermission(uri)
            } catch (_: SecurityException) {
                _state.update { it.copy(vaultError = "Couldn't keep access to this folder. Try picking it again.") }
                return@launch
            }
            loadVault(uri)
        }
    }

    fun onJournalInputChange(text: String) {
        _state.update { it.copy(journalInput = text) }
        checkJournal(debounceMs = 300)
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val vault = s.vault ?: return
        val folder = (s.journalCheck as? JournalCheck.Found)?.path
            ?: VaultPaths.normalizeFolder(s.journalInput)
            ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            container.settings.saveVault(VaultSettings(vault.uri, folder))
            access.releaseOtherPermissions(vault.uri)
            onSaved()
        }
    }

    private suspend fun loadVault(uri: Uri) {
        _state.update { it.copy(loadingVault = true, vaultError = null) }
        val root = access.root(uri)
        val children = root?.let {
            try {
                access.listChildren(uri, it)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        if (root == null || children == null) {
            _state.update { it.copy(loadingVault = false, vaultError = "Couldn't read this folder.") }
            return
        }
        val vault = PickedVault(
            uri = uri,
            name = root.name,
            isObsidianVault = children.any { it.isDirectory && it.name == ".obsidian" },
            folders = children.filter { it.isDirectory && !it.name.startsWith(".") }
                .map { it.name }
                .sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
        _state.update { it.copy(vault = vault, loadingVault = false) }
        checkJournal(debounceMs = 0)
    }

    private fun checkJournal(debounceMs: Long) {
        checkJob?.cancel()
        val vault = _state.value.vault ?: return
        checkJob = viewModelScope.launch {
            _state.update { it.copy(journalCheck = JournalCheck.Pending) }
            delay(debounceMs)
            val folder = VaultPaths.normalizeFolder(_state.value.journalInput)
            val result = if (folder == null) {
                JournalCheck.Invalid
            } else {
                try {
                    inspect(vault.uri, folder)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    JournalCheck.Error
                }
            }
            _state.update { it.copy(journalCheck = result) }
        }
    }

    private suspend fun inspect(vaultUri: Uri, folder: String): JournalCheck {
        val path = access.resolvePath(vaultUri, VaultPaths.segments(folder)) ?: return JournalCheck.Missing
        val journal = path.last()
        if (!journal.isDirectory) return JournalCheck.NotAFolder
        val years = access.listChildren(vaultUri, journal)
            .filter { it.isDirectory && VaultPaths.isYearFolder(it.name) }
            .sortedBy { it.name }
        val noteCount = years.sumOf { year ->
            access.listChildren(vaultUri, year).count { !it.isDirectory && VaultPaths.isDailyNoteName(it.name) }
        }
        return JournalCheck.Found(path.joinToString("/") { it.name }, years.map { it.name }, noteCount)
    }
}
