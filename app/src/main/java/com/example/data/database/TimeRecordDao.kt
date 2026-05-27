package com.example.data.database

import androidx.room.*
import com.example.data.entity.TimeRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeRecordDao {
    @Query("SELECT * FROM time_records ORDER BY date DESC, id DESC")
    fun getAllTimeRecords(): Flow<List<TimeRecord>>

    @Query("SELECT * FROM time_records WHERE employeeId = :employeeId ORDER BY date DESC")
    fun getTimeRecordsForEmployee(employeeId: Int): Flow<List<TimeRecord>>

    @Query("SELECT * FROM time_records WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getTimeRecordForEmployeeAndDate(employeeId: Int, date: String): TimeRecord?

    @Query("SELECT * FROM time_records WHERE date = :date")
    suspend fun getTimeRecordsForDate(date: String): List<TimeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeRecord(record: TimeRecord): Long

    @Update
    suspend fun updateTimeRecord(record: TimeRecord)

    @Delete
    suspend fun deleteTimeRecord(record: TimeRecord)

    @Query("SELECT COUNT(*) FROM time_records")
    suspend fun getRecordCount(): Int

    @Query("SELECT * FROM time_records")
    suspend fun getAllTimeRecordsList(): List<TimeRecord>
}
