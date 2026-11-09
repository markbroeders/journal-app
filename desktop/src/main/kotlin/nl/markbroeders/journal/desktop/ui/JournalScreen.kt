package nl.markbroeders.journal.desktop.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import nl.markbroeders.journal.desktop.data.DEFAULT_SIDEBAR_WIDTH
import java.awt.Cursor
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markbroeders.journal.desktop.Platform
import nl.markbroeders.journal.desktop.ui.components.LocalMedia
import nl.markbroeders.journal.desktop.ui.editor.EditorPane
import nl.markbroeders.journal.desktop.ui.theme.LocalJournalColors
import java.nio.file.Files
import java.nio.file.Path

/** The vault is open: timeline on the left, the entry or editor on the right, and the dialogs. */
@Composable
fun JournalScreen(controller: AppController) {
    val state by controller.index.state.collectAsState()
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalMedia provides state) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = maxWidth
        val saved by controller.settings.settings.collectAsState()
        // While dragging the width lives here; it's saved when the drag ends.
        var dragged by remember { mutableStateOf<Dp?>(null) }
        val maxSidebar = (total - MIN_DETAIL_WIDTH).coerceAtLeast(MIN_SIDEBAR_WIDTH)
        val sidebarWidth = (dragged ?: saved.sidebarWidth.dp).coerceIn(MIN_SIDEBAR_WIDTH, maxSidebar)
        Row(Modifier.fillMaxSize()) {
            Sidebar(controller, state, Modifier.width(sidebarWidth).fillMaxHeight())
            ColumnDivider(
                onDrag = { delta -> dragged = ((dragged ?: sidebarWidth) + delta).coerceIn(MIN_SIDEBAR_WIDTH, maxSidebar) },
                onDragEnd = {
                    dragged?.let { width -> controller.settings.update { it.copy(sidebarWidth = width.value.roundToInt().toFloat()) } }
                    dragged = null
                },
                onReset = { controller.settings.update { it.copy(sidebarWidth = DEFAULT_SIDEBAR_WIDTH) } },
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (val detail = controller.detail) {
                    Detail.None -> EmptyPane(controller)
                    is Detail.View -> EntryPane(controller, state, detail.date, detail.key)
                    is Detail.Edit -> EditorPane(
                        detail.session,
                        onCancel = controller::cancelEditing,
                        onAddPhotos = {
                            scope.launch {
                                val home = Path.of(System.getProperty("user.home"))
                                val start = home.resolve("Pictures").takeIf { Files.isDirectory(it) } ?: home
                                detail.session.addPhotos(Platform.pickImages("Add photos", start))
                            }
                        },
                    )
                }
            }
        }
        }
    }

    if (controller.pendingLeave != null) {
        AlertDialog(
            onDismissRequest = { controller.resolveLeave(null) },
            title = { Text("Save this entry?") },
            text = { Text("You have changes that aren't saved yet.") },
            confirmButton = { TextButton(onClick = { controller.resolveLeave(true) }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { controller.resolveLeave(false) }) { Text("Discard") }
                    TextButton(onClick = { controller.resolveLeave(null) }) { Text("Keep editing") }
                }
            },
        )
    }
    when (val deletion = controller.deletion) {
        is Deletion.AskDeleteFile -> AlertDialog(
            onDismissRequest = controller::dismissDeletion,
            title = { Text("Delete the note file too?") },
            text = { Text("This was the last entry of ${deletion.date}. The note file would be empty. Delete the file as well?") },
            confirmButton = { TextButton(onClick = { controller.delete(deletion.date, deletion.key, deletion.raw, true) }) { Text("Delete file") } },
            dismissButton = { TextButton(onClick = { controller.delete(deletion.date, deletion.key, deletion.raw, false) }) { Text("Keep empty file") } },
        )
        is Deletion.Failed -> AlertDialog(
            onDismissRequest = controller::dismissDeletion,
            title = { Text("Not deleted") },
            text = { Text(deletion.message) },
            confirmButton = { TextButton(onClick = controller::dismissDeletion) { Text("OK") } },
        )
        null -> {}
    }
    if (controller.showSettings) {
        val settings by controller.settings.settings.collectAsState()
        SettingsDialog(settings, onDismiss = { controller.showSettings = false }) { next ->
            controller.showSettings = false
            if (next.vaultSettings != settings.vaultSettings) controller.leaveEditor { controller.settings.update { next } }
            else controller.settings.update { next }
        }
    }
}

/** Ctrl+N new entry, Ctrl+S save, Ctrl+E edit, Ctrl+F search, Esc cancel editing. */
fun shortcut(event: KeyEvent, controller: AppController): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (controller.pendingLeave != null || controller.deletion != null || controller.showSettings) return false
    val editing = controller.detail is Detail.Edit
    return when {
        event.isCtrlPressed && event.key == Key.N -> { controller.newEntry(); true }
        event.isCtrlPressed && event.key == Key.S && editing -> { controller.saveEditing(); true }
        event.isCtrlPressed && event.key == Key.E && controller.detail is Detail.View -> { controller.editShown(); true }
        event.isCtrlPressed && event.key == Key.F -> { controller.focusSearch(); true }
        event.key == Key.Escape && editing -> { controller.cancelEditing(); true }
        else -> false
    }
}

private val MIN_SIDEBAR_WIDTH = 280.dp
private val MIN_DETAIL_WIDTH = 420.dp

/**
 * The hairline between the timeline and the entry, with a wider invisible grip: drag it to resize
 * the timeline, double-click it for the default width.
 */
@Composable
private fun ColumnDivider(onDrag: (Dp) -> Unit, onDragEnd: () -> Unit, onReset: () -> Unit) {
    val density = LocalDensity.current
    val hovered = remember { MutableInteractionSource() }
    val active by hovered.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxHeight()
            .width(9.dp)
            .testTag("column-divider")
            .hoverable(hovered)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { px -> onDrag(with(density) { px.toDp() }) },
                onDragStarted = { dragging = true },
                onDragStopped = {
                    dragging = false
                    onDragEnd()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(
            thickness = if (active || dragging) 3.dp else 1.dp,
            color = if (active || dragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else LocalJournalColors.current.line,
        )
    }
}
