package nl.markbroeders.journal.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import nl.markbroeders.journal.ui.theme.LocalJournalColors

/**
 * Covers the app while it is locked. Asks to unlock once for each [promptRequest] (the app
 * coming back to the screen), when started; after a cancel the button asks again.
 * Back leaves the app.
 */
@Composable
fun LockScreen(
    promptRequest: Int,
    error: String?,
    onUnlock: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    var prompted by remember { mutableIntStateOf(-1) }
    LifecycleStartEffect(promptRequest) {
        if (prompted != promptRequest) {
            prompted = promptRequest
            onUnlock()
        }
        onStopOrDispose {}
    }
    val colors = LocalJournalColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.header)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.onHeader, modifier = Modifier.size(56.dp))
        Text("Journal is locked", style = MaterialTheme.typography.headlineSmall, color = colors.onHeader)
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.onHeader, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = colors.onHeader, contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Unlock")
        }
    }
}
