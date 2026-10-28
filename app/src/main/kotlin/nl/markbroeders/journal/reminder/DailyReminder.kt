package nl.markbroeders.journal.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import nl.markbroeders.journal.JournalApp
import nl.markbroeders.journal.R
import nl.markbroeders.journal.capture.QuickCapture
import nl.markbroeders.journal.data.ReminderSettings
import nl.markbroeders.journal.data.Reminder
import java.time.ZonedDateTime

/**
 * The daily reminder: an alarm at the chosen time ([ReminderReceiver] posts the notification and
 * sets the next one). The alarm is inexact (a 10 minute window), which needs no exact-alarm
 * permission; in deep Doze it may come later.
 */
object DailyReminder {
    private const val CHANNEL_ID = "daily-reminder"
    private const val NOTIFICATION_ID = 1
    private const val WINDOW_MS = 10 * 60_000L
    const val ACTION_REMIND = "nl.markbroeders.journal.action.REMIND"

    /** Sets the next alarm for [settings], or cancels it when the reminder is off. */
    fun schedule(context: Context, settings: ReminderSettings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val alarm = alarmIntent(context)
        if (!settings.enabled) {
            alarms.cancel(alarm)
            return
        }
        createChannel(context)
        val at = Reminder.nextAt(ZonedDateTime.now(), settings.time)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), WINDOW_MS, alarm)
    }

    suspend fun reschedule(context: Context) {
        val container = (context.applicationContext as JournalApp).container
        schedule(context, container.settings.reminder.first())
    }

    /** Whether the reminder's notification can show (permission granted, notifications not blocked). */
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun notify(context: Context) {
        if (!canNotify(context)) return
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_edit)
            .setContentTitle("Time to write")
            .setContentText("How was your day? Tap to write today's entry.")
            .setContentIntent(captureIntent(context, camera = false))
            .addAction(R.drawable.ic_widget_camera, "Photo", captureIntent(context, camera = true))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was revoked in the meantime.
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "A reminder to write, on days without an entry yet" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Request codes 10/11: distinct from the widget's, so the two don't replace each other's extras.
    private fun captureIntent(context: Context, camera: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (camera) 11 else 10,
        QuickCapture.intent(context, camera),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
