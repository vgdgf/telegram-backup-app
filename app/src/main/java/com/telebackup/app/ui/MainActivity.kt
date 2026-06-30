package com.telebackup.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telebackup.app.ui.screens.BackupScreen
import com.telebackup.app.ui.theme.TeleBackupTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TeleBackupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val vm: MainViewModel = hiltViewModel()
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { result ->
                        val granted = result.values.all { it } ||
                            result.filterKeys { it.contains("MEDIA") }.values.any { it }
                        if (granted) vm.kickInitialScan()
                    }

                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(requiredPermissions())
                    }

                    BackupScreen(
                        settings = settings,
                        state = state,
                        onToggleBackup = vm::toggleBackup,
                        onSaveCredentials = vm::setCredentials,
                        onWifiOnly = vm::setWifiOnly,
                        onCompress = vm::setCompress,
                        onFolders = vm::setFolders,
                        onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                    )
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.toTypedArray()
    }
}
