package com.example.data.repository

import com.example.data.database.EmployeeDao
import com.example.data.database.TimeRecordDao
import com.example.data.entity.Employee
import com.example.data.entity.TimeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TimeRecordRepository(
    private val employeeDao: EmployeeDao,
    private val timeRecordDao: TimeRecordDao
) {
    val allEmployees: Flow<List<Employee>> = employeeDao.getAllEmployees()
    val allTimeRecords: Flow<List<TimeRecord>> = timeRecordDao.getAllTimeRecords()

    fun getTimeRecordsForEmployee(employeeId: Int): Flow<List<TimeRecord>> {
        return timeRecordDao.getTimeRecordsForEmployee(employeeId)
    }

    suspend fun getTimeRecordForEmployeeAndDate(employeeId: Int, date: String): TimeRecord? {
        return timeRecordDao.getTimeRecordForEmployeeAndDate(employeeId, date)
    }

    suspend fun insertEmployee(employee: Employee): Long {
        return employeeDao.insertEmployee(employee)
    }

    suspend fun updateEmployee(employee: Employee) {
        employeeDao.updateEmployee(employee)
    }

    suspend fun deleteEmployee(employee: Employee) {
        employeeDao.deleteEmployee(employee)
    }

    suspend fun getEmployeeById(id: Int): Employee? {
        return employeeDao.getEmployeeById(id)
    }

    suspend fun verifyPin(pin: String): Employee? {
        return employeeDao.verifyPin(pin)
    }

    suspend fun verifyNameAndPin(name: String, pin: String): Employee? {
        return employeeDao.verifyNameAndPin(name, pin)
    }

    suspend fun insertTimeRecord(record: TimeRecord): Long {
        return timeRecordDao.insertTimeRecord(record)
    }

    suspend fun updateTimeRecord(record: TimeRecord) {
        timeRecordDao.updateTimeRecord(record)
    }

    suspend fun deleteTimeRecord(record: TimeRecord) {
        timeRecordDao.deleteTimeRecord(record)
    }

    suspend fun preloadDataIfEmpty() {
        val empCount = employeeDao.getEmployeeCount()
        if (empCount == 0) {
            // Seed base employees (Managers & Employees)
            val manager = Employee(
                name = "Carlos Santana",
                position = "Gerente de Operações",
                role = "GERENTE",
                pin = "8888",
                email = "carlos.santana@empresa.com"
            )
            val managerId = employeeDao.insertEmployee(manager).toInt()

            val emp1 = Employee(
                name = "Ricardo Almeida",
                position = "Desenvolvedor Sênior",
                role = "FUNCIONARIO",
                pin = "1234",
                email = "ricardo.almeida@empresa.com"
            )
            val emp1Id = employeeDao.insertEmployee(emp1).toInt()

            val emp2 = Employee(
                name = "Ana Silva",
                position = "Designer UX",
                role = "FUNCIONARIO",
                pin = "4321",
                email = "ana.silva@empresa.com"
            )
            val emp2Id = employeeDao.insertEmployee(emp2).toInt()

            val emp3 = Employee(
                name = "Mariana Costa",
                position = "Atendente de Suporte",
                role = "FUNCIONARIO",
                pin = "5555",
                email = "mariana.costa@empresa.com"
            )
            val emp3Id = employeeDao.insertEmployee(emp3).toInt()

            // Let's seed timesheet entries for the past 30 days for these employees to provide realistic history
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // Seed entries for previous 30 days
            for (offset in 0..35) {
                val loopCal = calendar.clone() as Calendar
                loopCal.add(Calendar.DAY_OF_YEAR, -offset)

                // Skip weekends (Saturday and Sunday)
                val dayOfWeek = loopCal.get(Calendar.DAY_OF_WEEK)
                if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                    continue
                }

                val dateStr = dateFormat.format(loopCal.time)

                // Create realistic clocks for emp1 (Ricardo Almeida) - very punctual
                val entryTime1 = getCalendarForTime(loopCal, 8, 3, offset).timeInMillis
                val lunchOutTime1 = getCalendarForTime(loopCal, 12, 2, offset).timeInMillis
                val lunchReturnTime1 = getCalendarForTime(loopCal, 13, 4, offset).timeInMillis
                val exitTime1 = getCalendarForTime(loopCal, 17, 58, offset).timeInMillis

                val latOffset = (offset % 5) * 0.001
                val lngOffset = (offset % 3) * 0.001
                val baseLoc = "-23.5505${offset % 10},-46.6333${offset % 10}"

                timeRecordDao.insertTimeRecord(
                    TimeRecord(
                        employeeId = emp1Id,
                        date = dateStr,
                        entryTime = entryTime1,
                        lunchOutTime = lunchOutTime1,
                        lunchReturnTime = lunchReturnTime1,
                        exitTime = exitTime1,
                        entryLocation = baseLoc,
                        lunchOutLocation = baseLoc,
                        lunchReturnLocation = baseLoc,
                        exitLocation = baseLoc,
                        isOffline = (offset % 3 == 0),
                        notes = if (offset == 5) "Atraso justificado por consulta médica" else null
                    )
                )

                // Create realistic clocks for emp2 (Ana Silva) - had a few delays and a couple of absences
                // Let's say offset 3 is a delay, and offset 8 is an absence (no entry)
                if (offset != 8) {
                    val entryHour = if (offset == 3) 9 else 8
                    val entryMin = if (offset == 3) 12 else 10
                    val entryTime2 = getCalendarForTime(loopCal, entryHour, entryMin, offset).timeInMillis
                    val lunchOutTime2 = getCalendarForTime(loopCal, 12, 5, offset).timeInMillis
                    val lunchReturnTime2 = getCalendarForTime(loopCal, 13, 5, offset).timeInMillis
                    val exitTime2 = getCalendarForTime(loopCal, 18, 0, offset).timeInMillis

                    timeRecordDao.insertTimeRecord(
                        TimeRecord(
                            employeeId = emp2Id,
                            date = dateStr,
                            entryTime = entryTime2,
                            lunchOutTime = lunchOutTime2,
                            lunchReturnTime = lunchReturnTime2,
                            exitTime = exitTime2,
                            entryLocation = baseLoc,
                            lunchOutLocation = baseLoc,
                            lunchReturnLocation = baseLoc,
                            exitLocation = baseLoc,
                            isOffline = false,
                            notes = if (offset == 3) "Atrasado devido a trânsito intenso" else null
                        )
                    )
                }

                // Create clocks for emp3 (Mariana Costa) - sometimes forgets lunch break register
                // Offset 10 forget return; offset 12 normal
                val normalClock = offset != 10
                val entryTime3 = getCalendarForTime(loopCal, 8, 15, offset).timeInMillis
                val lunchOutTime3 = if (normalClock) getCalendarForTime(loopCal, 12, 30, offset).timeInMillis else null
                val lunchReturnTime3 = if (normalClock) getCalendarForTime(loopCal, 13, 30, offset).timeInMillis else null
                val exitTime3 = getCalendarForTime(loopCal, 17, 15, offset).timeInMillis

                timeRecordDao.insertTimeRecord(
                    TimeRecord(
                        employeeId = emp3Id,
                        date = dateStr,
                        entryTime = entryTime3,
                        lunchOutTime = lunchOutTime3,
                        lunchReturnTime = lunchReturnTime3,
                        exitTime = exitTime3,
                        entryLocation = baseLoc,
                        lunchOutLocation = if (lunchOutTime3 != null) baseLoc else null,
                        lunchReturnLocation = if (lunchReturnTime3 != null) baseLoc else null,
                        exitLocation = baseLoc,
                        isOffline = true
                    )
                )
            }
        }
    }

    private fun getCalendarForTime(baseCal: Calendar, hour: Int, minute: Int, seed: Int): Calendar {
        val cal = baseCal.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, hour)
        // Add tiny variation
        val varMin = minute + (seed % 7) - 3
        cal.set(Calendar.MINUTE, varMin.coerceIn(0, 59))
        cal.set(Calendar.SECOND, seed % 60)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    suspend fun resolveBucketId(companyCode: String, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val cleanCode = companyCode.lowercase().trim()
        val getUrl = URL("https://keyvalue.immanuel.co/api/KeyVal/GetValue/6q25whwl/$cleanCode")
        
        var existingBucketId: String? = null
        if (!forceRefresh) {
            try {
                val conn = getUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var res = reader.readText().trim()
                    reader.close()
                    if (res.startsWith("\"") && res.endsWith("\"")) {
                        res = res.substring(1, res.length - 1)
                    }
                    if (res.isNotBlank() && res != "null" && res != "regponto_fallback") {
                        existingBucketId = res
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (existingBucketId != null) {
            return@withContext existingBucketId
        }
        
        // Let's create a bucket on kvdb.io
        var newBucketId: String? = null
        try {
            val kvdbPostUrl = URL("https://kvdb.io/")
            val conn = kvdbPostUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.outputStream.write(ByteArray(0))
            if (conn.responseCode == HttpURLConnection.HTTP_OK || conn.responseCode == HttpURLConnection.HTTP_CREATED) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val res = reader.readText().trim()
                reader.close()
                if (res.isNotBlank()) {
                    newBucketId = res
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val finalBucketId = newBucketId ?: "regponto_fallback"
        
        try {
            val updateUrl = URL("https://keyvalue.immanuel.co/api/KeyVal/UpdateValue/6q25whwl/$cleanCode/$finalBucketId")
            val conn = updateUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        finalBucketId
    }

    suspend fun syncWithCloud(companyCode: String) {
        performSync(companyCode, false)
    }

    private suspend fun performSync(companyCode: String, forceRefreshBucket: Boolean): Unit = withContext(Dispatchers.IO) {
        val bucketId = resolveBucketId(companyCode, forceRefreshBucket)
        val url = URL("https://kvdb.io/$bucketId/sync_payload")
        
        // 1. Fetch current cloud data (GET)
        var cloudJsonString: String? = null
        var getResponseCode = -1
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            getResponseCode = connection.responseCode
            if (getResponseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                cloudJsonString = sb.toString()
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (getResponseCode == HttpURLConnection.HTTP_NOT_FOUND && !forceRefreshBucket) {
            // Self-heal: Create a new bucket and retry sync!
            performSync(companyCode, true)
            return@withContext
        }
        
        // 2. Fetch all local employees and records
        val localEmployeesList = employeeDao.getAllEmployeesList()
        val localRecordsList = timeRecordDao.getAllTimeRecordsList()

        // 3. Parse cloud data
        val cloudEmployees = mutableListOf<Employee>()
        val cloudRecords = mutableListOf<JSONObject>()
        
        if (!cloudJsonString.isNullOrBlank()) {
            try {
                val rootObj = JSONObject(cloudJsonString)
                val empArray = rootObj.optJSONArray("employees")
                if (empArray != null) {
                    for (i in 0 until empArray.length()) {
                        val obj = empArray.getJSONObject(i)
                        cloudEmployees.add(
                            Employee(
                                name = obj.getString("name"),
                                position = obj.getString("position"),
                                role = obj.getString("role"),
                                pin = obj.getString("pin"),
                                email = obj.optString("email", ""),
                                active = obj.optBoolean("active", true)
                            )
                        )
                    }
                }
                val recArray = rootObj.optJSONArray("records")
                if (recArray != null) {
                    for (i in 0 until recArray.length()) {
                        cloudRecords.add(recArray.getJSONObject(i))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Merge Employees
        for (cloudEmp in cloudEmployees) {
            val existingLocal = employeeDao.getEmployeeByPin(cloudEmp.pin)
            if (existingLocal == null) {
                employeeDao.insertEmployee(cloudEmp)
            } else {
                val updated = existingLocal.copy(
                    name = cloudEmp.name,
                    position = cloudEmp.position,
                    role = cloudEmp.role,
                    email = cloudEmp.email,
                    active = cloudEmp.active
                )
                employeeDao.updateEmployee(updated)
            }
        }

        // 5. Merge Records
        val activeEmployees = employeeDao.getAllEmployeesList()
        val pinToLocalIdMap = activeEmployees.associateBy({ it.pin }, { it.id })

        for (cloudRecObj in cloudRecords) {
            val pin = cloudRecObj.optString("employeePin", "")
            val localId = pinToLocalIdMap[pin] ?: continue

            val dateStr = cloudRecObj.getString("date")
            val existingRecord = timeRecordDao.getTimeRecordForEmployeeAndDate(localId, dateStr)

            val cloudEntry = if (cloudRecObj.isNull("entryTime")) null else cloudRecObj.getLong("entryTime")
            val cloudLunchOut = if (cloudRecObj.isNull("lunchOutTime")) null else cloudRecObj.getLong("lunchOutTime")
            val cloudLunchReturn = if (cloudRecObj.isNull("lunchReturnTime")) null else cloudRecObj.getLong("lunchReturnTime")
            val cloudExit = if (cloudRecObj.isNull("exitTime")) null else cloudRecObj.getLong("exitTime")

            val cloudEntryLoc = cloudRecObj.optString("entryLocation", null)
            val cloudLunchOutLoc = cloudRecObj.optString("lunchOutLocation", null)
            val cloudLunchReturnLoc = cloudRecObj.optString("lunchReturnLocation", null)
            val cloudExitLoc = cloudRecObj.optString("exitLocation", null)
            val cloudNotes = cloudRecObj.optString("notes", null)

            if (existingRecord == null) {
                val newRecord = TimeRecord(
                    employeeId = localId,
                    date = dateStr,
                    entryTime = cloudEntry,
                    lunchOutTime = cloudLunchOut,
                    lunchReturnTime = cloudLunchReturn,
                    exitTime = cloudExit,
                    entryLocation = cloudEntryLoc?.takeIf { it != "null" && it != "" },
                    lunchOutLocation = cloudLunchOutLoc?.takeIf { it != "null" && it != "" },
                    lunchReturnLocation = cloudLunchReturnLoc?.takeIf { it != "null" && it != "" },
                    exitLocation = cloudExitLoc?.takeIf { it != "null" && it != "" },
                    isOffline = false,
                    notes = cloudNotes?.takeIf { it != "null" && it != "" }
                )
                timeRecordDao.insertTimeRecord(newRecord)
            } else {
                val merged = if (existingRecord.isOffline) {
                    // This record has unsynced local edits, preserve local data and fill in any missing parts from the cloud
                    existingRecord.copy(
                        entryTime = existingRecord.entryTime ?: cloudEntry,
                        lunchOutTime = existingRecord.lunchOutTime ?: cloudLunchOut,
                        lunchReturnTime = existingRecord.lunchReturnTime ?: cloudLunchReturn,
                        exitTime = existingRecord.exitTime ?: cloudExit,
                        entryLocation = existingRecord.entryLocation ?: cloudEntryLoc?.takeIf { it != "null" && it != "" },
                        lunchOutLocation = existingRecord.lunchOutLocation ?: cloudLunchOutLoc?.takeIf { it != "null" && it != "" },
                        lunchReturnLocation = existingRecord.lunchReturnLocation ?: cloudLunchReturnLoc?.takeIf { it != "null" && it != "" },
                        exitLocation = existingRecord.exitLocation ?: cloudExitLoc?.takeIf { it != "null" && it != "" },
                        notes = existingRecord.notes ?: cloudNotes?.takeIf { it != "null" && it != "" },
                        isOffline = false
                    )
                } else {
                    // This record was already synced previously. We strictly overwrite with the server's cloud data so that any foreign register/edit from other devices will instantly sync to this device!
                    existingRecord.copy(
                        entryTime = cloudEntry,
                        lunchOutTime = cloudLunchOut,
                        lunchReturnTime = cloudLunchReturn,
                        exitTime = cloudExit,
                        entryLocation = cloudEntryLoc?.takeIf { it != "null" && it != "" },
                        lunchOutLocation = cloudLunchOutLoc?.takeIf { it != "null" && it != "" },
                        lunchReturnLocation = cloudLunchReturnLoc?.takeIf { it != "null" && it != "" },
                        exitLocation = cloudExitLoc?.takeIf { it != "null" && it != "" },
                        notes = cloudNotes?.takeIf { it != "null" && it != "" },
                        isOffline = false
                    )
                }
                timeRecordDao.updateTimeRecord(merged)
            }
        }

        // 6. Build final merged payload to PUT back to cloud
        val finalUrl = URL("https://kvdb.io/$bucketId/sync_payload")
        
        val finalEmployees = employeeDao.getAllEmployeesList()
        val idToPinMap = finalEmployees.associateBy({ it.id }, { it.pin })
        val finalRecords = timeRecordDao.getAllTimeRecordsList()

        val rootObj = JSONObject()
        
        val empsArray = JSONArray()
        for (emp in finalEmployees) {
            val obj = JSONObject()
            obj.put("name", emp.name)
            obj.put("position", emp.position)
            obj.put("role", emp.role)
            obj.put("pin", emp.pin)
            obj.put("email", emp.email)
            obj.put("active", emp.active)
            empsArray.put(obj)
        }
        rootObj.put("employees", empsArray)

        val recsArray = JSONArray()
        for (rec in finalRecords) {
            val pin = idToPinMap[rec.employeeId] ?: continue
            val obj = JSONObject()
            obj.put("employeePin", pin)
            obj.put("date", rec.date)
            obj.put("entryTime", rec.entryTime ?: JSONObject.NULL)
            obj.put("lunchOutTime", rec.lunchOutTime ?: JSONObject.NULL)
            obj.put("lunchReturnTime", rec.lunchReturnTime ?: JSONObject.NULL)
            obj.put("exitTime", rec.exitTime ?: JSONObject.NULL)
            obj.put("entryLocation", rec.entryLocation ?: JSONObject.NULL)
            obj.put("lunchOutLocation", rec.lunchOutLocation ?: JSONObject.NULL)
            obj.put("lunchReturnLocation", rec.lunchReturnLocation ?: JSONObject.NULL)
            obj.put("exitLocation", rec.exitLocation ?: JSONObject.NULL)
            obj.put("notes", rec.notes ?: JSONObject.NULL)
            obj.put("isOffline", false)
            recsArray.put(obj)
        }
        rootObj.put("records", recsArray)

        // 7. PUT merged payload to Cloud
        val putConnection = finalUrl.openConnection() as HttpURLConnection
        putConnection.requestMethod = "PUT"
        putConnection.doOutput = true
        putConnection.connectTimeout = 10000
        putConnection.readTimeout = 10000
        putConnection.setRequestProperty("Content-Type", "application/json")

        val writer = OutputStreamWriter(putConnection.outputStream, "UTF-8")
        writer.write(rootObj.toString())
        writer.flush()
        writer.close()

        val putResponseCode = putConnection.responseCode
        putConnection.disconnect()
        
        if (putResponseCode == HttpURLConnection.HTTP_NOT_FOUND && !forceRefreshBucket) {
            // Self-heal: Create a new bucket and retry sync!
            performSync(companyCode, true)
            return@withContext
        }
        
        if (putResponseCode != HttpURLConnection.HTTP_OK && putResponseCode != HttpURLConnection.HTTP_CREATED && putResponseCode != 204) {
            throw Exception("Falha ao salvar dados na nuvem: HTTP $putResponseCode")
        }
    }
}
