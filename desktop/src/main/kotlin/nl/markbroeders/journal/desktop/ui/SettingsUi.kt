package nl.markbroeders.journal.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markbroeders.journal.data.VaultPaths
import nl.markbroeders.journal.desktop.Platform
import nl.markbroeders.journal.desktop.data.Appearance
import nl.markbroeders.journal.desktop.data.DesktopSettings
import nl.markbroeders.journal.desktop.ui.theme.JournalType
import java.nio.file.Files
import java.nio.file.Path

/** Checks a vault and journal folder as typed; returns the error to show, or null if they're fine. */
private fun problem(vault: String, journal: String): String? = when {
    vault.isBlank() -> "Choose the vault folder."
    !Files.isDirectory(Path.of(vault.trim())) -> "This folder doesn't exist."
    VaultPaths.normalizeFolder(journal) == null -> "Enter a folder inside the vault, like Journal."
    else -> null
}

private fun isVault(vault: String) = runCatching { Files.isDirectory(Path.of(vault.trim(), ".obsidian")) }.getOrDefault(false)

/** The vault folder (with a Browse button) and the journal folder inside it. */
@Composable
private fun VaultFields(vault: String, onVault: (String) -> Unit, journal: String, onJournal: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = vault,
                onValueChange = onVault,
                label = { Text("Vault folder") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                scope.launch {
                    val start = vault.takeIf { it.isNotBlank() }?.let { Path.of(it.trim()) } ?: Path.of(System.getProperty("user.home"))
                    Platform.pickFolder("Choose your Obsidian vault", start)?.let { onVault(it.toString()) }
                }
            }) { Text("Browse…") }
        }
        if (vault.isNotBlank() && !isVault(vault) && Files.isDirectory(runCatching { Path.of(vault.trim()) }.getOrNull() ?: Path.of(""))) {
            Text(
                "This folder has no .obsidian folder. Is it the vault's top folder?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = journal,
            onValueChange = onJournal,
            label = { Text("Journal folder in the vault") },
            supportingText = { Text("Daily notes go in this folder as YYYY/YYYY-MM-DD.md.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** First run: pick the vault. */
@Composable
fun SetupScreen(initial: DesktopSettings, onDone: (Path, String) -> Unit) {
    var vault by remember { mutableStateOf(initial.vault?.toString().orEmpty()) }
    var journal by remember { mutableStateOf(initial.journalFolder) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("Journal", style = JournalType.title)
            Text(
                "Choose your Obsidian vault. Entries are saved as daily notes in its journal folder " +
                    "(one Markdown file per day), so they show up in Obsidian as they are. Nothing else is stored.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VaultFields(vault, { vault = it; error = null }, journal, { journal = it; error = null })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = {
                error = problem(vault, journal)
                if (error == null) onDone(Path.of(vault.trim()), VaultPaths.normalizeFolder(journal)!!)
            }) { Text("Open journal") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(current: DesktopSettings, onDismiss: () -> Unit, onSave: (DesktopSettings) -> Unit) {
    var vault by remember { mutableStateOf(current.vault?.toString().orEmpty()) }
    var journal by remember { mutableStateOf(current.journalFolder) }
    var appearance by remember { mutableStateOf(current.appearance) }
    var serif by remember { mutableStateOf(current.serif) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                VaultFields(vault, { vault = it; error = null }, journal, { journal = it; error = null })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Appearance.entries.forEachIndexed { i, option ->
                            SegmentedButton(
                                selected = appearance == option,
                                onClick = { appearance = option },
                                shape = SegmentedButtonDefaults.itemShape(i, Appearance.entries.size),
                            ) { Text(option.name) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entry font", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = serif, onClick = { serif = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Serif") }
                        SegmentedButton(selected = !serif, onClick = { serif = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Sans serif") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = problem(vault, journal)
                if (error == null) {
                    onSave(
                        current.copy(
                            vault = Path.of(vault.trim()),
                            journalFolder = VaultPaths.normalizeFolder(journal)!!,
                            appearance = appearance,
                            serif = serif,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
