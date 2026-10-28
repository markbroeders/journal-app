package nl.markbroeders.journal.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import nl.markbroeders.journal.R

/** Home-screen widget: write a new entry, or start one with a photo. */
class CaptureWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_capture).apply {
            setOnClickPendingIntent(R.id.widget_new_entry, pendingIntent(context, camera = false))
            setOnClickPendingIntent(R.id.widget_new_photo, pendingIntent(context, camera = true))
        }
        manager.updateAppWidget(ids, views)
    }

    private fun pendingIntent(context: Context, camera: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (camera) 1 else 0,
        QuickCapture.intent(context, camera),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
