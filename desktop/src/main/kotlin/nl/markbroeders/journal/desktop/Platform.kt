package nl.markbroeders.journal.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Desktop integration: file pickers, opening files and links, the system's dark mode. */
object Platform {

    private val log = Logger.getLogger("Platform")

    /** Asks for a folder; null if cancelled. Uses the desktop's own dialog (zenity) when there is one. */
    suspend fun pickFolder(title: String, start: Path?): Path? {
        zenityAsync(listOf("--file-selection", "--directory", "--title=$title") + startArg(start, folder = true))?.let { output ->
            return output.lineSequence().firstOrNull { it.isNotBlank() }?.let { Path.of(it) }
        }
        val chooser = JFileChooser(start?.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return withContext(Dispatchers.Main) {
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
        }
    }

    /** Asks for one or more images; empty if cancelled. */
    suspend fun pickImages(title: String, start: Path?): List<Path> {
        val patterns = IMAGE_EXTENSIONS.flatMap { listOf("*.$it", "*.${it.uppercase()}") }.joinToString(" ")
        zenityAsync(
            listOf("--file-selection", "--multiple", "--separator=\n", "--title=$title", "--file-filter=Images | $patterns") +
                startArg(start, folder = true),
        )?.let { output ->
            return output.lines().filter { it.isNotBlank() }.map { Path.of(it) }
        }
        val chooser = JFileChooser(start?.toFile()).apply {
            dialogTitle = title
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter("Images", *IMAGE_EXTENSIONS.toTypedArray())
        }
        return withContext(Dispatchers.Main) {
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFiles.map { it.toPath() } else emptyList()
        }
    }

    /** Opens [path] in its default app (a video, a photo). */
    fun open(path: Path) {
        if (!xdgOpen(path.toString())) {
            runCatching { Desktop.getDesktop().open(path.toFile()) }.onFailure { log.log(Level.WARNING, "Can't open $path", it) }
        }
    }

    fun browse(uri: String) {
        if (!xdgOpen(uri)) {
            runCatching { Desktop.getDesktop().browse(URI(uri)) }.onFailure { log.log(Level.WARNING, "Can't open $uri", it) }
        }
    }

    /** Shows [note] in Obsidian (its `obsidian://` link by absolute path). */
    fun openInObsidian(note: Path) {
        val encoded = URLEncoder.encode(note.toAbsolutePath().toString(), Charsets.UTF_8).replace("+", "%20")
        browse("obsidian://open?path=$encoded")
    }

    /** True when the desktop prefers a dark look (GNOME's setting, which most desktops follow); null if unknown. */
    fun prefersDark(): Boolean? {
        val output = run(listOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")) ?: return null
        return when {
            "dark" in output -> true
            "light" in output || "default" in output -> false
            else -> null
        }
    }

    private fun startArg(start: Path?, folder: Boolean): List<String> =
        start?.takeIf { Files.isDirectory(it) }?.let { listOf("--filename=$it${if (folder) File.separator else ""}") }.orEmpty()

    private suspend fun zenityAsync(args: List<String>): String? = withContext(Dispatchers.IO) { zenity(args) }

    /** Runs zenity; its output, "" when cancelled, or null if zenity isn't there. */
    private fun zenity(args: List<String>): String? {
        if (!hasZenity) return null
        return try {
            val process = ProcessBuilder(listOf("zenity") + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            log.log(Level.FINE, "zenity failed", e)
            null
        }
    }

    private val hasZenity: Boolean by lazy { isOnPath("zenity") }

    private fun xdgOpen(target: String): Boolean {
        if (!isOnPath("xdg-open")) return false
        return runCatching {
            ProcessBuilder("xdg-open", target).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            true
        }.getOrDefault(false)
    }

    private fun run(command: List<String>): String? = try {
        if (!isOnPath(command.first())) {
            null
        } else {
            val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            if (process.waitFor(2, TimeUnit.SECONDS)) process.inputStream.bufferedReader().readText() else null
        }
    } catch (_: Exception) {
        null
    }

    private fun isOnPath(program: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).any { Files.isExecutable(Path.of(it, program)) }

    private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}
