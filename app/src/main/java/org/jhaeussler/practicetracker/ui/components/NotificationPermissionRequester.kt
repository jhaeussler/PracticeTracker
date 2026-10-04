package org.jhaeussler.practicetracker.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class NotificationPermissionRequester(
    private val context: Context,
    private val launcher: ManagedActivityResultLauncher<String, Boolean>,
    private val setOnGrantedAction: (() -> Unit) -> Unit
) {
    fun checkAndRequest(onGranted: (() -> Unit)? = null)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            if (onGranted != null) {
                setOnGrantedAction(onGranted)
            }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

val LocalNotificationRequester = staticCompositionLocalOf<NotificationPermissionRequester> {
    error("No NotificationPermissionRequester provided")
}

@Composable
fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val context = LocalContext.current
    var onGrantedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGrantedCallback?.invoke()
        }
        onGrantedCallback = null
    }

    return remember(launcher, context) {
        NotificationPermissionRequester(
            context = context,
            launcher = launcher,
            setOnGrantedAction = { callback -> onGrantedCallback = callback }
        )
    }
}