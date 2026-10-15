package com.m57.hermescontrol.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.speech.RecognizerIntent

object SpeechInputHelper {
    /** Returns whether Android exposes an activity that can handle speech input. */
    fun isSpeechInputAvailable(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
        queryLegacy: (PackageManager, Intent) -> List<ResolveInfo?> = { packageManager, intent ->
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        },
        queryModern: (PackageManager, Intent) -> List<ResolveInfo?> = ::queryModernActivities,
    ): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities: List<ResolveInfo?> =
            runCatching {
                if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                    queryModern(context.packageManager, intent)
                } else {
                    queryLegacy(context.packageManager, intent)
                }
            }.getOrDefault(emptyList())

        return activities.any { it != null }
    }

    @SuppressLint("NewApi")
    private fun queryModernActivities(
        packageManager: PackageManager,
        intent: Intent,
    ): List<ResolveInfo?> =
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0L),
        )
}
