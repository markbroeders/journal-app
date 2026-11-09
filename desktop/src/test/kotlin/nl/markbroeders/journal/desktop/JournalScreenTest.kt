package nl.markbroeders.journal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import nl.markbroeders.journal.desktop.data.JournalIndex
import nl.markbroeders.journal.desktop.data.SettingsStore
import nl.markbroeders.journal.desktop.data.VaultSettings
import nl.markbroeders.journal.desktop.ui.AppController
import nl.markbroeders.journal.desktop.ui.JournalScreen
import nl.markbroeders.journal.desktop.ui.shortcut
import nl.markbroeders.journal.desktop.ui.theme.DesktopRoot
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.prefs.Preferences
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives the real journal screen against a temporary vault. */
@OptIn(ExperimentalTestApi::class)
class JournalScreenTest {

    private val vault: Path = Files.createTempDirectory("vault")
    private val prefs = Preferences.userRoot().node("nl/markbroeders/journal-test")
    private val today = LocalDate.now()
    private val ridge = vault.resolve("Journal/2026/2026-10-25.md")

    init {
        vault.resolve(".obsidian").createDirectories()
        vault.resolve(".obsidian/app.json").writeText("""{"attachmentFolderPath": "Attachments"}""")
        vault.resolve("Attachments").createDirectories()
        vault.resolve("Attachments/ridge.jpg").writeBytes(photo())
        ridge.parent.createDirectories()
        ridge.writeText(
            "---\ntags: [hiking]\nstarred: true\n---\n\n## 13:40\nmood:: 5\n\nRidge walk\nFinally did the **ridge walk**.\n\n" +
                "![[ridge.jpg]]\n\n> The best views come after the hardest climb.\n\n- [x] Thermos\n- [ ] Gloves ==never again==\n",
        )
        vault.resolve("Journal/2026/2026-10-24.md").writeText("Movie night. Fell asleep halfway through, as usual.\n")
    }

    @AfterTest
    fun cleanUp() {
        vault.toFile().deleteRecursively()
        runCatching { prefs.removeNode() }
    }

    private fun DesktopComposeUiTest.app(dark: Boolean = false): AppController {
        lateinit var controller: AppController
        setContent {
            val scope = rememberCoroutineScope()
            controller = remember {
                val index = JournalIndex(scope).also { it.open(VaultSettings(vault, "Journal")) }
                AppController(SettingsStore(prefs), index, scope)
            }
            DesktopRoot(dark = dark) {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { shortcut(it, controller) }) { JournalScreen(controller) }
            }
        }
        waitForText("Ridge walk")
        return controller
    }

    private fun SemanticsNodeInteractionsProvider.exists(text: String, substring: Boolean = false) =
        onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { exists(text, substring) }

    private fun DesktopComposeUiTest.screenshot(name: String) {
        val dir = System.getenv("JOURNAL_SCREENSHOTS") ?: return
        waitForIdle()
        val bitmap = captureToImage().asSkiaBitmap()
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
        Path.of(dir).createDirectories().resolve("$name.png").writeBytes(png)
    }

    @Test
    fun `shows an entry formatted`() = runDesktopComposeUiTest(1240, 820) {
        app()
        onNodeWithText("Ridge walk").performClick()
        waitForText("The best views come after the hardest climb.")
        assertTrue(exists("Great"))
        screenshot("entry")
    }

    @Test
    fun `dark mode shows the entry in light text`() = runDesktopComposeUiTest(1240, 820) {
        app(dark = true)
        onNodeWithText("Ridge walk").performClick()
        waitForText("The best views come after the hardest climb.")
        screenshot("entry-dark")
    }

    @Test
    fun `writes a new entry into today's note`() = runDesktopComposeUiTest(1240, 820) {
        app()
        onNodeWithContentDescription("New entry (Ctrl+N)").performClick()
        waitForText("Start writing…")
        onNodeWithTag("editor-text").performTextInput("Hello from the desktop. #laptop")
        onNodeWithContentDescription("Mood: Good").performClick()
        screenshot("editor")
        onNodeWithText("Save").performClick()
        val note = vault.resolve("Journal/${today.year}/$today.md")
        waitUntil(timeoutMillis = 10_000) { note.exists() }
        val text = note.readText()
        assertTrue(Regex("""## \d\d:\d\d\nmood:: 4\n\nHello from the desktop\. #laptop\n""").containsMatchIn(text), text)
        // The saved entry is shown, and the timeline picked it up.
        waitForText("#laptop", substring = true)
        screenshot("saved")
    }

    @Test
    fun `a conflicting edit asks, and keep mine wins`() = runDesktopComposeUiTest(1240, 820) {
        app()
        onNodeWithText("Ridge walk").performClick()
        onNodeWithContentDescription("Edit (Ctrl+E)").performClick()
        waitForText("Edit entry")
        waitUntil(timeoutMillis = 10_000) { exists("ridge walk", substring = true) }
        onNodeWithTag("editor-text").performTextInput("Mine. ")
        ridge.writeText(ridge.readText().replace("Finally did", "Changed in Obsidian:"))
        onNodeWithText("Save").performClick()
        waitForText("Entry changed elsewhere")
        screenshot("conflict")
        onNodeWithText("Keep mine").performClick()
        waitUntil(timeoutMillis = 10_000) { "Mine. " in ridge.readText() }
        val text = ridge.readText()
        assertFalse("Changed in Obsidian" in text, text)
        assertTrue(text.startsWith("---\ntags: [hiking]\nstarred: true\n---\n"), text)
    }

    @Test
    fun `leaving unsaved text asks first, and discard writes nothing`() = runDesktopComposeUiTest(1240, 820) {
        app()
        onNodeWithContentDescription("New entry (Ctrl+N)").performClick()
        waitForText("Start writing…")
        onNodeWithTag("editor-text").performTextInput("Not keeping this.")
        onNodeWithText("Movie night", substring = true).performClick()
        waitForText("Save this entry?")
        onNodeWithText("Discard").performClick()
        waitForText("Movie night. Fell asleep halfway through, as usual.")
        assertFalse(vault.resolve("Journal/${today.year}/$today.md").exists())
    }

    @Test
    fun `search filters the timeline`() = runDesktopComposeUiTest(1240, 820) {
        app()
        onNodeWithTag("search").performTextInput("#hiking")
        waitForText("1 entry")
        assertFalse(exists("Movie night", substring = true))
        screenshot("search")
    }

    @Test
    fun `dragging the divider resizes the timeline and remembers it`() = runDesktopComposeUiTest(1240, 820) {
        app()
        val before = onNodeWithTag("search").fetchSemanticsNode().size.width
        onNodeWithTag("column-divider").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(150f, 0f))
            moveBy(Offset(50f, 0f))
            release()
        }
        waitForIdle()
        val after = onNodeWithTag("search").fetchSemanticsNode().size.width
        assertEquals(200, after - before)
        assertEquals(600f, prefs.getFloat("sidebarWidth", 0f))
        screenshot("wide-sidebar")
        // It can't squeeze the entry below its minimum.
        onNodeWithTag("column-divider").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(900f, 0f))
            release()
        }
        waitForIdle()
        assertEquals(1240f - 420f, prefs.getFloat("sidebarWidth", 0f))
    }

    @Test
    fun `an edit in Obsidian shows up by itself`() = runDesktopComposeUiTest(1240, 820) {
        app()
        vault.resolve("Journal/2026/2026-10-24.md").writeText("Movie night, rewritten in Obsidian.\n")
        waitForText("Movie night, rewritten in Obsidian.")
        assertEquals(false, exists("Fell asleep", substring = true))
    }

    /** A small landscape-ish JPEG. */
    private fun photo(): ByteArray {
        val surface = Surface.makeRasterN32Premul(400, 300)
        surface.canvas.clear(Color.makeRGB(150, 196, 232))
        surface.canvas.drawRect(Rect.makeXYWH(0f, 180f, 400f, 120f), Paint().apply { color = Color.makeRGB(63, 94, 72) })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)!!.bytes
    }
}
