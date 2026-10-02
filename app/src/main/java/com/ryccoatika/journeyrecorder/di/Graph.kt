package com.ryccoatika.journeyrecorder.di

import android.content.Context
import androidx.room.Room
import com.ryccoatika.journeyrecorder.analytics.AppAnalytics
import com.ryccoatika.journeyrecorder.data.AppPrefs
import com.ryccoatika.journeyrecorder.data.JourneyRepository
import com.ryccoatika.journeyrecorder.data.db.AppDatabase
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.recorder.RecorderStateHolder
import com.ryccoatika.journeyrecorder.recorder.StepPipeline

object Graph {
    lateinit var db: AppDatabase
        private set

    val journeyDao: JourneyDao get() = db.journeyDao()

    val recorderState = RecorderStateHolder()

    val stepPipeline: StepPipeline by lazy { StepPipeline(journeyDao) }

    val repository: JourneyRepository by lazy {
        JourneyRepository(journeyDao, recorderState, stepPipeline, analytics)
    }

    lateinit var appPrefs: AppPrefs
        private set

    lateinit var analytics: AppAnalytics
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        db = Room.databaseBuilder(appContext, AppDatabase::class.java, "journeys.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        appPrefs = AppPrefs(appContext)
        analytics = AppAnalytics(appContext)
    }
}