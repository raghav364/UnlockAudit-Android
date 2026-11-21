package com.raghava.unlockaudit

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import com.raghava.unlockaudit.service.TrackerService
import com.raghava.unlockaudit.ui.AuditApp
import com.raghava.unlockaudit.ui.AuditViewModel

class MainActivity : ComponentActivity() {
    private val vm by viewModels<AuditViewModel>()
    private val askNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Even when denied, foreground service remains visible in Android's task manager. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by vm.themeMode.collectAsState()
            AuditApp(
                vm = vm, themeMode = mode,
                openUsageSettings = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                startTracking = {
                    if (!vm.repo.hasUsageAccess()) {
                        vm.message.value = "Grant Usage Access before starting tracking"
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } else {
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                        try {
                            ContextCompat.startForegroundService(this, Intent(this, TrackerService::class.java)
                                .setAction(TrackerService.ACTION_START))
                        } catch (e: Exception) {
                            vm.message.value = "Cannot start tracking: ${e.localizedMessage}"
                        }
                    }
                },
                stopTracking = {
                    // Send stop command to running service, which persists the pending session.
                    try {
                        startService(Intent(this, TrackerService::class.java)
                            .setAction(TrackerService.ACTION_STOP))
                    } catch (e: Exception) {
                        vm.message.value = "Cannot stop tracking: ${e.localizedMessage}"
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
