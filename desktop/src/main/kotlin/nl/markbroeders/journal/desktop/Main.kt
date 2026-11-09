package nl.markbroeders.journal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import nl.markbroeders.journal.desktop.data.Appearance
import nl.markbroeders.journal.desktop.data.JournalIndex
import nl.markbroeders.journal.desktop.data.SettingsStore
import nl.markbroeders.journal.desktop.ui.AppController
import nl.markbroeders.journal.desktop.ui.JournalScreen
import nl.markbroeders.journal.desktop.ui.shortcut
import nl.markbroeders.journal.desktop.ui.SetupScreen
import nl.markbroeders.journal.desktop.ui.theme.DesktopRoot
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.input.key.onPreviewKeyEvent
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.system.exitProcess
import java.nio.file.Files

fun main() {
    useWaylandIfPossible()
    val store = SettingsStore()
    // Read once at start: the desktop's light/dark preference, for Appearance → System.
    val systemDark = Platform.prefersDark()
    if (Toolkit.getDefaultToolkit().javaClass.name.endsWith("WLToolkit")) {
        waylandMain(store, systemDark)
    } else {
        application {
            val scope = rememberCoroutineScope()
            val controller = remember { AppController(store, JournalIndex(scope), scope) }
            Window(
                onCloseRequest = { controller.leaveEditor(::exitApplication) },
                state = rememberWindowState(size = DpSize(1240.dp, 820.dp)),
                title = "Journal",
                icon = rememberVectorPainter(AppIcon),
            ) {
                window.minimumSize = Dimension(820, 560)
                JournalApp(controller, systemDark)
            }
        }
    }
}

/**
 * In a Wayland session on the JetBrains Runtime, picks its native Wayland toolkit (must happen
 * before anything touches AWT), with the compositor drawing the window frame. Elsewhere Java runs
 * as an X11 app (XWayland), which a fractional scale makes blurry.
 */
private fun useWaylandIfPossible() {
    val wayland = !System.getenv("WAYLAND_DISPLAY").isNullOrEmpty()
    val jbr = System.getProperty("java.vm.vendor").orEmpty().contains("JetBrains")
    // JBR presets awt.toolkit.name to XToolkit; JOURNAL_X11=1 keeps it (e.g. if Wayland misbehaves).
    if (wayland && jbr && System.getenv("JOURNAL_X11") == null) {
        System.setProperty("awt.toolkit.name", "WLToolkit")
        System.setProperty("sun.awt.wl.WindowDecorationStyle", "server")
    }
}

/**
 * On a JetBrains Runtime with its native Wayland toolkit: Compose's own window needs an X11
 * drawing surface, so the UI goes into a Swing frame that Compose paints through Swing's graphics.
 * Being a real Wayland window, it's sharp at fractional scales (XWayland windows are upscaled).
 */
private fun waylandMain(store: SettingsStore, systemDark: Boolean?) {
    System.setProperty("compose.swing.render.on.graphics", "true")
    SwingUtilities.invokeLater {
        val frame = JFrame("Journal")
        var controller: AppController? = null
        val panel = ComposePanel()
        panel.setContent {
            val scope = rememberCoroutineScope()
            val c = remember { AppController(store, JournalIndex(scope), scope) }
            controller = c
            JournalApp(c, systemDark)
        }
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                val exit = {
                    frame.dispose()
                    exitProcess(0)
                }
                controller?.leaveEditor(exit) ?: exit()
            }
        })
        frame.contentPane.add(panel)
        frame.size = Dimension(1240, 820)
        frame.minimumSize = Dimension(820, 560)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

/** The window's content: vault setup or the journal, in the chosen look, with the shortcuts. */
@Composable
private fun JournalApp(controller: AppController, systemDark: Boolean?) {
    val store = controller.settings
    val settings by store.settings.collectAsState()
    DisposableEffect(settings.vaultSettings) {
        controller.index.open(settings.vaultSettings?.takeIf { Files.isDirectory(it.vault) })
        onDispose { }
    }
    val dark = when (settings.appearance) {
        Appearance.System -> systemDark ?: isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { shortcut(it, controller) }) {
        DesktopRoot(dark = dark, serif = settings.serif) {
            if (settings.vaultSettings == null || !Files.isDirectory(settings.vault!!)) {
                SetupScreen(settings) { vault, journal -> store.update { it.copy(vault = vault, journalFolder = journal) } }
            } else {
                JournalScreen(controller)
            }
        }
    }
}
