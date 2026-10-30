package com.senda.app.data.local.entity

enum class SessionCountMode {
    ACTIVE_SCREEN_TIME,
    WALL_CLOCK_WINDOW
}

enum class InterventionType {
    BREATHING,
    MATH,
    REASON
}

enum class TargetType {
    APP,
    WEBSITE,
    KEYWORD
}

enum class ScheduleType {
    LIMITED,
    STRICT,
    BREAK
}

enum class MathDifficulty {
    EASY,
    MEDIUM,
    HARD
}
