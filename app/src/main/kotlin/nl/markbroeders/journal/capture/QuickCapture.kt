package nl.markbroeders.journal.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import nl.markbroeders.journal.MainActivity
import nl.markbroeders.journal.data.SharedText

/** A request to start a new entry right away: from a share, the widget or an app shortcut. */
sealed interface QuickCapture {

    /** Text and/or photos shared from another app; the app returns to it when the entry closes. */
    data class Share(val text: String, val photos: List<Uri>) : QuickCapture

    /** A blank new entry, optionally starting with the camera. */
    data class NewEntry(val camera: Boolean) : QuickCapture

    companion object {
        const val ACTION_NEW_ENTRY = "nl.markbroeders.journal.action.NEW_ENTRY"
        const val ACTION_NEW_PHOTO = "nl.markbroeders.journal.action.NEW_PHOTO"

        fun from(intent: Intent?): QuickCapture? = when (intent?.action) {
            ACTION_NEW_ENTRY -> NewEntry(camera = false)
            ACTION_NEW_PHOTO -> NewEntry(camera = true)
            Intent.ACTION_SEND -> share(
                intent,
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)),
            )
            Intent.ACTION_SEND_MULTIPLE -> share(
                intent,
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty(),
            )
            else -> null
        }

        private fun share(intent: Intent, streams: List<Uri>): Share? {
            val text = SharedText.compose(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
            // Only images become photos; other streams (a shared file) are ignored.
            val isImage = intent.type?.startsWith("image/") == true
            val photos = if (isImage) streams else emptyList()
            return if (text.isEmpty() && photos.isEmpty()) null else Share(text, photos)
        }

        /** Opens the app on a fresh new entry (the widget and shortcuts use this). */
        fun intent(context: Context, camera: Boolean): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(if (camera) ACTION_NEW_PHOTO else ACTION_NEW_ENTRY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
