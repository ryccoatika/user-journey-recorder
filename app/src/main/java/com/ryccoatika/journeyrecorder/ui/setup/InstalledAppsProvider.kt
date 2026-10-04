package com.ryccoatika.journeyrecorder.ui.setup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.ryccoatika.journeyrecorder.util.DeviceInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val icon: ImageBitmap,
)

object InstalledAppsProvider {
    /** All launchable apps except our own, sorted by label. */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        resolveInfos
            .asSequence()
            .filter { it.activityInfo != null }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val drawable = resolveInfo.loadIcon(pm)
                val bitmap = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    drawable.toBitmap()
                } else {
                    drawable.toBitmap(width = ICON_FALLBACK_SIZE, height = ICON_FALLBACK_SIZE)
                }
                InstalledApp(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                    versionName = DeviceInfoProvider.targetVersionName(context, packageName),
                    icon = bitmap.asImageBitmap(),
                )
            }.sortedBy { it.label.lowercase() }
            .toList()
    }

    private const val ICON_FALLBACK_SIZE = 96
}
