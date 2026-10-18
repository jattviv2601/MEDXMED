package com.example.util

import android.app.Activity
import android.util.Log
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object AppUpdateHelper {
    private const val TAG = "AppUpdateHelper"

    fun checkForAppUpdate(
        activity: Activity,
        onUpdateAvailable: () -> Unit = {}
    ) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    Log.d(TAG, "New app update available on Google Play!")
                    onUpdateAvailable()
                    try {
                        appUpdateManager.startUpdateFlow(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to start immediate update flow: ${e.message}")
                    }
                }
            }.addOnFailureListener { e ->
                Log.d(TAG, "App update check skipped/unavailable in this environment: ${e.message}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception during app update check: ${e.message}")
        }
    }
}

@Composable
fun AppUpdateBlockingDialog(
    showDialog: Boolean,
    onUpdateClick: () -> Unit
) {
    if (showDialog) {
        // Blocking dialog (cannot dismiss by clicking outside or back button)
        AlertDialog(
            onDismissRequest = {},
            title = { Text("App Update Required 🚀") },
            text = { Text("A new version of MEDXMED is available on the Play Store. Please update now to continue using the latest features and security improvements.") },
            confirmButton = {
                Button(onClick = onUpdateClick) {
                    Text("Update Now")
                }
            }
        )
    }
}
