package com.telebackup.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telebackup.app.data.local.entity.BackupItem
import com.telebackup.app.data.prefs.AppSettings
import com.telebackup.app.data.repository.BackupRepository
import com.telebackup.app.service.BackupForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val uploaded: Int = 0,
    val remaining: Int = 0,
    val recent: List<BackupItem> = emptyList(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: BackupRepository,
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = repository.settings

    val uiState: StateFlow<BackupUiState> = kotlinx.coroutines.flow.combine(
        repository.uploadedCount,
        repository.remainingCount,
        repository.recent(),
    ) { uploaded, remaining, recent ->
        BackupUiState(uploaded, remaining, recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupUiState())

    fun setCredentials(token: String, chatId: String) =
        repository.updateSettings { it.copy(botToken = token.trim(), chatId = chatId.trim()) }

    fun setWifiOnly(enabled: Boolean) =
        repository.updateSettings { it.copy(wifiOnly = enabled) }

    fun setCompress(enabled: Boolean) =
        repository.updateSettings { it.copy(compressImages = enabled) }

    fun setFolders(folders: Set<String>) =
        repository.updateSettings { it.copy(monitoredFolders = folders) }

    fun toggleBackup(enabled: Boolean) {
        repository.updateSettings { it.copy(backupEnabled = enabled) }
        val ctx = getApplication<Application>()
        if (enabled) BackupForegroundService.start(ctx)
        else BackupForegroundService.stop(ctx)
    }

    /** Triggered after permissions are granted to do an initial sweep. */
    fun kickInitialScan() {
        if (settings.value.backupEnabled && settings.value.isConfigured) {
            BackupForegroundService.start(getApplication())
        }
    }
}
