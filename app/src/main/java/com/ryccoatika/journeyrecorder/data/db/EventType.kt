package com.ryccoatika.journeyrecorder.data.db

enum class EventType {
    SCREEN_OPEN,
    CLICK,
    LONG_CLICK,
    TEXT_INPUT,
    SCROLL,
    SELECT,
    SYSTEM_DIALOG,
    APP_MARKER,
}

enum class Confidence { NORMAL, LOW }

enum class JourneyStatus { COMPLETED, RECOVERED }