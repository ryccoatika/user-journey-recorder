package com.ryccoatika.journeyrecorder.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object DeviceInfoProvider {

    fun deviceInfo(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun androidVersion(): String =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** versionName of an installed package, or null when unavailable. */
    fun targetVersionName(context: Context, packageName: String): String? = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        info.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
