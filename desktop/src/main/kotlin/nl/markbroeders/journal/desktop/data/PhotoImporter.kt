package nl.markbroeders.journal.desktop.data

import nl.markbroeders.journal.data.Photos
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Copies photos into the vault's attachment folder (Obsidian's setting) as
 * `YYYY-MM-DD-HHmm-N.jpg`, like the Android app: upright, at most [MAX_EDGE] pixels on the long
 * side, JPEG. Never overwrites a file; `N` counts up past names used in the folder or anywhere
 * else an embed could resolve to ([taken]).
 */
class PhotoImporter(private val files: JournalFiles) {

    /** Imports [sources] in order; returns the created files. Stops at the first failure (earlier ones stay). */
    fun import(date: LocalDate, time: LocalTime, sources: List<Path>, taken: Set<String>): List<Path> {
        val folder = files.attachmentDir(date)
        Files.createDirectories(folder)
        val names = HashSet(taken)
        folder.listDirectoryEntries().mapTo(names) { it.name }
        val created = mutableListOf<Path>()
        for (source in sources) {
            val bytes = encode(source)
            val name = Photos.nextName(date, time, names)
            val target = folder.resolve(name)
            try {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            } catch (e: Exception) {
                if (e !is java.nio.file.FileAlreadyExistsException) runCatching { Files.deleteIfExists(target) }
                throw e
            }
            names += name
            created.add(target)
        }
        return created
    }

    /** Removes photos that were imported but ended up unused (e.g. the entry was discarded). */
    fun delete(paths: List<Path>) {
        for (path in paths) runCatching { Files.deleteIfExists(path) }
    }

    private fun encode(source: Path): ByteArray {
        val codec = try {
            Codec.makeFromData(Data.makeFromBytes(Files.readAllBytes(source)))
        } catch (e: IllegalArgumentException) {
            throw IOException("${source.name} isn't a supported image.", e)
        }
        val image = Image.makeFromBitmap(codec.readPixels())
        val origin = codec.encodedOrigin
        val swap = origin in SWAPPED
        val scale = minOf(1f, MAX_EDGE.toFloat() / max(codec.width, codec.height))
        val w = (codec.width * scale).roundToInt().coerceAtLeast(1)
        val h = (codec.height * scale).roundToInt().coerceAtLeast(1)
        val outW = if (swap) h else w
        val outH = if (swap) w else h

        val surface = Surface.makeRasterN32Premul(outW, outH)
        val canvas = surface.canvas
        canvas.clear(Color.WHITE)
        canvas.concat(orientation(origin, outW.toFloat(), outH.toFloat()))
        canvas.drawImageRect(
            image,
            Rect.makeWH(codec.width.toFloat(), codec.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
            Paint(),
            true,
        )
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)?.bytes
            ?: throw IOException("${source.name} couldn't be converted.")
    }

    /** Maps the stored pixels onto an upright canvas of [w]×[h] (EXIF orientation). */
    private fun orientation(origin: EncodedOrigin, w: Float, h: Float): Matrix33 = when (origin) {
        EncodedOrigin.TOP_RIGHT -> Matrix33(-1f, 0f, w, 0f, 1f, 0f, 0f, 0f, 1f)
        EncodedOrigin.BOTTOM_RIGHT -> Matrix33(-1f, 0f, w, 0f, -1f, h, 0f, 0f, 1f)
        EncodedOrigin.BOTTOM_LEFT -> Matrix33(1f, 0f, 0f, 0f, -1f, h, 0f, 0f, 1f)
        EncodedOrigin.LEFT_TOP -> Matrix33(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        EncodedOrigin.RIGHT_TOP -> Matrix33(0f, -1f, w, 1f, 0f, 0f, 0f, 0f, 1f)
        EncodedOrigin.RIGHT_BOTTOM -> Matrix33(0f, -1f, w, -1f, 0f, h, 0f, 0f, 1f)
        EncodedOrigin.LEFT_BOTTOM -> Matrix33(0f, 1f, 0f, -1f, 0f, h, 0f, 0f, 1f)
        else -> Matrix33.IDENTITY
    }

    private companion object {
        const val MAX_EDGE = 2560
        const val JPEG_QUALITY = 85
        val SWAPPED = setOf(EncodedOrigin.LEFT_TOP, EncodedOrigin.RIGHT_TOP, EncodedOrigin.RIGHT_BOTTOM, EncodedOrigin.LEFT_BOTTOM)
    }
}
