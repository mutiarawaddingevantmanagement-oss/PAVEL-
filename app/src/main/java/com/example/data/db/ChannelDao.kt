package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY orderIndex ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE lastWatched > 0 ORDER BY lastWatched DESC LIMIT 15")
    fun getRecentChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupName FROM channels ORDER BY groupName ASC")
    fun getUniqueCategories(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE groupName = :groupName ORDER BY orderIndex ASC")
    fun getChannelsByGroup(groupName: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' ORDER BY orderIndex ASC")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE streamUrl = :streamUrl")
    suspend fun updateFavoriteStatus(streamUrl: String, isFavorite: Boolean)

    @Query("UPDATE channels SET lastWatched = :timestamp WHERE streamUrl = :streamUrl")
    suspend fun updateLastWatched(streamUrl: String, timestamp: Long)

    @Query("DELETE FROM channels")
    suspend fun clearAllChannels()
    
    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelCount(): Int
}
