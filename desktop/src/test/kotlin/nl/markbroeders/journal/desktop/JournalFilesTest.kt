package nl.markbroeders.journal.desktop

import nl.markbroeders.journal.data.DayEdit
import nl.markbroeders.journal.data.EntryChange
import nl.markbroeders.journal.data.EntryKey
import nl.markbroeders.journal.data.NoteEdit
import nl.markbroeders.journal.desktop.data.JournalFiles
import nl.markbroeders.journal.desktop.data.NoteListing
import nl.markbroeders.journal.desktop.data.SaveResult
import nl.markbroeders.journal.desktop.data.VaultSettings
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JournalFilesTest {

    private val vault: Path = Files.createTempDirectory("vault")
    private val files = JournalFiles(VaultSettings(vault, "Journal"))
    private val date = LocalDate.of(2026, 10, 23)
    private val note = vault.resolve("Journal/2026/2026-10-23.md")

    @AfterTest
    fun cleanUp() {
        vault.toFile().deleteRecursively()
    }

    private fun write(text: String) {
        note.parent.createDirectories()
        note.writeText(text)
    }

    @Test
    fun `adding to a missing day creates the year folder and the note`() {
        val result = files.save(date, NoteEdit(EntryChange.Add(LocalTime.of(8, 42), "Morning.")))
        assertEquals(SaveResult.Saved(EntryKey(LocalTime.of(8, 42))), result)
        assertEquals("## 08:42\n\nMorning.\n", note.readText())
        assertFalse(note.resolveSibling(".2026-10-23.md.tmp").exists())
    }

    @Test
    fun `adding merges into what is on disk and keeps everything else`() {
        val original = "---\ndate: 2026-10-23\nweird: [a, b]\n---\nWritten in Obsidian.\n\n## 20:11\nEvening.\n"
        write(original)
        files.save(date, NoteEdit(EntryChange.Add(LocalTime.of(9, 0), "Later added, earlier time.")))
        val text = note.readText()
        assertTrue(text.startsWith("---\ndate: 2026-10-23\nweird: [a, b]\n---\nWritten in Obsidian.\n"))
        assertTrue(text.indexOf("## 09:00") < text.indexOf("## 20:11"))
    }

    @Test
    fun `editing an entry that changed on disk is a conflict until forced`() {
        write("## 08:00\nFirst.\n")
        val opened = files.loadDay(date).entry(EntryKey(LocalTime.of(8, 0)))!!.raw
        write("## 08:00\nChanged in Obsidian.\n")
        val edit = NoteEdit(EntryChange.Edit(EntryKey(LocalTime.of(8, 0)), opened, "Mine.", emptyList()))
        assertEquals(SaveResult.Conflict, files.save(date, edit))
        assertEquals("## 08:00\nChanged in Obsidian.\n", note.readText())
        assertIs<SaveResult.Saved>(files.save(date, edit, force = true))
        assertEquals("## 08:00\nMine.\n", note.readText())
    }

    @Test
    fun `deleting the last entry asks before deleting the file`() {
        write("## 08:00\nOnly one.\n")
        val raw = files.loadDay(date).entry(EntryKey(LocalTime.of(8, 0)))!!.raw
        val delete = NoteEdit(EntryChange.Delete(EntryKey(LocalTime.of(8, 0)), raw))
        assertEquals(SaveResult.NoteWouldBeBlank, files.save(date, delete))
        assertTrue(note.exists())
        files.save(date, delete, deleteBlankFile = true)
        assertFalse(note.exists())
    }

    @Test
    fun `starring only touches the frontmatter`() {
        write("Untimed text.\n")
        files.save(date, NoteEdit(EntryChange.None, DayEdit(starred = true)))
        assertEquals("---\nstarred: true\n---\nUntimed text.\n", note.readText())
    }

    @Test
    fun `listing finds notes in their year folder only, newest first`() {
        write("x\n")
        vault.resolve("Journal/2026/2026-10-24.md").writeText("y\n")
        vault.resolve("Journal/2026/2025-01-01.md").writeText("wrong folder\n")
        vault.resolve("Journal/2026/notes.md").writeText("not a day\n")
        val listing = files.listNotes()
        assertIs<NoteListing.Files>(listing)
        assertEquals(listOf(LocalDate.of(2026, 10, 24), date), listing.files.map { it.date })
    }

    @Test
    fun `a missing journal folder is reported`() {
        assertEquals(NoteListing.FolderMissing, files.listNotes())
    }

    @Test
    fun `listing recovers a temp file whose note is gone and drops one whose note exists`() {
        note.parent.createDirectories()
        note.resolveSibling(".2026-10-23.md.tmp").writeText("recovered\n")
        vault.resolve("Journal/2026/2026-10-24.md").writeText("kept\n")
        vault.resolve("Journal/2026/.2026-10-24.md.tmp").writeText("stale\n")
        val listing = files.listNotes() as NoteListing.Files
        assertEquals(2, listing.files.size)
        assertEquals("recovered\n", note.readText())
        assertEquals("kept\n", vault.resolve("Journal/2026/2026-10-24.md").readText())
        assertFalse(vault.resolve("Journal/2026/.2026-10-24.md.tmp").exists())
    }

    @Test
    fun `attachments follow the Obsidian setting and earlier folders win`() {
        vault.resolve(".obsidian").createDirectories()
        vault.resolve(".obsidian/app.json").writeText("""{"attachmentFolderPath": "x - attachments"}""")
        vault.resolve("x - attachments/2026").createDirectories()
        vault.resolve("x - attachments/2026/Photo.JPG").writeText("a")
        vault.resolve("photo.jpg").writeText("b")
        vault.resolve("notes.md").writeText("c")
        val (found, _) = files.listAttachments(emptyList())
        assertEquals(setOf("photo.jpg"), found.keys)
        assertEquals(vault.resolve("x - attachments/2026/Photo.JPG"), found["photo.jpg"])
        assertEquals(vault.resolve("x - attachments"), files.attachmentDir(date))
    }
}
