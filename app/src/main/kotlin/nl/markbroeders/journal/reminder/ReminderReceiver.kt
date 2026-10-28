package nl.markbroeders.journal.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.markbroeders.journal.JournalApp
import nl.markbroeders.journal.data.Reminder
import java.time.LocalDate

/**
 * The reminder's alarm: posts the notification unless today's note already has an entry, then
 * sets tomorrow's alarm. Also sets the alarm again after a reboot, an app update or a change of
 * the clock or time zone (alarms don't survive those, or would ring at the wrong time).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == DailyReminder.ACTION_REMIND && !writtenToday(context)) {
                    DailyReminder.notify(context)
                }
                DailyReminder.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Reads today's note from the vault rather than the index, which may not have seen an entry
     * written in Obsidian yet. If that can't be done in time, the reminder shows.
     */
    private suspend fun writtenToday(context: Context): Boolean {
        val container = (context.applicationContext as JournalApp).container
        val settings = container.settings.vault.first() ?: return false
        if (!container.vaultAccess.hasPermission(settings.vaultUri)) return false
        return try {
            withTimeoutOrNull(READ_TIMEOUT_MS) {
                Reminder.isWritten(container.journal.loadDay(settings, LocalDate.now()))
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read today's note", e)
            false
        }
    }

    private companion object {
        const val TAG = "ReminderReceiver"
        // goAsync() allows about 10 seconds in total.
        const val READ_TIMEOUT_MS = 6_000L
    }
}
