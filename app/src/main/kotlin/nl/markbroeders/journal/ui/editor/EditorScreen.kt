package nl.markbroeders.journal.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markbroeders.journal.ui.components.MetaChip
import nl.markbroeders.journal.ui.components.dayAndMonth
import nl.markbroeders.journal.ui.components.longDate
import nl.markbroeders.journal.ui.components.moodIcon
import nl.markbroeders.journal.ui.components.moodName
import nl.markbroeders.journal.ui.components.weatherIcon
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import nl.markbroeders.journal.ui.theme.reading
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Markdown editor, full screen or (for a new entry, [asSheet]) as a sheet over the screen below,
 * with an expand button ([onExpand]) to go full screen. Back, Save and tapping beside the sheet all
 * save; nothing is lost by leaving. [onClosed] is called once the entry is saved (or there was
 * nothing to save).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onClosed: () -> Unit,
    /** Open the camera as soon as the entry is ready (the widget's photo button). */
    startWithCamera: Boolean = false,
    asSheet: Boolean = false,
    onExpand: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { viewModel.saveAndClose() }

    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var editTags by rememberSaveable { mutableStateOf(false) }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS),
    ) { uris -> viewModel.addPhotos(uris) }
    val context = LocalContext.current
    // Where the camera app writes; saved so it survives our process being killed meanwhile.
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile?.let(::File)
        cameraFile = null
        if (ok && file != null) {
            viewModel.addPhotos(listOf(Uri.fromFile(file))) { file.delete() }
        } else {
            file?.delete()
        }
    }

    val openCamera = {
        val file = newCameraFile(context)
        cameraFile = file.path
        try {
            takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (_: ActivityNotFoundException) {
            cameraFile = null
            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }
    var cameraStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (startWithCamera && !cameraStarted && !state.loading) {
            cameraStarted = true
            openCamera()
        }
    }

    val content = @Composable { modifier: Modifier ->
        EditorContent(
            state = state,
            viewModel = viewModel,
            asSheet = asSheet,
            onPickDate = { pickDate = true },
            onPickTime = { pickTime = true },
            onTags = { editTags = true },
            onGallery = {
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = openCamera,
            onExpand = onExpand,
            modifier = modifier,
        )
    }
    if (asSheet) {
        EditorSheet(onDismiss = viewModel::saveAndClose) { content(Modifier.fillMaxSize()) }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = viewModel::saveAndClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and close")
                        }
                    },
                    title = {
                        // A new entry shows its date and time as chips below instead.
                        if (!state.isNew) {
                            Column {
                                Text(
                                    longDate(state.date),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    state.time?.toString() ?: "Day note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = journalTopBarColors(),
                )
            },
        ) { padding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            )
        }
    }

    if (pickDate) {
        DateDialog(state.date, onDismiss = { pickDate = false }) {
            viewModel.onDateChange(it)
            pickDate = false
        }
    }
    if (pickTime && state.time != null) {
        TimeDialog(state.time!!, onDismiss = { pickTime = false }) {
            viewModel.onTimeChange(it)
            pickTime = false
        }
    }
    if (editTags) {
        TagsDialog(state.tags, onDismiss = { editTags = false }) {
            viewModel.onTagsChange(it)
            editTags = false
        }
    }
    if (state.conflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Entry changed elsewhere") },
            text = {
                Text(
                    "This entry was changed in the file while you were editing (in Obsidian or by sync). " +
                        "Keep your version, or discard your changes and keep the other one?",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.saveAndClose(force = true) }) { Text("Keep mine") } },
            dismissButton = { TextButton(onClick = viewModel::discard) { Text("Discard mine") } },
        )
    }
    state.photoError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPhotoError,
            title = { Text("Couldn't add the photo") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::dismissPhotoError) { Text("OK") } },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Couldn't save") },
            text = { Text("$error\n\nYour text is still here. Try again, or leave without saving.") },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Keep editing") } },
            dismissButton = { TextButton(onClick = viewModel::discard) { Text("Discard my changes") } },
        )
    }
}

private const val MAX_PHOTOS = 10

/** A fresh file in the cache for the camera app to write into (shared via FileProvider). */
private fun newCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(dir, "photo-${System.currentTimeMillis()}.jpg")
}

/**
 * The text with its cursor. The view model owns the text; the field keeps the selection and
 * takes text and cursor from the view model whenever it replaces the body ([EditorUiState.bodyRevision]).
 */
@Composable
private fun BodyField(
    state: EditorUiState,
    onChange: (String, Int) -> Unit,
    autoFocus: Boolean,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    var value by remember { mutableStateOf(TextFieldValue(state.body, TextRange(state.cursor))) }
    LaunchedEffect(state.bodyRevision) {
        if (value.text != state.body || value.selection.end != state.cursor) {
            value = TextFieldValue(state.body, TextRange(state.cursor))
        }
    }
    val text = value.text
    BasicTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it.text, it.selection.end)
        },
        textStyle = MaterialTheme.typography.bodyLarge.reading().copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        "Start writing…",
                        style = MaterialTheme.typography.bodyLarge.reading(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * A sheet from the bottom over a dimmed screen, about two thirds high and up to just below the
 * status bar while the keyboard is open. Tapping the dimmed part calls [onDismiss].
 */
@Composable
private fun EditorSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = maxHeight
        AnimatedVisibility(shown, enter = fadeIn()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = "Save and close", onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            shown,
            enter = slideInVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            ) {
                Column(Modifier.height(full * 0.66f)) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .align(Alignment.CenterHorizontally),
                    )
                    content()
                }
            }
        }
    }
}

/** Date chips (new entries), the text, the mood faces and the toolbar with Save. */
@Composable
private fun EditorContent(
    state: EditorUiState,
    viewModel: EditorViewModel,
    asSheet: Boolean,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onTags: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        if (state.saving || state.addingPhotos) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.isNew) {
            WhenChips(state.date, state.time, onDate = viewModel::onDateChange, onPickDate = onPickDate, onPickTime = onPickTime)
        }
        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.missing -> Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "This entry no longer exists in the note. It may have been changed or removed in Obsidian.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> BodyField(
                state = state,
                onChange = viewModel::onBodyChange,
                autoFocus = state.isNew,
                modifier = Modifier.weight(1f),
            )
        }
        MoodRow(state.mood, onMood = viewModel::onMoodChange)
        HorizontalDivider(Modifier.padding(top = 10.dp), color = LocalJournalColors.current.line)
        Toolbar(
            state = state,
            onStar = { viewModel.onStarredChange(!state.starred) },
            onTags = onTags,
            onGallery = onGallery,
            onCamera = onCamera,
            onExpand = onExpand.takeIf { asSheet },
            onSave = viewModel::saveAndClose,
        )
    }
}

/** Today / Yesterday / another date, and the time: the selected chip is tinted blue. */
@Composable
private fun WhenChips(
    date: LocalDate,
    time: LocalTime?,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val today = LocalDate.now()
    val other = date != today && date != today.minusDays(1)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("Today", selected = date == today) { onDate(today) }
        Chip("Yesterday", selected = date == today.minusDays(1)) { onDate(today.minusDays(1)) }
        Chip(if (other) dayAndMonth(date) else null, selected = other, icon = Icons.Outlined.CalendarMonth, iconLabel = "Pick a date", onClick = onPickDate)
        if (time != null) Chip(time.toString(), selected = false, icon = Icons.Outlined.Schedule, iconLabel = "Change time", onClick = onPickTime)
    }
}

/** A pill like Vink's date chips: tinted when [selected], outlined otherwise. */
@Composable
private fun Chip(
    label: String?,
    selected: Boolean,
    icon: ImageVector? = null,
    iconLabel: String? = null,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(19.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (selected) accent else LocalJournalColors.current.body,
        border = if (selected) null else BorderStroke(1.dp, LocalJournalColors.current.line),
        modifier = Modifier.heightIn(min = 38.dp),
    ) {
        Row(
            Modifier.padding(horizontal = if (label == null) 11.dp else 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = if (label == null) iconLabel else null, modifier = Modifier.size(17.dp))
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Five faces; tapping the chosen one again clears the mood. */
@Composable
private fun MoodRow(mood: Int?, onMood: (Int?) -> Unit) {
    val colors = LocalJournalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (m in 1..5) {
            val on = mood == m
            Surface(
                onClick = { onMood(if (on) null else m) },
                shape = RoundedCornerShape(12.dp),
                color = if (on) colors.amberSoft else colors.chip,
                contentColor = if (on) colors.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (on) BorderStroke(1.5.dp, colors.amber) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .semantics { selected = on },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(moodIcon(m), contentDescription = "Mood: ${moodName(m)}", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    state: EditorUiState,
    onStar: () -> Unit,
    onTags: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    /** Null when already full screen. */
    onExpand: (() -> Unit)?,
    onSave: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onGallery, enabled = !state.addingPhotos) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add photos", tint = muted)
        }
        IconButton(onClick = onCamera, enabled = !state.addingPhotos) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = "Take a photo", tint = muted)
        }
        if (onExpand != null) {
            IconButton(onClick = onExpand) {
                Icon(Icons.Outlined.OpenInFull, contentDescription = "Full screen", tint = muted)
            }
        }
        // A locked quick capture doesn't show (or change) the day's star and tags. The sheet has no
        // room for the tags' text; its tag button is blue when there are tags.
        if (!state.writeOnly) DayButtons(state, onStar, onTags, showTags = onExpand == null)
        Spacer(Modifier.weight(1f))
        // Location and weather are kept as they are, and shown when editing full screen.
        val info = state.location?.let { Icons.Outlined.Place to it.substringBefore('(').trim() }
            ?: state.weather?.let { weatherIcon(it) to it.substringBefore(',').trim() }
        if (onExpand == null) info?.let { (icon, text) -> MetaChip(icon, text, Modifier.padding(end = 8.dp)) }
        Button(
            onClick = onSave,
            enabled = !state.saving,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 22.dp),
        ) {
            Text("Save", fontWeight = FontWeight.Bold)
        }
    }
}

/** The day's star and tags (frontmatter), with the tags listed next to them if [showTags]. */
@Composable
private fun RowScope.DayButtons(state: EditorUiState, onStar: () -> Unit, onTags: () -> Unit, showTags: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onStar) {
        if (state.starred) {
            Icon(Icons.Rounded.Star, contentDescription = "Unstar day", tint = LocalJournalColors.current.amber)
        } else {
            Icon(Icons.Rounded.StarBorder, contentDescription = "Star day", tint = muted)
        }
    }
    IconButton(onClick = onTags) {
        Icon(
            Icons.Outlined.Sell,
            contentDescription = "Day tags",
            tint = if (state.tags.isEmpty()) muted else MaterialTheme.colorScheme.primary,
        )
    }
    if (showTags && state.tags.isNotEmpty()) {
        Text(
            state.tags.joinToString(" ") { "#$it" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = picker)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = picker) },
    )
}

@Composable
private fun TagsDialog(tags: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf(tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Saved in the note's frontmatter. #tags you type in the text work too.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("journal, family") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
