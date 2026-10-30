package com.senda.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.ColumnInfo
import java.util.UUID

@Entity(
    tableName = "group_schedules",
    foreignKeys = [
        ForeignKey(
            entity = AppGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class GroupScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,
    val scheduleType: ScheduleType,
    val daysOfWeek: List<Int>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    @ColumnInfo(defaultValue = "''") val ruleId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "''") val name: String = "",
    @Embedded(prefix = "custom_") val customRules: LimitRules? = null
)
