package nl.markbroeders.journal.data

import java.net.URLDecoder

/** Where Obsidian puts new attachments (the "Default location for new attachments" setting). */
sealed interface AttachmentFolder {
    /** `/`: the vault root. */
    data object VaultRoot : AttachmentFolder

    /** A vault-relative folder, e.g. `Attachments` or `x - attachments`. */
    data class InVault(val path: String) : AttachmentFolder

    /** `./` or `./sub`: next to the note, optionally in a subfolder ([sub] empty for the note's own folder). */
    data class NextToNote(val sub: String) : AttachmentFolder
}

/** Reads the bits of `.obsidian/app.json` the app needs. */
object ObsidianConfig {

    /** Used when app.json is missing or doesn't set the attachment folder. */
    val DEFAULT_ATTACHMENT_FOLDER = AttachmentFolder.InVault("Attachments")

    // A lenient lookup instead of a JSON parser: app.json is flat and this key is a plain string.
    private val ATTACHMENT_FOLDER = Regex(""""attachmentFolderPath"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    fun attachmentFolder(appJson: String?): AttachmentFolder {
        val raw = appJson?.let { ATTACHMENT_FOLDER.find(it)?.groupValues?.get(1) }?.let(::unescape)
            ?: return DEFAULT_ATTACHMENT_FOLDER
        val path = raw.trim()
        return when {
            path.isEmpty() || path == "/" -> AttachmentFolder.VaultRoot
            path == "." || path.startsWith("./") ->
                AttachmentFolder.NextToNote(VaultPaths.segments(path.removePrefix(".")).joinToString("/"))
            else -> VaultPaths.segments(path).joinToString("/")
                .let { if (it.isEmpty()) AttachmentFolder.VaultRoot else AttachmentFolder.InVault(it) }
        }
    }

    private fun unescape(s: String): String = s.replace(Regex("""\\(u[0-9a-fA-F]{4}|.)""")) { m ->
        val c = m.groupValues[1]
        when {
            c.length == 5 -> c.substring(1).toInt(16).toChar().toString()
            c == "n" -> "\n"
            c == "t" -> "\t"
            else -> c
        }
    }
}

/** Turning embed targets into the file names they refer to. */
object Embeds {

    /**
     * The file name an embed target points at: the last path segment, without a `#heading` or
     * `|size` suffix. Markdown links are URL-encoded (`my%20photo.jpg`), wikilinks are not.
     */
    fun fileName(target: String): String? {
        var t = target.substringBefore('|').substringBefore('#').trim()
        if (t.contains("://")) return null
        if (t.contains('%')) t = runCatching { URLDecoder.decode(t.replace("+", "%2B"), "UTF-8") }.getOrDefault(t)
        return t.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    fun isMedia(target: String): Boolean = Markdown.isMedia(target)

    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "mkv", "m4v")

    fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
}
