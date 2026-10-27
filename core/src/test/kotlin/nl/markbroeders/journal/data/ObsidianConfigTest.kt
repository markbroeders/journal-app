package nl.markbroeders.journal.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObsidianConfigTest {

    private fun folder(json: String?) = ObsidianConfig.attachmentFolder(json)

    @Test
    fun `fixture vault uses a vault-relative folder`() {
        val json = Fixtures.vaultFile(".obsidian/app.json")
        assertEquals(AttachmentFolder.InVault("x - attachments"), folder(json))
    }

    @Test
    fun `missing file or key falls back to Attachments`() {
        assertEquals(AttachmentFolder.InVault("Attachments"), folder(null))
        assertEquals(AttachmentFolder.InVault("Attachments"), folder("""{"showInlineTitle": false}"""))
        assertEquals(AttachmentFolder.InVault("Attachments"), folder("not json"))
    }

    @Test
    fun `root and note-relative settings`() {
        assertEquals(AttachmentFolder.VaultRoot, folder("""{"attachmentFolderPath": "/"}"""))
        assertEquals(AttachmentFolder.VaultRoot, folder("""{"attachmentFolderPath": ""}"""))
        assertEquals(AttachmentFolder.NextToNote(""), folder("""{"attachmentFolderPath": "./"}"""))
        assertEquals(AttachmentFolder.NextToNote("img"), folder("""{"attachmentFolderPath": "./img"}"""))
        assertEquals(AttachmentFolder.NextToNote("a/b"), folder("""{"attachmentFolderPath": "./a/b/"}"""))
    }

    @Test
    fun `nested vault path and escapes`() {
        assertEquals(AttachmentFolder.InVault("Media/Photos"), folder("""{"attachmentFolderPath":"Media/Photos/"}"""))
        assertEquals(AttachmentFolder.InVault("Foto's \"oud\""), folder("""{"attachmentFolderPath": "Foto's \"oud\""}"""))
        assertEquals(AttachmentFolder.InVault("Bijlagen/é"), folder("""{"attachmentFolderPath": "Bijlagen/é"}"""))
    }

    @Test
    fun `embed targets become file names`() {
        assertEquals("2026-10-23-2011-1.jpg", Embeds.fileName("2026-10-23-2011-1.jpg"))
        assertEquals("photo.jpg", Embeds.fileName("x - attachments/photo.jpg"))
        assertEquals("photo.jpg", Embeds.fileName("photo.jpg|300"))
        assertEquals("my photo.jpg", Embeds.fileName("Attachments/my%20photo.jpg"))
        assertEquals("a+b.png", Embeds.fileName("a+b.png"))
        assertNull(Embeds.fileName("https://example.com/x.jpg"))
    }

    @Test
    fun `videos are recognised`() {
        assertTrue(Embeds.isVideo("clip.MP4"))
        assertFalse(Embeds.isVideo("photo.jpg"))
    }
}
