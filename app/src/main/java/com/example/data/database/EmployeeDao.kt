package com.example.data.database

import androidx.room.*
import com.example.data.entity.Employee
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE active = 1 ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Int): Employee?

    @Query("SELECT * FROM employees WHERE pin = :pin AND active = 1 LIMIT 1")
    suspend fun verifyPin(pin: String): Employee?

    @Query("SELECT * FROM employees WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND pin = :pin AND active = 1 LIMIT 1")
    suspend fun verifyNameAndPin(name: String, pin: String): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun getEmployeeCount(): Int

    @Query("SELECT * FROM employees ORDER BY name ASC")
    suspend fun getAllEmployeesList(): List<Employee>

    @Query("SELECT * FROM employees WHERE pin = :pin LIMIT 1")
    suspend fun getEmployeeByPin(pin: String): Employee?
}
