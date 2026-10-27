package nl.markbroeders.journal.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max

/**
 * Copies photos into the vault's attachment folder (Obsidian's setting, see [ObsidianConfig]) as
 * `YYYY-MM-DD-HHmm-N.jpg`: upright, at most [MAX_EDGE] pixels on the long side, JPEG. Never
 * overwrites a file; `N` counts up past names already used in the folder or anywhere in the index.
 */
class PhotoImporter(context: Context, private val access: VaultAccess, private val dao: IndexDao) {

    private val resolver = context.contentResolver

    /** Imports [sources] in order; returns the created files. Stops at the first failure (earlier ones stay). */
    suspend fun import(settings: VaultSettings, date: LocalDate, time: LocalTime, sources: List<Uri>): List<DocNode> {
        val folder = access.ensureDirectory(settings.vaultUri, folderPath(settings, date))
        val taken = HashSet<String>()
        access.listChildren(settings.vaultUri, folder).mapTo(taken) { it.name }
        dao.attachments().mapTo(taken) { it.name }
        val created = mutableListOf<DocNode>()
        for (source in sources) {
            val bytes = encode(source)
            val name = Photos.nextName(date, time, taken)
            val file = access.createFile(folder, name)
            try {
                access.writeBytes(file, bytes)
            } catch (e: Exception) {
                runCatching { access.delete(file) }
                throw e
            }
            taken += name
            created += file
        }
        return created
    }

    /** Removes photos that were imported but ended up unused (e.g. the entry was discarded). */
    suspend fun delete(files: List<DocNode>) {
        for (file in files) {
            try {
                access.delete(file)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove unused photo ${file.name}", e)
            }
        }
    }

    private suspend fun folderPath(settings: VaultSettings, date: LocalDate): List<String> =
        when (val folder = access.attachmentFolder(settings.vaultUri)) {
            AttachmentFolder.VaultRoot -> emptyList()
            is AttachmentFolder.InVault -> VaultPaths.segments(folder.path)
            is AttachmentFolder.NextToNote ->
                VaultPaths.dailyNotePath(settings.journalFolder, date).dropLast(1) + VaultPaths.segments(folder.sub)
        }

    /** Decodes [source] at about [MAX_EDGE], turns it upright, and encodes it as JPEG. */
    private suspend fun encode(source: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) throw IOException("This file isn't a supported image.")

        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = open(source).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IOException("This image couldn't be read.")
        val orientation = runCatching {
            open(source).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val scale = minOf(1f, MAX_EDGE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val bitmap = if (matrix.isIdentity) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        }
        try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun open(uri: Uri) = resolver.openInputStream(uri) ?: throw IOException("Can't open the photo.")

    private companion object {
        const val TAG = "PhotoImporter"
        const val MAX_EDGE = 2560
        const val JPEG_QUALITY = 85
    }
}
