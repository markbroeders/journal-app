package nl.markbroeders.journal.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markbroeders.journal.data.ReminderSettings
import nl.markbroeders.journal.data.VaultPaths
import nl.markbroeders.journal.lock.isDeviceSecure
import nl.markbroeders.journal.reminder.DailyReminder
import nl.markbroeders.journal.ui.theme.journalTopBarColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Picks the vault root and the journal folder inside it. [onCancel] is null during first-run
 * setup (there is nothing to go back to) and set when changing settings later.
 * [lockEnabled] is null during setup; in settings it shows the app lock switch, which applies
 * right away (turning it on asks to unlock first). [reminder] likewise shows the daily reminder's
 * settings, also applied right away.
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onVaultPicked(uri)
    }
    if (onCancel != null) BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onCancel == null) "Set up Journal" else "Settings") },
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = journalTopBarColors(),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (accessLost && state.vault == null) {
                Notice(
                    Icons.Filled.Warning,
                    "Journal no longer has access to your vault folder. Choose it again to continue.",
                    isError = true,
                )
            }
            Text(
                "Journal reads and writes the daily notes in your Obsidian vault. Your notes stay " +
                    "plain Markdown files; nothing is moved or renamed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            VaultSection(state, onPick = { picker.launch(state.vault?.uri) })

            if (state.vault != null) {
                JournalFolderSection(state, viewModel::onJournalInputChange)
            }

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (onCancel == null) "Continue" else "Save")
            }

            if (lockEnabled != null) LockSection(lockEnabled, onLockChange)
            if (reminder != null) ReminderSection(reminder, onReminderChange)
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
private fun JournalFolderSection(state: SetupUiState, onInputChange: (String) -> Unit) {
    SectionCard("Journal folder") {
        Text(
            "Folder inside the vault that holds the year folders (YYYY/YYYY-MM-DD.md).",
            style = MaterialTheme.typography.bodyMedium,
        )
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
}

@Composable
private fun LockSection(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Checked again on resume: the user may have set a screen lock in the meantime.
    var deviceSecure by remember { mutableStateOf(isDeviceSecure(context)) }
    LifecycleResumeEffect(Unit) {
        deviceSecure = isDeviceSecure(context)
        onPauseOrDispose {}
    }
    SectionCard("App lock") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Unlock with fingerprint, face or the device PIN/pattern whenever you open the app. " +
                    "New entries can still be written from the widget, shortcuts and sharing.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
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
}

@Composable
private fun ReminderSection(reminder: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
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

    SectionCard("Daily reminder") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A notification at a set time, on days you haven't written anything yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
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
        if (reminder.enabled) {
            OutlinedButton(onClick = { pickingTime = true }) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remind me at ${reminder.time.format(TIME_FORMAT)}")
            }
            if (!canNotify) {
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

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
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
