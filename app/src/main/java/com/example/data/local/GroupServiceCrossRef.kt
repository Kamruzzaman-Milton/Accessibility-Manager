package com.example.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "group_services",
    primaryKeys = ["groupId", "componentKey"],
    indices = [Index(value = ["groupId"]), Index(value = ["componentKey"])]
)
data class GroupServiceCrossRef(
    val groupId: String,
    val componentKey: String
)
