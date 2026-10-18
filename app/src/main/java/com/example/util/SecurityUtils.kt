package com.example.util

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.example.BuildConfig

/**
 * Utility to manage app-wide security, preventing screen capture,
 * screenshots, screen recording, and external data leakage.
 */
object SecurityUtils {

    private const val TAG = "SecurityUtils"

    /**
     * Determines whether the app is executing inside an emulator or virtual container.
     */
    fun isRunningInEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk", ignoreCase = true)
                || Build.MODEL.contains("Emulator", ignoreCase = true)
                || Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
                || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
                || (Build.BRAND.startsWith("generic", ignoreCase = true) && Build.DEVICE.startsWith("generic", ignoreCase = true))
                || Build.PRODUCT.contains("google_sdk", ignoreCase = true)
                || Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)
                || Build.PRODUCT.contains("sdk_google", ignoreCase = true)
                || Build.PRODUCT.contains("vbox86p", ignoreCase = true)
                || Build.HARDWARE.contains("goldfish", ignoreCase = true)
                || Build.HARDWARE.contains("ranchu", ignoreCase = true)
                || Build.BOARD.lowercase().contains("goldfish")
                || Build.HOST.lowercase().contains("build"))
    }

    /**
     * Applies WindowManager.LayoutParams.FLAG_SECURE to prevent screenshots,
     * screen recording, and display mirroring on user devices.
     *
     * In web-streamed browser emulators (like AI Studio live preview), FLAG_SECURE
     * blanks the browser video feed (causing a solid black screen).
     * Therefore, for live development preview on emulators, it is bypassed,
     * while strictly active on ALL physical devices and in release builds.
     */
    fun applyScreenshotProtection(activity: Activity) {
        try {
            val isEmulator = isRunningInEmulator()
            if (!isEmulator || !BuildConfig.DEBUG) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
                Log.d(TAG, "FLAG_SECURE applied: Screenshots and screen recording are blocked.")
            } else {
                Log.d(TAG, "Running in dev preview emulator - FLAG_SECURE bypassed for live browser streaming.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying screenshot protection", e)
        }
    }
}
