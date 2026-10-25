package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_services")
data class PinnedServiceEntity(
    @PrimaryKey val componentKey: String,
    val pinnedAt: Long = System.currentTimeMillis()
)
