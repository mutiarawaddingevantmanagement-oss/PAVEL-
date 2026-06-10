package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val streamUrl: String,
    val name: String,
    val logoUrl: String,
    val groupName: String = "Other",
    val isFavorite: Boolean = false,
    val lastWatched: Long = 0, // Timestamp of last watch (0 = never watched)
    val orderIndex: Int = 0,
    val playlistUrl: String = ""
)
