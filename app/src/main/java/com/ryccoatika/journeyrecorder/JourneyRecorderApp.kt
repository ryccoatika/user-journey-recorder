package com.ryccoatika.journeyrecorder

import android.app.Application
import com.ryccoatika.journeyrecorder.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JourneyRecorderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // Close journeys orphaned by a process death even if the accessibility
        // service never reconnects (its onServiceConnected does the same sweep).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            Graph.repository.recoverOrphans()
        }
    }
}