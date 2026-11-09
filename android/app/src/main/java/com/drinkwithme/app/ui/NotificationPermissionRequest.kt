package com.drinkwithme.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Fragt ab Android 13 nach der Berechtigung für Benachrichtigungen, sobald [shouldRequest] `true` wird
 * (nach erfolgreicher Anmeldung). Das Ergebnis ist egal: Ohne Berechtigung gibt es nur keine Pushs.
 */
@Composable
fun NotificationPermissionRequest(shouldRequest: Boolean, onRequested: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(shouldRequest) {
        if (!shouldRequest) return@LaunchedEffect
        onRequested()
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
