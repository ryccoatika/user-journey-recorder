package com.ryccoatika.journeyrecorder.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun eventTypeToString(v: EventType): String = v.name

    @TypeConverter fun stringToEventType(v: String): EventType = EventType.valueOf(v)

    @TypeConverter fun confidenceToString(v: Confidence): String = v.name

    @TypeConverter fun stringToConfidence(v: String): Confidence = Confidence.valueOf(v)

    @TypeConverter fun statusToString(v: JourneyStatus): String = v.name

    @TypeConverter fun stringToStatus(v: String): JourneyStatus = JourneyStatus.valueOf(v)
}