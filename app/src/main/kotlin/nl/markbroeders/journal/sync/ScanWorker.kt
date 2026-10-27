package nl.markbroeders.journal.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import nl.markbroeders.journal.JournalApp
import java.util.concurrent.TimeUnit

/** Periodically rescans the vault so the index is fresh when the app opens. */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as JournalApp).container
        val settings = container.settings.vault.first() ?: return Result.success()
        if (!container.vaultAccess.hasPermission(settings.vaultUri)) return Result.success()
        return try {
            container.scanner.scan(settings)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background scan failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ScanWorker"
        private const val NAME = "vault-scan"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScanWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
