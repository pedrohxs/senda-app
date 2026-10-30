package com.senda.app.data.local

import androidx.room.TypeConverter
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.TargetType

class SendaConverters {

    @TypeConverter
    fun fromSessionCountMode(value: SessionCountMode): String = value.name

    @TypeConverter
    fun toSessionCountMode(value: String): SessionCountMode = SessionCountMode.valueOf(value)

    @TypeConverter
    fun fromInterventionType(value: InterventionType): String = value.name

    @TypeConverter
    fun toInterventionType(value: String): InterventionType = InterventionType.valueOf(value)

    @TypeConverter
    fun fromMathDifficulty(value: MathDifficulty): String = value.name

    @TypeConverter
    fun toMathDifficulty(value: String): MathDifficulty = MathDifficulty.valueOf(value)

    @TypeConverter
    fun fromTargetType(value: TargetType): String = value.name

    @TypeConverter
    fun toTargetType(value: String): TargetType = TargetType.valueOf(value)

    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun fromDaysOfWeek(value: List<Int>): String = value.joinToString(separator = ",")

    @TypeConverter
    fun toDaysOfWeek(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").map(String::toInt)
}
