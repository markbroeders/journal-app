package nl.markbroeders.journal.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** A document (file or folder) inside the vault tree, with the metadata from one query row. */
data class DocNode(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val lastModified: Long,
    val size: Long,
    /** The vault tree this document was reached through (needed to list children). */
    val treeUri: Uri,
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

    /** Reads a document as UTF-8 text. Throws if it can't be opened. */
    suspend fun readText(node: DocNode): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(node.uri) ?: throw IOException("Can't open ${node.name}")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Fresh metadata for [node] (e.g. its current last-modified time), or null if it's gone. */
    suspend fun stat(node: DocNode): DocNode? = withContext(Dispatchers.IO) {
        try {
            resolver.query(node.uri, PROJECTION, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.toNode(node.treeUri) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun writeText(node: DocNode, text: String) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(node.uri, "wt") ?: throw IOException("Can't write ${node.name}")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    suspend fun writeBytes(node: DocNode, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(node.uri, "wt") ?: throw IOException("Can't write ${node.name}")
        stream.use { it.write(bytes) }
    }

    /** Obsidian's attachment folder setting, from `.obsidian/app.json` (default if unreadable). */
    suspend fun attachmentFolder(treeUri: Uri): AttachmentFolder {
        val json = resolve(treeUri, listOf(".obsidian", "app.json"))
            ?.let { runCatching { readText(it) }.getOrNull() }
        return ObsidianConfig.attachmentFolder(json)
    }

    /** Creates an empty file. Throws if the provider picks a different name (e.g. it already exists). */
    suspend fun createFile(parent: DocNode, name: String): DocNode = create(parent, OCTET_STREAM, name)

    suspend fun createDirectory(parent: DocNode, name: String): DocNode = create(parent, Document.MIME_TYPE_DIR, name)

    suspend fun delete(node: DocNode) = withContext(Dispatchers.IO) {
        if (!DocumentsContract.deleteDocument(resolver, node.uri)) throw IOException("Can't delete ${node.name}")
    }

    /** Renames [node]; throws if the provider picks a different name. */
    suspend fun rename(node: DocNode, name: String): DocNode = withContext(Dispatchers.IO) {
        val uri = DocumentsContract.renameDocument(resolver, node.uri, name) ?: node.uri
        val renamed = stat(node.copy(uri = uri, documentId = DocumentsContract.getDocumentId(uri)))
            ?: throw IOException("Renamed ${node.name} but can't find it")
        if (renamed.name != name) throw IOException("Renamed ${node.name} to ${renamed.name} instead of $name")
        renamed
    }

    /** Finds a direct child by exact name. */
    suspend fun child(parent: DocNode, name: String): DocNode? =
        listChildren(parent.treeUri, parent).firstOrNull { it.name == name }

    /** Resolves [segments], creating missing folders. */
    suspend fun ensureDirectory(treeUri: Uri, segments: List<String>): DocNode {
        var node = root(treeUri) ?: throw IOException("Vault folder is not accessible")
        for (segment in segments) {
            val children = listChildren(treeUri, node)
            node = children.firstOrNull { it.name == segment }
                ?: children.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: createDirectory(node, segment)
            if (!node.isDirectory) throw IOException("${node.name} is not a folder")
        }
        return node
    }

    private suspend fun create(parent: DocNode, mimeType: String, name: String): DocNode = withContext(Dispatchers.IO) {
        val uri = DocumentsContract.createDocument(resolver, parent.uri, mimeType, name)
            ?: throw IOException("Can't create $name")
        val created = stat(DocNode(uri, DocumentsContract.getDocumentId(uri), name, mimeType, 0, 0, parent.treeUri))
            ?: throw IOException("Created $name but can't find it")
        if (created.name != name) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw IOException("Couldn't create $name (got ${created.name})")
        }
        created
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
            treeUri = treeUri,
        )
    }

    private companion object {
        // Keeps providers from adding an extension based on the MIME type (".md.txt").
        const val OCTET_STREAM = "application/octet-stream"

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
