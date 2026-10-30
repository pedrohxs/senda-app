package com.senda.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_items",
    foreignKeys = [
        ForeignKey(
            entity = AppGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("targetIdentifier")]
)
data class GroupItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,
    val targetType: TargetType,
    val targetIdentifier: String,
    val displayName: String
)
