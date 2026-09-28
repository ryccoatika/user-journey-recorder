package com.ryccoatika.journeyrecorder.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journey_events",
    foreignKeys = [
        ForeignKey(
            entity = JourneyEntity::class,
            parentColumns = ["id"],
            childColumns = ["journeyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("journeyId")],
)
data class JourneyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    // ordering source of truth; timestamps are display-only (coalesced steps emit late)
    val sequence: Int,
    val wallClockMs: Long,
    val eventType: EventType,
    val screenName: String?,
    val elementId: String?,
    val elementText: String?,
    val contentDesc: String?,
    val className: String?,
    val bounds: String?,
    val typedText: String?,
    val masked: Boolean = false,
    val confidence: Confidence = Confidence.NORMAL,
    val collectionIndex: Int? = null,
)