package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val position: String,
    val role: String, // "EMPLOYEE", "MANAGER"
    val pin: String = "1234",
    val email: String = "",
    val active: Boolean = true
) : Serializable
