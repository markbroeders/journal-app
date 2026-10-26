package nl.markbroeders.journal.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A document (file or folder) inside the vault tree, with the metadata from one query row. */
data class DocNode(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val lastModified: Long,
    val size: Long,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
}

/**
 * Storage Access Framework access to the vault root. Uses [DocumentsContract] queries directly
 * rather than DocumentFile, so listing a folder is one query instead of one per child.
 */
class VaultAccess(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    /** Persists read/write access to a tree picked with ACTION_OPEN_DOCUMENT_TREE. */
    fun takePermission(treeUri: Uri) {
        resolver.takePersistableUriPermission(treeUri, READ_WRITE)
    }

    fun hasPermission(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }

    /** Releases every persisted tree permission except [keep]; the app only ever uses one vault. */
    fun releaseOtherPermissions(keep: Uri) {
        for (permission in resolver.persistedUriPermissions) {
            if (permission.uri == keep) continue
            val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            runCatching { resolver.releasePersistableUriPermission(permission.uri, flags) }
        }
    }

    /** The vault root folder, or null if it is gone or access was revoked. */
    suspend fun root(treeUri: Uri): DocNode? = withContext(Dispatchers.IO) {
        runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri),
            )
            resolver.query(docUri, PROJECTION, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.toNode(treeUri) else null
            }
        }.getOrNull()
    }

    /** Direct children of [parent]. Throws if the folder can't be read. */
    suspend fun listChildren(treeUri: Uri, parent: DocNode): List<DocNode> = withContext(Dispatchers.IO) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
        resolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.toNode(treeUri)) }
        }.orEmpty()
    }

    /**
     * Walks [segments] (vault-relative) from the root. Prefers an exact name match and falls
     * back to a case-insensitive one, as Android shared storage is case-insensitive.
     */
    suspend fun resolve(treeUri: Uri, segments: List<String>): DocNode? =
        resolvePath(treeUri, segments)?.lastOrNull() ?: if (segments.isEmpty()) root(treeUri) else null

    /** Like [resolve], but returns every node along the path (excluding the root), so callers get the actual names. */
    suspend fun resolvePath(treeUri: Uri, segments: List<String>): List<DocNode>? {
        var node = root(treeUri) ?: return null
        val path = mutableListOf<DocNode>()
        for (segment in segments) {
            if (!node.isDirectory) return null
            val children = listChildren(treeUri, node)
            node = children.firstOrNull { it.name == segment }
                ?: children.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
            path += node
        }
        return path
    }

    private fun android.database.Cursor.toNode(treeUri: Uri): DocNode {
        val id = getString(0)
        return DocNode(
            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
            documentId = id,
            name = getString(1) ?: "",
            mimeType = getString(2),
            lastModified = if (isNull(3)) 0 else getLong(3),
            size = if (isNull(4)) 0 else getLong(4),
        )
    }

    private companion object {
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
    }
}
