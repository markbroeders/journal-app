package nl.markbroeders.journal.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markbroeders.journal.data.Appearance
import nl.markbroeders.journal.data.ReminderSettings
import nl.markbroeders.journal.data.ThemeMode
import nl.markbroeders.journal.data.VaultPaths
import nl.markbroeders.journal.lock.isDeviceSecure
import nl.markbroeders.journal.reminder.DailyReminder
import nl.markbroeders.journal.ui.theme.JournalType
import nl.markbroeders.journal.ui.theme.LocalJournalColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Picks the vault root and the journal folder inside it. [onCancel] is null during first-run
 * setup (there is nothing to go back to) and set when changing settings later.
 * [lockEnabled] is null during setup; in settings it shows the app lock switch, which applies
 * right away (turning it on asks to unlock first). [reminder] likewise shows the daily reminder's
 * settings, and [appearance] the theme and reading font, also applied right away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    accessLost: Boolean,
    onDone: () -> Unit,
    onCancel: (() -> Unit)?,
    lockEnabled: Boolean? = null,
    onLockChange: (Boolean) -> Unit = {},
    reminder: ReminderSettings? = null,
    onReminderChange: (ReminderSettings) -> Unit = {},
    appearance: Appearance? = null,
    onAppearanceChange: (Appearance) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onVaultPicked(uri)
    }
    if (onCancel != null) BackHandler(onBack = onCancel)

    val ground = LocalJournalColors.current.ground
    Scaffold(
        containerColor = ground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ground, scrolledContainerColor = ground),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (onCancel == null) "Set up Journal" else "Settings",
                style = JournalType.title,
                modifier = Modifier.padding(start = 4.dp),
            )
            if (accessLost && state.vault == null) {
                Notice(
                    Icons.Filled.Warning,
                    "Journal no longer has access to your vault folder. Choose it again to continue.",
                    isError = true,
                )
            }
            if (onCancel == null) {
                Text(
                    "Journal reads and writes the daily notes in your Obsidian vault. Your notes stay " +
                        "plain Markdown files; nothing is moved or renamed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalJournalColors.current.body,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                VaultSection(state, onPick = { picker.launch(state.vault?.uri) })
                if (state.vault != null) {
                    JournalFolderSection(state, viewModel::onJournalInputChange)
                }
                SaveButton("Continue", enabled = state.canSave) { viewModel.save(onDone) }
            } else {
                VaultRows(state, onPick = { picker.launch(state.vault?.uri) }, onJournalInputChange = viewModel::onJournalInputChange)
                // Theme, lock and reminder apply right away; a different vault or folder needs saving.
                if (state.changed) SaveButton("Save vault settings", enabled = state.canSave) { viewModel.save(onDone) }
            }

            if (appearance != null) AppearanceSection(appearance, onAppearanceChange)
            if (reminder != null || lockEnabled != null) {
                SectionCard("Habits & privacy") {
                    if (reminder != null) ReminderRow(reminder, onReminderChange)
                    if (reminder != null && lockEnabled != null) {
                        HorizontalDivider(Modifier.padding(start = 42.dp), color = LocalJournalColors.current.line)
                    }
                    if (lockEnabled != null) LockRow(lockEnabled, onLockChange)
                }
            }
        }
    }
}

@Composable
private fun VaultSection(state: SetupUiState, onPick: () -> Unit) {
    SectionCard("Obsidian vault") {
        Text(
            "Choose the vault's root folder: the one that contains the .obsidian folder.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val vault = state.vault
        when {
            state.loadingVault -> CircularProgressIndicator(Modifier.size(24.dp))
            vault != null -> {
                Text(vault.name, style = MaterialTheme.typography.titleMedium)
                if (vault.isObsidianVault) {
                    Notice(Icons.Filled.CheckCircle, "Obsidian vault found.")
                } else {
                    Notice(
                        Icons.Filled.Warning,
                        "This folder has no .obsidian folder, so it may not be the vault root. " +
                            "Attachment settings can't be read from it.",
                        isError = true,
                    )
                }
            }
        }
        state.vaultError?.let { Notice(Icons.Filled.Warning, it, isError = true) }
        OutlinedButton(onClick = onPick) {
            Text(if (vault == null) "Choose vault folder" else "Choose a different folder")
        }
    }
}

@Composable
private fun SaveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun JournalFolderSection(state: SetupUiState, onInputChange: (String) -> Unit) {
    SectionCard("Journal folder") {
        Text(
            "Folder inside the vault that holds the year folders (YYYY/YYYY-MM-DD.md).",
            style = MaterialTheme.typography.bodyMedium,
        )
        JournalFolderFields(state, onInputChange)
    }
}

/** The journal folder's path field with its check, and the vault's top folders as suggestions. */
@Composable
private fun JournalFolderFields(state: SetupUiState, onInputChange: (String) -> Unit) {
    val check = state.journalCheck
    val isError = check == JournalCheck.Invalid || check == JournalCheck.NotAFolder ||
        check == JournalCheck.Error
    OutlinedTextField(
        value = state.journalInput,
        onValueChange = onInputChange,
        label = { Text("Path in vault") },
        singleLine = true,
        isError = isError,
        supportingText = { Text(checkMessage(check)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    val folders = state.vault?.folders.orEmpty()
    if (folders.isNotEmpty()) {
        val selected = VaultPaths.normalizeFolder(state.journalInput)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (folder in folders) {
                FilterChip(
                    selected = folder.equals(selected, ignoreCase = true),
                    onClick = { onInputChange(folder) },
                    label = { Text(folder) },
                )
            }
        }
    }
}

/**
 * Settings: the vault and journal folder as two rows, as in Vink. The vault row opens the folder
 * picker, the journal folder row a dialog with the path and suggestions.
 */
@Composable
private fun VaultRows(state: SetupUiState, onPick: () -> Unit, onJournalInputChange: (String) -> Unit) {
    var editingFolder by remember { mutableStateOf<String?>(null) }
    val colors = LocalJournalColors.current
    SectionCard("Vault") {
        val vault = state.vault
        SettingRow(
            Icons.Outlined.Folder,
            colors.violet,
            "Vault",
            when {
                state.loadingVault -> "Loading…"
                vault != null -> vault.name
                else -> "Choose the vault folder"
            },
            onClick = onPick,
        ) { Chevron() }
        if (vault != null && !vault.isObsidianVault) {
            Notice(Icons.Filled.Warning, "This folder has no .obsidian folder, so it may not be the vault root.", isError = true)
        }
        state.vaultError?.let { Notice(Icons.Filled.Warning, it, isError = true) }
        if (vault != null) {
            HorizontalDivider(Modifier.padding(start = 42.dp), color = colors.line)
            SettingRow(
                Icons.AutoMirrored.Outlined.MenuBook,
                MaterialTheme.colorScheme.primary,
                "Journal folder",
                "${state.journalInput} · ${checkMessage(state.journalCheck)}",
                onClick = { editingFolder = state.journalInput },
            ) { Chevron() }
        }
    }
    editingFolder?.let { before ->
        AlertDialog(
            onDismissRequest = {
                onJournalInputChange(before)
                editingFolder = null
            },
            title = { Text("Journal folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Folder inside the vault that holds the year folders (YYYY/YYYY-MM-DD.md).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    JournalFolderFields(state, onJournalInputChange)
                }
            },
            confirmButton = { TextButton(onClick = { editingFolder = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    onJournalInputChange(before)
                    editingFolder = null
                }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalJournalColors.current.faint,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun AppearanceSection(appearance: Appearance, onChange: (Appearance) -> Unit) {
    SectionCard("Appearance") {
        Text("Theme", style = SettingTitle)
        Choice(
            options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
            selected = appearance.theme,
            onSelect = { onChange(appearance.copy(theme = it)) },
        )
        Text("Font for entries", style = SettingTitle)
        Choice(
            options = listOf(true to "Serif", false to "Sans serif"),
            selected = appearance.serif,
            onSelect = { onChange(appearance.copy(serif = it)) },
        )
    }
}

/** A segmented control as in Vink: the chosen option a white pill on a grey track. */
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(7.dp)) else Modifier)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A setting with a coloured square icon, a title and a description, and its control on the right. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    Row(
        if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(title, style = SettingTitle)
            Text(description, style = JournalType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

@Composable
private fun LockRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Checked again on resume: the user may have set a screen lock in the meantime.
    var deviceSecure by remember { mutableStateOf(isDeviceSecure(context)) }
    LifecycleResumeEffect(Unit) {
        deviceSecure = isDeviceSecure(context)
        onPauseOrDispose {}
    }
    SettingRow(
        Icons.Outlined.Lock,
        LocalJournalColors.current.teal,
        "App lock",
        "Fingerprint, face or screen lock. Quick capture still works.",
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            enabled = enabled || deviceSecure,
        )
    }
    if (!enabled && !deviceSecure) {
        Notice(Icons.Filled.Warning, "Set a screen lock (PIN, pattern or password) in Android's settings first.", isError = true)
    }
}

@Composable
private fun ReminderRow(reminder: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    val context = LocalContext.current
    // Checked again on resume: notifications may have been allowed in Android's settings.
    var canNotify by remember { mutableStateOf(DailyReminder.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = DailyReminder.canNotify(context)
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = DailyReminder.canNotify(context)
    }
    var pickingTime by remember { mutableStateOf(false) }

    SettingRow(
        Icons.Outlined.Notifications,
        LocalJournalColors.current.amber,
        "Daily reminder",
        if (reminder.enabled) "If nothing is written by then" else "On days you haven't written yet",
    ) {
        if (reminder.enabled) {
            Surface(
                onClick = { pickingTime = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(end = 10.dp),
            ) {
                Text(
                    reminder.time.format(TIME_FORMAT),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Reminder time ${reminder.time.format(TIME_FORMAT)}" },
                )
            }
        }
        Switch(
            checked = reminder.enabled,
            onCheckedChange = { on ->
                onChange(reminder.copy(enabled = on))
                if (on && !canNotify && Build.VERSION.SDK_INT >= 33) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
    if (reminder.enabled && !canNotify) {
        Notice(Icons.Filled.Warning, "Notifications for Journal are turned off, so the reminder can't show.", isError = true)
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }) {
            Text("Open notification settings")
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            initial = reminder.time,
            onDismiss = { pickingTime = false },
            onConfirm = { time ->
                pickingTime = false
                onChange(reminder.copy(time = time))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val SettingTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private fun checkMessage(check: JournalCheck): String = when (check) {
    JournalCheck.Pending -> "Checking…"
    JournalCheck.Invalid -> "Enter a folder path inside the vault, like Journal."
    JournalCheck.Missing -> "This folder doesn't exist yet. It will be created with your first entry."
    JournalCheck.NotAFolder -> "That's a file, not a folder."
    JournalCheck.Error -> "Couldn't read this folder."
    is JournalCheck.Found -> when {
        check.noteCount > 0 -> {
            val years = if (check.years.size == 1) check.years[0] else "${check.years.first()}–${check.years.last()}"
            "Found ${check.noteCount} daily ${if (check.noteCount == 1) "note" else "notes"} ($years)."
        }
        check.years.isNotEmpty() -> "Found year folders, but no daily notes yet."
        else -> "Folder found. No year folders (YYYY) yet."
    }
}

/** A small-caps label over a white card with rounded corners, on the grey ground (as in Vink). */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(Locale.getDefault()),
            style = JournalType.label.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LocalJournalColors.current.card)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, isError: Boolean = false) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
