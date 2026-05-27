package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.Employee
import com.example.data.entity.TimeRecord
import com.example.data.repository.TimeRecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimeRecordRepository
    private val sharedPrefs = application.getSharedPreferences("ponto_prefs", Context.MODE_PRIVATE)

    private val _rememberLogin = MutableStateFlow(sharedPrefs.getBoolean("remember_login", false))
    val rememberLogin: StateFlow<Boolean> = _rememberLogin.asStateFlow()

    private val _savedName = MutableStateFlow(sharedPrefs.getString("saved_name", "") ?: "")
    val savedName: StateFlow<String> = _savedName.asStateFlow()

    private val _savedPin = MutableStateFlow(sharedPrefs.getString("saved_pin", "") ?: "")
    val savedPin: StateFlow<String> = _savedPin.asStateFlow()

    private val _companyCode = MutableStateFlow(
        if (sharedPrefs.contains("company_code")) {
            sharedPrefs.getString("company_code", "") ?: ""
        } else {
            sharedPrefs.edit().putString("company_code", "GESPOOL").apply()
            "GESPOOL"
        }
    )
    val companyCode: StateFlow<String> = _companyCode.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TimeRecordRepository(db.employeeDao(), db.timeRecordDao())
        
        // Sync with cloud instantly on startup if key is configured
        val savedCode = sharedPrefs.getString("company_code", "") ?: ""
        if (savedCode.isNotBlank()) {
            viewModelScope.launch {
                try {
                    repository.syncWithCloud(savedCode)
                    _lastSyncTime.value = System.currentTimeMillis()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Periodic auto-sync worker: synchronizes automatically between different devices in the cloud every 30 seconds
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30000) // 30 seconds interval
                val currentCode = _companyCode.value
                if (currentCode.isNotBlank() && !_isSyncing.value) {
                    try {
                        repository.syncWithCloud(currentCode)
                        _lastSyncTime.value = System.currentTimeMillis()
                        // Ensure active session updates immediately with fetched cloud data
                        val user = _loggedInUser.value
                        if (user != null) {
                            loadEmployeeData(user.id)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun setRememberLogin(enabled: Boolean) {
        _rememberLogin.value = enabled
        sharedPrefs.edit().putBoolean("remember_login", enabled).apply()
        if (!enabled) {
            sharedPrefs.edit().remove("saved_name").remove("saved_pin").apply()
            _savedName.value = ""
            _savedPin.value = ""
        }
    }

    fun saveLoginCredentials(name: String, pin: String) {
        if (_rememberLogin.value) {
            sharedPrefs.edit()
                .putString("saved_name", name)
                .putString("saved_pin", pin)
                .apply()
            _savedName.value = name
            _savedPin.value = pin
        } else {
            sharedPrefs.edit().remove("saved_name").remove("saved_pin").apply()
            _savedName.value = ""
            _savedPin.value = ""
        }
    }

    fun setCompanyCode(code: String) {
        val cleanCode = code.trim().uppercase()
        sharedPrefs.edit().putString("company_code", cleanCode).apply()
        _companyCode.value = cleanCode
    }

    fun syncWithCloud() {
        val code = _companyCode.value
        if (code.isBlank()) {
            _statusMessage.value = "⚠️ Código da empresa não configurado."
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            try {
                repository.syncWithCloud(code)
                _lastSyncTime.value = System.currentTimeMillis()
                val currentUser = _loggedInUser.value
                if (currentUser != null) {
                    loadEmployeeData(currentUser.id)
                }
                _statusMessage.value = "✅ Sincronização em nuvem realizada com sucesso!"
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                _syncError.value = errMsg
                _statusMessage.value = "❌ Falha ao sincronizar: $errMsg"
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun triggerAutoSync() {
        val code = _companyCode.value
        if (code.isNotBlank()) {
            viewModelScope.launch {
                try {
                    repository.syncWithCloud(code)
                    _lastSyncTime.value = System.currentTimeMillis()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Active session states
    private val _loggedInUser = MutableStateFlow<Employee?>(null)
    val loggedInUser: StateFlow<Employee?> = _loggedInUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Global list of employees (for managers or switching users)
    val allEmployees: StateFlow<List<Employee>> = repository.allEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All time records (useful for global manager reports)
    val allTimeRecords: StateFlow<List<TimeRecord>> = repository.allTimeRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected employee's attendance logs
    private val _userTimeRecords = MutableStateFlow<List<TimeRecord>>(emptyList())
    val userTimeRecords: StateFlow<List<TimeRecord>> = _userTimeRecords.asStateFlow()

    // Status of today's time record for the logged-in employee
    private val _todayRecord = MutableStateFlow<TimeRecord?>(null)
    val todayRecord: StateFlow<TimeRecord?> = _todayRecord.asStateFlow()

    // Global action message (failures or successes)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Location configuration Simulation
    private val _simulatedGPS = MutableStateFlow(true)
    val simulatedGPS: StateFlow<Boolean> = _simulatedGPS.asStateFlow()

    private val _customCoordinates = MutableStateFlow("Escritório Central (São Paulo)")
    val customCoordinates: StateFlow<String> = _customCoordinates.asStateFlow()

    // History filter configurations
    private val _historyFilter = MutableStateFlow("SEMANA") // HOJE, SEMANA, MES, PERSONALIZADO
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    // Manager dashboard filters
    private val _managerSelectedEmployeeId = MutableStateFlow<Int?>(null) // null = Todos
    val managerSelectedEmployeeId: StateFlow<Int?> = _managerSelectedEmployeeId.asStateFlow()

    fun setSimulatedGPS(enabled: Boolean) {
        _simulatedGPS.value = enabled
    }

    fun setCustomCoordinates(label: String) {
        _customCoordinates.value = label
    }

    fun setHistoryFilter(filter: String) {
        _historyFilter.value = filter
    }

    fun setCustomPeriod(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
        _historyFilter.value = "PERSONALIZADO"
    }

    fun setManagerSelectedEmployeeId(id: Int?) {
        _managerSelectedEmployeeId.value = id
    }

    // PIN Authentication
    fun loginWithPin(pin: String) {
        viewModelScope.launch {
            _loginError.value = null
            val employee = repository.verifyPin(pin)
            if (employee != null) {
                _loggedInUser.value = employee
                loadEmployeeData(employee.id)
                _statusMessage.value = "Bem-vindo, ${employee.name}!"
            } else {
                _loginError.value = "PIN incorreto. Tente novamente."
            }
        }
    }

    // Name and PIN Authentication
    fun loginWithNameAndPin(name: String, pin: String) {
        viewModelScope.launch {
            _loginError.value = null
            if (name.isBlank() || pin.isBlank()) {
                _loginError.value = "Por favor, preencha o Nome e o PIN."
                return@launch
            }

            // Sync with cloud before checking Name/PIN (to load newly registered people instantly)
            val code = _companyCode.value
            if (code.isNotBlank()) {
                try {
                    repository.syncWithCloud(code)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val employee = repository.verifyNameAndPin(name, pin)
            if (employee != null) {
                saveLoginCredentials(name, pin)
                _loggedInUser.value = employee
                loadEmployeeData(employee.id)
                _statusMessage.value = "Bem-vindo, ${employee.name}!"
                triggerAutoSync()
            } else {
                _loginError.value = "Nome ou PIN incorretos. Tente novamente."
            }
        }
    }

    fun logout() {
        employeeDataJob?.cancel()
        employeeDataJob = null
        _loggedInUser.value = null
        _todayRecord.value = null
        _userTimeRecords.value = emptyList()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    private var employeeDataJob: kotlinx.coroutines.Job? = null

    private fun loadEmployeeData(employeeId: Int) {
        loadNotificationsForEmployee(employeeId)
        employeeDataJob?.cancel()
        employeeDataJob = viewModelScope.launch {
            repository.getTimeRecordsForEmployee(employeeId).collect { logs ->
                _userTimeRecords.value = logs
                
                // Extract today's entry
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayLog = logs.find { it.date == todayStr }
                _todayRecord.value = todayLog
            }
        }
    }

    // Refresh today's record status
    fun refreshTodayRecord() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val record = repository.getTimeRecordForEmployeeAndDate(user.id, todayStr)
            _todayRecord.value = record
        }
    }

    // Clock In Action
    fun clockIn() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val existingRecord = repository.getTimeRecordForEmployeeAndDate(user.id, todayStr)

            // Rule: cannot clock in twice in a row for the same day (or if already clocked in today)
            if (existingRecord != null && existingRecord.entryTime != null) {
                _statusMessage.value = "⚠️ Entrada já registrada para o dia de hoje!"
                return@launch
            }

            val timestamp = System.currentTimeMillis()
            val coord = if (_simulatedGPS.value) _customCoordinates.value else "GPS Ativo: -23.5505, -46.6333"

            val newRecord = TimeRecord(
                employeeId = user.id,
                date = todayStr,
                entryTime = timestamp,
                entryLocation = coord,
                isOffline = true // default state is stored locally offline
            )

            repository.insertTimeRecord(newRecord)
            loadEmployeeData(user.id)
            _statusMessage.value = "✅ Entrada registrada com sucesso às ${formatTime(timestamp)}!"
            triggerAutoSync()
        }
    }

    // Lunch Out Action
    fun lunchOut() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val record = repository.getTimeRecordForEmployeeAndDate(user.id, todayStr)

            // Rule: Cannot register lunch without clocking in first
            if (record == null || record.entryTime == null) {
                _statusMessage.value = "❌ Erro: Não é possível registrar saída de almoço sem uma Entrada registrada!"
                return@launch
            }

            if (record.lunchOutTime != null) {
                _statusMessage.value = "⚠️ Saída para almoço já registrada hoje!"
                return@launch
            }

            val timestamp = System.currentTimeMillis()
            val coord = if (_simulatedGPS.value) _customCoordinates.value else "GPS Ativo: -23.5505, -46.6333"

            val updatedRecord = record.copy(
                lunchOutTime = timestamp,
                lunchOutLocation = coord
            )

            repository.updateTimeRecord(updatedRecord)
            loadEmployeeData(user.id)
            _statusMessage.value = "🍽️ Almoço iniciado às ${formatTime(timestamp)}!"
            triggerAutoSync()
        }
    }

    // Lunch Return Action
    fun lunchReturn() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val record = repository.getTimeRecordForEmployeeAndDate(user.id, todayStr)

            // Rule: Cannot return from lunch without starting lunch first
            if (record == null || record.lunchOutTime == null) {
                _statusMessage.value = "❌ Erro: Registrar retorno de almoço sem saída prévia é bloqueado!"
                return@launch
            }

            if (record.lunchReturnTime != null) {
                _statusMessage.value = "⚠️ Retorno do almoço já registrado hoje!"
                return@launch
            }

            val timestamp = System.currentTimeMillis()
            val coord = if (_simulatedGPS.value) _customCoordinates.value else "GPS Ativo: -23.5505, -46.6333"

            val updatedRecord = record.copy(
                lunchReturnTime = timestamp,
                lunchReturnLocation = coord
            )

            repository.updateTimeRecord(updatedRecord)
            loadEmployeeData(user.id)
            _statusMessage.value = "💪 Retorno do almoço registrado às ${formatTime(timestamp)}!"
            triggerAutoSync()
        }
    }

    // Clock Out (Saída) Action
    fun clockOut() {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val record = repository.getTimeRecordForEmployeeAndDate(user.id, todayStr)

            if (record == null || record.entryTime == null) {
                _statusMessage.value = "❌ Erro: Você deve bater o ponto de Entrada antes de encerrar o expediente!"
                return@launch
            }

            // Rule: Cannot clock out for the day in lunch break without returning first
            if (record.lunchOutTime != null && record.lunchReturnTime == null) {
                _statusMessage.value = "❌ Erro: Encerrar expediente sem voltar do almoço é bloqueado!"
                return@launch
            }

            if (record.exitTime != null) {
                _statusMessage.value = "⚠️ Saída do turno já registrada para o dia de hoje!"
                return@launch
            }

            val timestamp = System.currentTimeMillis()
            val coord = if (_simulatedGPS.value) _customCoordinates.value else "GPS Ativo: -23.5505, -46.6333"

            val updatedRecord = record.copy(
                exitTime = timestamp,
                exitLocation = coord
            )

            repository.updateTimeRecord(updatedRecord)
            loadEmployeeData(user.id)
            _statusMessage.value = "🚪 Expediente encerrado às ${formatTime(timestamp)}! Bom descanso."
            triggerAutoSync()
        }
    }

    // Manager: Add Employee
    fun registerFirstAccess(name: String, role: String, pin: String, position: String = "", email: String = "") {
        viewModelScope.launch {
            if (name.isBlank() || pin.length < 4) {
                _loginError.value = "Dados de cadastro inválidos. Nome e PIN (mínimo 4 dígitos) são obrigatórios."
                return@launch
            }
            // Check if PIN exists
            val existing = repository.verifyPin(pin)
            if (existing != null) {
                _loginError.value = "Este PIN já está em uso por outro usuário."
                return@launch
            }
            
            val newEmp = Employee(
                name = name,
                position = if (position.isBlank()) {
                    if (role == "GERENTE") "Cargo de Gerência" else "Cargo de Operações"
                } else position,
                role = role,
                pin = pin,
                email = email
            )
            repository.insertEmployee(newEmp)
            _loginError.value = null
            _statusMessage.value = "Usuário '$name' cadastrado com sucesso! Agora efetue o login."
            triggerAutoSync()
        }
    }

    // Manager: Add Employee
    fun addEmployee(name: String, position: String, role: String, pin: String, email: String) {
        viewModelScope.launch {
            if (name.isBlank() || position.isBlank() || pin.length < 4) {
                _statusMessage.value = "❌ Erro: Nome, cargo e PIN válido (min 4 dígitos) necessários!"
                return@launch
            }
            
            // Uniqueness check for PIN
            val existing = repository.verifyPin(pin)
            if (existing != null) {
                _statusMessage.value = "❌ Erro: Este PIN já está em uso por outro funcionário!"
                return@launch
            }

            val newEmp = Employee(
                name = name,
                position = position,
                role = role,
                pin = pin,
                email = email
            )
            repository.insertEmployee(newEmp)
            _statusMessage.value = "👥 Funcionário(a) $name cadastrado(a) com sucesso!"
            triggerAutoSync()
        }
    }

    // Manager: Edit Employee
    fun editEmployee(id: Int, name: String, position: String, role: String, pin: String, email: String) {
        viewModelScope.launch {
            if (name.isBlank() || position.isBlank() || pin.length < 4) {
                _statusMessage.value = "❌ Erro: Dados inválidos!"
                return@launch
            }

            // Uniqueness check for PIN
            val existing = repository.verifyPin(pin)
            if (existing != null && existing.id != id) {
                _statusMessage.value = "❌ Erro: Este PIN já está em uso!"
                return@launch
            }

            val updatedEmp = Employee(
                id = id,
                name = name,
                position = position,
                role = role,
                pin = pin,
                email = email
            )
            repository.updateEmployee(updatedEmp)
            _statusMessage.value = "📝 Dados de $name atualizados!"
            triggerAutoSync()
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee)
            _statusMessage.value = "🗑️ Funcionário(a) ${employee.name} removido(a)."
            triggerAutoSync()
        }
    }

    // Export Reports to CSV and trigger standard Android share panel
    fun exportReportToCSV(context: Context) {
        viewModelScope.launch {
            try {
                val csvFile = File(context.cacheDir, "relatorio_ponto_${System.currentTimeMillis()}.csv")
                val writer = FileWriter(csvFile)
                
                // Write CSV headers
                writer.append("Funcionario,Cargo,Data,Entrada,Saida Almoço,Retorno Almoço,Saida Fim do Dia,Total Horas Trabalhadas,Tempo Almoço,GPS Localizacao,isOffline\n")
                
                val records = repository.allTimeRecords.first()
                val employees = repository.allEmployees.first()

                for (record in records) {
                    val emp = employees.find { it.id == record.employeeId } ?: continue
                    
                    val dateFormatted = reformatDateToBR(record.date)
                    val entryStr = record.entryTime?.let { formatTime(it) } ?: "--:--"
                    val lunchOutStr = record.lunchOutTime?.let { formatTime(it) } ?: "--:--"
                    val lunchReturnStr = record.lunchReturnTime?.let { formatTime(it) } ?: "--:--"
                    val exitStr = record.exitTime?.let { formatTime(it) } ?: "--:--"
                    
                    val totals = calculateWorkedAndLunchDurations(record)
                    val workedText = totals.first
                    val lunchText = totals.second

                    val locationStr = (record.entryLocation ?: "Simulado").replace(",", ";")
                    val isOfflineStr = if (record.isOffline) "Sim" else "Não"

                    writer.append("${emp.name},${emp.position},$dateFormatted,$entryStr,$lunchOutStr,$lunchReturnStr,$exitStr,$workedText,$lunchText,$locationStr,$isOfflineStr\n")
                }
                
                writer.flush()
                writer.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Relatório de Ponto - Geral")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Compartilhar Relatório CSV")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                _statusMessage.value = "📊 Relatório CSV gerado para compartilhamento!"
            } catch (e: Exception) {
                _statusMessage.value = "❌ Falha ao exportar relatório: ${e.localizedMessage}"
                e.printStackTrace()
            }
        }
    }

    // Dynamic calculations helper
    fun filterUserTimeRecords(logs: List<TimeRecord>, filter: String, startMillis: Long?, endMillis: Long?): List<TimeRecord> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (filter) {
            "HOJE" -> {
                val todayStr = dateFormat.format(Date())
                logs.filter { it.date == todayStr }
            }
            "SEMANA" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val mondayStart = cal.timeInMillis
                logs.filter { log ->
                    val dateObj = dateFormat.parse(log.date)
                    dateObj != null && dateObj.time >= mondayStart
                }
            }
            "MES" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val monthStart = cal.timeInMillis
                logs.filter { log ->
                    val dateObj = dateFormat.parse(log.date)
                    dateObj != null && dateObj.time >= monthStart
                }
            }
            "PERSONALIZADO" -> {
                if (startMillis == null || endMillis == null) {
                    logs
                } else {
                    // Normalize standard endMillis to end of that selected day
                    val endCal = Calendar.getInstance()
                    endCal.timeInMillis = endMillis
                    endCal.set(Calendar.HOUR_OF_DAY, 23)
                    endCal.set(Calendar.MINUTE, 59)
                    val endDayMillis = endCal.timeInMillis

                    logs.filter { log ->
                        val dateObj = dateFormat.parse(log.date)
                        dateObj != null && dateObj.time in startMillis..endDayMillis
                    }
                }
            }
            else -> logs
        }
    }

    // Auto calculate working times and break durations helper
    fun calculateWorkedAndLunchDurations(record: TimeRecord): Pair<String, String> {
        val entry = record.entryTime
        val lunchOut = record.lunchOutTime
        val lunchRet = record.lunchReturnTime
        val exit = record.exitTime

        var totalWorkedMillis: Long = 0
        var totalLunchMillis: Long = 0

        // Case 1: normal work day with completed lunch register
        if (entry != null && lunchOut != null && lunchRet != null && exit != null) {
            totalWorkedMillis = (lunchOut - entry) + (exit - lunchRet)
            totalLunchMillis = lunchRet - lunchOut
        }
        // Case 2: unfinished lunch (currently on lunch)
        else if (entry != null && lunchOut != null && lunchRet == null) {
            totalWorkedMillis = lunchOut - entry
            totalLunchMillis = System.currentTimeMillis() - lunchOut
        }
        // Case 3: lunch completed but shift still incomplete inside work
        else if (entry != null && lunchOut != null && lunchRet != null && exit == null) {
            totalWorkedMillis = (lunchOut - entry) + (System.currentTimeMillis() - lunchRet)
            totalLunchMillis = lunchRet - lunchOut
        }
        // Case 4: No lunch started yet, still working shift
        else if (entry != null && lunchOut == null && exit == null) {
            totalWorkedMillis = System.currentTimeMillis() - entry
        }
        // Case 5: Direct clock-out without lunch break at all
        else if (entry != null && lunchOut == null && exit != null) {
            totalWorkedMillis = exit - entry
        }

        // Format to string
        val workedText = formatDuration(totalWorkedMillis)
        val lunchText = formatDuration(totalLunchMillis)

        return Pair(workedText, lunchText)
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "0m"
        val totalMinutes = millis / 1000 / 60
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    fun formatTime(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    fun formatFullDate(dateStr: String): String {
        val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: return dateStr
        val brFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        val raw = brFormat.format(dateObj)
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun reformatDateToBR(dateStr: String): String {
        val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: return dateStr
        return SimpleDateFormat("dd/M", Locale.getDefault()).format(dateObj)
    }

    fun reformatFullDateToBR(dateStr: String): String {
        val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: return dateStr
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateObj)
    }

    // --- NEW SATURDAY WORKDAY & WEEKLY SUMMARY FEATURES ---

    data class WeeklyDayInfo(
        val dayLabel: String,         // Seg, Ter, Qua, Qui, Sex, Sáb
        val dateStr: String,          // yyyy-MM-dd
        val isToday: Boolean,
        val workedFormatted: String,   // hours worked formatted, e.g., "8h 15m" or "0h"
        val workedMillis: Long,
        val isSaturday: Boolean = false,
        val saturdayWorking: Boolean = false
    )

    private val _saturdayWorkingToggled = MutableStateFlow(false)
    val saturdayWorkingToggled: StateFlow<Boolean> = _saturdayWorkingToggled.asStateFlow()

    fun isSaturdayWorking(employeeId: Int, weekMondayStr: String): Boolean {
        // Retrieve Saturday worked state from SharedPreferences (with default as false / folga)
        return sharedPrefs.getBoolean("sat_work_${employeeId}_$weekMondayStr", false)
    }

    fun toggleSaturdayWorking(employeeId: Int, weekMondayStr: String) {
        val current = isSaturdayWorking(employeeId, weekMondayStr)
        sharedPrefs.edit().putBoolean("sat_work_${employeeId}_$weekMondayStr", !current).apply()
        _saturdayWorkingToggled.value = !_saturdayWorkingToggled.value
        // If current employee toggled, refresh employee data
        val user = _loggedInUser.value
        if (user != null && user.id == employeeId) {
            loadEmployeeData(user.id)
        }
    }

    fun getWeekMondayStr(): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    fun getWeeklySummary(employeeId: Int, logs: List<TimeRecord>): List<WeeklyDayInfo> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        val weekMondayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        val labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
        val summary = mutableListOf<WeeklyDayInfo>()
        
        val tempCal = cal.clone() as Calendar
        for (i in 0..5) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tempCal.time)
            val isToday = (dateStr == todayStr)
            val isSat = (i == 5)
            
            val record = logs.find { it.date == dateStr }
            var workedMillis: Long = 0
            if (record != null) {
                val entry = record.entryTime
                val lunchOut = record.lunchOutTime
                val lunchRet = record.lunchReturnTime
                val exit = record.exitTime
                
                if (entry != null) {
                    if (exit != null) {
                        workedMillis = if (lunchOut != null && lunchRet != null) {
                            (lunchOut - entry) + (exit - lunchRet)
                        } else if (lunchOut != null) {
                            lunchOut - entry
                        } else {
                            exit - entry
                        }
                    } else if (lunchRet != null) {
                        workedMillis = (lunchOut!! - entry) + (System.currentTimeMillis() - lunchRet)
                    } else if (lunchOut != null) {
                        workedMillis = lunchOut - entry
                    } else {
                        workedMillis = System.currentTimeMillis() - entry
                    }
                }
            }
            
            if (workedMillis < 0) workedMillis = 0
            
            // Format
            val totalMinutes = workedMillis / 1000 / 60
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            val workedText = if (workedMillis == 0L) "0h" else if (h > 0) "${h}h ${m}m" else "${m}m"
            
            val satWorking = if (isSat) isSaturdayWorking(employeeId, weekMondayStr) else false
            
            summary.add(
                WeeklyDayInfo(
                    dayLabel = labels[i],
                    dateStr = dateStr,
                    isToday = isToday,
                    workedFormatted = workedText,
                    workedMillis = workedMillis,
                    isSaturday = isSat,
                    saturdayWorking = satWorking
                )
            )
            
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return summary
    }

    fun getWeeklyTotalWorkedText(summary: List<WeeklyDayInfo>): String {
        val totalMillis = summary.sumOf { it.workedMillis }
        val totalMinutes = totalMillis / 1000 / 60
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return "${h}h ${m}m"
    }

    // --- MANAGER EDIT AND CUSTOM TIMESTAMP MANAGEMENT ---

    fun changeTimeRecordForEmployee(
        employeeId: Int,
        dateStr: String, // "yyyy-MM-dd"
        entryHour: Int?,
        entryMinute: Int?,
        exitHour: Int?,
        exitMinute: Int?
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateObj = sdf.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = dateObj
            
            val entryTimeMs = if (entryHour != null && entryMinute != null) {
                val entryCal = cal.clone() as Calendar
                entryCal.set(Calendar.HOUR_OF_DAY, entryHour)
                entryCal.set(Calendar.MINUTE, entryMinute)
                entryCal.set(Calendar.SECOND, 0)
                entryCal.set(Calendar.MILLISECOND, 0)
                entryCal.timeInMillis
            } else null

            val exitTimeMs = if (exitHour != null && exitMinute != null) {
                val exitCal = cal.clone() as Calendar
                exitCal.set(Calendar.HOUR_OF_DAY, exitHour)
                exitCal.set(Calendar.MINUTE, exitMinute)
                exitCal.set(Calendar.SECOND, 0)
                exitCal.set(Calendar.MILLISECOND, 0)
                exitCal.timeInMillis
            } else null

            val existing = repository.getTimeRecordForEmployeeAndDate(employeeId, dateStr)
            if (existing == null) {
                val newRecord = TimeRecord(
                    employeeId = employeeId,
                    date = dateStr,
                    entryTime = entryTimeMs,
                    exitTime = exitTimeMs,
                    isOffline = false
                )
                repository.insertTimeRecord(newRecord)
            } else {
                val updated = existing.copy(
                    entryTime = entryTimeMs ?: existing.entryTime,
                    exitTime = exitTimeMs ?: existing.exitTime,
                    isOffline = false
                )
                repository.updateTimeRecord(updated)
            }
            
            // Format schedule description for warning alert
            val dateFormatted = try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(parsed ?: Date())
            } catch (e: Exception) {
                dateStr
            }

            val entryFormatted = if (entryHour != null && entryMinute != null) {
                String.format("%02d:%02d", entryHour, entryMinute)
            } else "-"

            val exitFormatted = if (exitHour != null && exitMinute != null) {
                String.format("%02d:%02d", exitHour, exitMinute)
            } else "-"

            val notifMessage = "Horário de trabalho definido para o dia $dateFormatted: das $entryFormatted às $exitFormatted."
            addNotificationForEmployee(employeeId, dateStr, notifMessage)

            // Refresh employee data if logged in
            val currentUser = _loggedInUser.value
            if (currentUser != null && currentUser.id == employeeId) {
                loadEmployeeData(currentUser.id)
            }
            _statusMessage.value = "✅ Ponto definido com sucesso para o dia $dateStr!"
            triggerAutoSync()
        }
    }

    // --- NOTIFICATION SUITE ---

    data class ScheduleNotification(
        val id: String,
        val employeeId: Int,
        val dateStr: String,
        val message: String,
        val timestamp: Long,
        val isRead: Boolean = false
    )

    private val _notifications = MutableStateFlow<List<ScheduleNotification>>(emptyList())
    val notifications: StateFlow<List<ScheduleNotification>> = _notifications.asStateFlow()

    fun loadNotificationsForEmployee(employeeId: Int) {
        val jsonStr = sharedPrefs.getString("notifs_$employeeId", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ScheduleNotification>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ScheduleNotification(
                        id = obj.getString("id"),
                        employeeId = obj.getInt("employeeId"),
                        dateStr = obj.getString("dateStr"),
                        message = obj.getString("message"),
                        timestamp = obj.getLong("timestamp"),
                        isRead = obj.optBoolean("isRead", false)
                    )
                )
            }
            _notifications.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            _notifications.value = emptyList()
        }
    }

    fun addNotificationForEmployee(employeeId: Int, dateStr: String, message: String) {
        val currentJson = sharedPrefs.getString("notifs_$employeeId", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(currentJson)
            val newObj = org.json.JSONObject().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("employeeId", employeeId)
                put("dateStr", dateStr)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("isRead", false)
            }
            arr.put(newObj)
            sharedPrefs.edit().putString("notifs_$employeeId", arr.toString()).apply()
            
            val user = _loggedInUser.value
            if (user != null && user.id == employeeId) {
                loadNotificationsForEmployee(user.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun markAllNotificationsAsRead(employeeId: Int) {
        val currentJson = sharedPrefs.getString("notifs_$employeeId", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(currentJson)
            for (i in 0 until arr.length()) {
                arr.getJSONObject(i).put("isRead", true)
            }
            sharedPrefs.edit().putString("notifs_$employeeId", arr.toString()).apply()
            
            val user = _loggedInUser.value
            if (user != null && user.id == employeeId) {
                loadNotificationsForEmployee(user.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
