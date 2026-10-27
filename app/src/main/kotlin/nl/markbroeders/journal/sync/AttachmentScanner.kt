package nl.markbroeders.journal.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import nl.markbroeders.journal.data.AttachmentFolder
import nl.markbroeders.journal.data.AttachmentRow
import nl.markbroeders.journal.data.DocNode
import nl.markbroeders.journal.data.Embeds
import nl.markbroeders.journal.data.IndexDao
import nl.markbroeders.journal.data.VaultAccess
import nl.markbroeders.journal.data.VaultPaths
import nl.markbroeders.journal.data.VaultSettings

/**
 * Lists the media files that embeds can refer to, so the media grid can find them by name.
 * Looks in Obsidian's attachment folder (from `.obsidian/app.json`) first, then in the usual
 * fallbacks: the vault root, `Attachments`, and the note folders. One query per folder.
 */
class AttachmentScanner(private val access: VaultAccess, private val dao: IndexDao) {

    /** Updates the attachments table; failures are logged, the rest of the scan goes on. */
    suspend fun scan(settings: VaultSettings, yearFolders: List<DocNode>) {
        try {
            val rows = list(settings, yearFolders)
            val current = dao.attachments().toSet()
            if (current != rows.toSet()) {
                dao.replaceAttachments(rows)
                Log.i(TAG, "Indexed ${rows.size} attachments")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Listing attachments failed", e)
        }
    }

    private suspend fun list(settings: VaultSettings, yearFolders: List<DocNode>): List<AttachmentRow> {
        val uri = settings.vaultUri
        val folder = access.attachmentFolder(uri)
        val root = access.root(uri) ?: return emptyList()

        // Earlier sources win when two folders hold a file with the same name.
        val found = LinkedHashMap<String, AttachmentRow>()
        val visited = HashSet<String>()
        suspend fun collect(dir: DocNode?, depth: Int) {
            if (dir == null || !dir.isDirectory || !visited.add(dir.documentId)) return
            for (child in access.listChildren(uri, dir)) {
                when {
                    child.isDirectory -> if (depth > 0 && !child.name.startsWith('.')) collect(child, depth - 1)
                    Embeds.isMedia(child.name) -> found.putIfAbsent(
                        child.name.lowercase(),
                        AttachmentRow(child.name, child.uri.toString(), child.lastModified),
                    )
                }
            }
        }
        suspend fun resolve(path: String) = access.resolve(uri, VaultPaths.segments(path))

        when (folder) {
            AttachmentFolder.VaultRoot -> collect(root, depth = 0)
            is AttachmentFolder.InVault -> collect(resolve(folder.path), SUBFOLDER_DEPTH)
            is AttachmentFolder.NextToNote -> for (year in yearFolders) {
                val dir = if (folder.sub.isEmpty()) year else resolveIn(year, folder.sub)
                collect(dir, SUBFOLDER_DEPTH)
            }
        }
        collect(root, depth = 0)
        collect(resolve(DEFAULT_FOLDER), SUBFOLDER_DEPTH)
        for (year in yearFolders) collect(year, depth = 0)
        return found.values.toList()
    }

    /** Resolves a relative [path] below [dir], case-insensitively like [VaultAccess.resolve]. */
    private suspend fun resolveIn(dir: DocNode, path: String): DocNode? {
        var node = dir
        for (segment in VaultPaths.segments(path)) {
            val children = access.listChildren(dir.treeUri, node)
            node = children.firstOrNull { it.name == segment }
                ?: children.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
        }
        return node
    }

    private companion object {
        const val TAG = "AttachmentScanner"
        const val DEFAULT_FOLDER = "Attachments"

        // Attachment folders are sometimes split into subfolders (per year, per note); don't go deeper.
        const val SUBFOLDER_DEPTH = 2
    }
}
