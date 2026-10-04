package com.ryccoatika.journeyrecorder.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.ryccoatika.journeyrecorder.recorder.JourneyAccessibilityService

object PermissionChecks {
    /** True when our accessibility service is listed in the system's enabled-services setting. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, JourneyAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry) ?: continue
            if (component == expected) return true
        }
        return false
    }

    /** Informational only: the bubble uses an accessibility overlay; this is the fallback path. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
}
