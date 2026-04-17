package com.danghung.nhungapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.danghung.nhungapp.data.local.entity.HistoryEntity

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_logs ORDER BY created_at DESC")
    suspend fun getAllHistory(): List<HistoryEntity>

    @Insert
    suspend fun insertHistory(entity: HistoryEntity): Long

    @Query("DELETE FROM history_logs")
    suspend fun deleteAll()
}
