package com.ryccoatika.journeyrecorder.ui.setup

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.journeyrecorder.data.JourneyRepository
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.util.DeviceInfoProvider
import com.ryccoatika.journeyrecorder.util.PermissionChecks
import kotlinx.coroutines.launch

class SetupViewModel(
    private val repo: JourneyRepository = Graph.repository,
) : ViewModel() {
    var accessibilityEnabled by mutableStateOf(false)
        private set
    var overlayGranted by mutableStateOf(false)
        private set

    /**
     * Live binding state of the accessibility service. The settings toggle can
     * be on while no service instance is running (e.g. after a force-stop) —
     * starting then would record a silent zero-step journey with no bubble.
     */
    var serviceConnected by mutableStateOf(Graph.recorderState.serviceConnected.value)
        private set

    /** Set once a recording started; the screen observes it and navigates. */
    var started by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            Graph.recorderState.serviceConnected.collect { serviceConnected = it }
        }
    }

    /** null while loading. */
    var apps by mutableStateOf<List<InstalledApp>?>(null)
        private set
    var query by mutableStateOf("")
    var selectedPackage by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var starting by mutableStateOf(false)
        private set

    val filteredApps by derivedStateOf {
        val loaded = apps ?: return@derivedStateOf emptyList<InstalledApp>()
        val q = query.trim()
        if (q.isEmpty()) {
            loaded
        } else {
            loaded.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    val canStart: Boolean
        get() = accessibilityEnabled && serviceConnected && selectedPackage != null && !starting

    fun refreshPermissions(context: Context) {
        accessibilityEnabled = PermissionChecks.isAccessibilityServiceEnabled(context)
        overlayGranted = PermissionChecks.canDrawOverlays(context)
    }

    fun loadApps(context: Context) {
        if (apps != null) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            apps = InstalledAppsProvider.load(appContext)
        }
    }

    fun dismissError() {
        errorMessage = null
    }

    fun start(context: Context) {
        val app = apps?.firstOrNull { it.packageName == selectedPackage } ?: return
        if (starting) return
        starting = true
        // Application context: survives rotation while startRecording suspends.
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = runCatching {
                repo.startRecording(
                    targetPackage = app.packageName,
                    targetAppLabel = app.label,
                    appVersionName = app.versionName,
                    deviceInfo = DeviceInfoProvider.deviceInfo(),
                    androidVersion = DeviceInfoProvider.androidVersion(),
                )
            }
            starting = false
            result.fold(
                onSuccess = {
                    appContext.packageManager
                        .getLaunchIntentForPackage(app.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let(appContext::startActivity)
                    started = true
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "Could not start recording"
                },
            )
        }
    }
}
