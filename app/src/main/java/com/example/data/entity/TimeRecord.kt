package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val date: String, // format "yyyy-MM-dd"
    val entryTime: Long? = null,
    val lunchOutTime: Long? = null,
    val lunchReturnTime: Long? = null,
    val exitTime: Long? = null,
    val entryLocation: String? = null,
    val lunchOutLocation: String? = null,
    val lunchReturnLocation: String? = null,
    val exitLocation: String? = null,
    val isOffline: Boolean = true,
    val notes: String? = null
) : Serializable
