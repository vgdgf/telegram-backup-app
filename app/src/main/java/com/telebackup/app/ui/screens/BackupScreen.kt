package com.telebackup.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.telebackup.app.data.local.entity.BackupItem
import com.telebackup.app.data.local.entity.UploadStatus
import com.telebackup.app.data.prefs.AppSettings
import com.telebackup.app.ui.BackupUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    settings: AppSettings,
    state: BackupUiState,
    onToggleBackup: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onCompress: (Boolean) -> Unit,
    onFolders: (Set<String>) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("TeleBackup") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusCard(settings, state, onToggleBackup) }
            item {
                CredentialsCard(settings, onSaveCredentials)
            }
            item {
                OptionsCard(
                    settings = settings,
                    onWifiOnly = onWifiOnly,
                    onCompress = onCompress,
                    onFolders = onFolders,
                )
            }
            item {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium)
            }
            items(state.recent) { item -> ActivityRow(item) }
        }
    }
}

@Composable
private fun StatusCard(
    settings: AppSettings,
    state: BackupUiState,
    onToggleBackup: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Backup", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (settings.backupEnabled) "Active — watching for media"
                        else "Stopped",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.backupEnabled,
                    onCheckedChange = onToggleBackup,
                    enabled = settings.isConfigured,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Stat("Uploaded", state.uploaded.toString())
                Stat("Remaining", state.remaining.toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CredentialsCard(
    settings: AppSettings,
    onSave: (String, String) -> Unit,
) {
    var token by remember(settings.botToken) { mutableStateOf(settings.botToken) }
    var chatId by remember(settings.chatId) { mutableStateOf(settings.chatId) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Telegram bot", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Bot token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it },
                label = { Text("Chat ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(token, chatId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save credentials") }
        }
    }
}

@Composable
private fun OptionsCard(
    settings: AppSettings,
    onWifiOnly: (Boolean) -> Unit,
    onCompress: (Boolean) -> Unit,
    onFolders: (Set<String>) -> Unit,
) {
    val knownFolders = listOf("DCIM", "Pictures", "Movies", "Download", "Screenshots")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Upload on Wi-Fi only", settings.wifiOnly, onWifiOnly)
            ToggleRow("Compress images before upload", settings.compressImages, onCompress)

            Text(
                "Monitored folders (none selected = all)",
                style = MaterialTheme.typography.bodyMedium,
            )
            knownFolders.forEach { folder ->
                val selected = settings.monitoredFolders.contains(folder)
                ToggleRow(folder, selected) { checked ->
                    val updated = settings.monitoredFolders.toMutableSet().apply {
                        if (checked) add(folder) else remove(folder)
                    }
                    onFolders(updated)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActivityRow(item: BackupItem) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (icon, tint) = when (item.status) {
                UploadStatus.UPLOADED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                UploadStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                UploadStatus.UPLOADING -> Icons.Default.CloudUpload to MaterialTheme.colorScheme.secondary
                else -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.outline
            }
            Icon(icon, contentDescription = item.status.name, tint = tint,
                modifier = Modifier.size(24.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    item.status.name + (item.lastError?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
