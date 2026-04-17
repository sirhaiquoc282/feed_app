package com.danghung.nhungapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.danghung.nhungapp.data.local.entity.ScheduleEntity

// Added: DAO for CRUD operations of Home schedules.
@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY created_at DESC, id DESC")
    suspend fun getAllSchedules(): List<ScheduleEntity>

    @Query("SELECT COUNT(*) FROM schedules WHERE date_time = :dateTime")
    suspend fun countByDateTime(dateTime: String): Int

    @Query("SELECT * FROM schedules WHERE id = :scheduleId LIMIT 1")
    suspend fun getById(scheduleId: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE status = 0")
    suspend fun getPendingSchedules(): List<ScheduleEntity>

    @Insert
    suspend fun insertSchedule(entity: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(entity: ScheduleEntity)

    @Query("UPDATE schedules SET status = :status WHERE id = :scheduleId")
    suspend fun updateStatusById(scheduleId: Long, status: Boolean)

    @Query("UPDATE schedules SET status = 1 WHERE id = :scheduleId AND status = 0")
    suspend fun claimPendingSchedule(scheduleId: Long): Int

    @Delete
    suspend fun deleteSchedule(entity: ScheduleEntity)
}
