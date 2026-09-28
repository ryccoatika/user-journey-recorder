package com.ryccoatika.journeyrecorder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journeys")
data class JourneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetPackage: String,
    val targetAppLabel: String?,
    val appVersionName: String?,
    val deviceInfo: String,
    val androidVersion: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: JourneyStatus = JourneyStatus.COMPLETED,
    // true when the target exposed zero view resource-ids at recording start
    val noElementIds: Boolean = false,
)