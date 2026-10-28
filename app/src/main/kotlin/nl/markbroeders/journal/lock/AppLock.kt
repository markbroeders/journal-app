package nl.markbroeders.journal.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the journal is unlocked in this process. Whether the lock is on at all is a setting
 * ([nl.markbroeders.journal.data.SettingsStore.appLock]); the app is locked when it is on and
 * [unlocked] is false. A new process always starts locked.
 */
class AppLock {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** Bumped on every [lock]: the lock screen asks to unlock once per request. */
    private val _promptRequests = MutableStateFlow(0)
    val promptRequests: StateFlow<Int> = _promptRequests.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    /** Called when the app leaves the screen. */
    fun lock() {
        _unlocked.value = false
        _promptRequests.update { it + 1 }
    }
}
