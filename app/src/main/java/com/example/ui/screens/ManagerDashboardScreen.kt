package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Employee
import com.example.data.entity.TimeRecord
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerDashboardScreen(
    viewModel: AttendanceViewModel,
    manager: Employee,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var managerTab by remember { mutableStateOf("relatorios") } // "relatorios", "equipe"
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }

    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Painel do Gestor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Olá, ${manager.name}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    val companyCode by viewModel.companyCode.collectAsState()
                    val isSyncing by viewModel.isSyncing.collectAsState()
                    
                    if (companyCode.isNotBlank()) {
                        val lastSyncTime by viewModel.lastSyncTime.collectAsState()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (lastSyncTime != null) {
                                val lastSyncStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime!!))
                                Text(
                                    text = "Auto ($lastSyncStr) ",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = { viewModel.syncWithCloud() }, enabled = !isSyncing) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = "Sincronizar Nuvem",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.exportReportToCSV(context) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Exportar CSV/Planilha",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onBackToLogin()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Sair",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = managerTab == "relatorios",
                    onClick = { managerTab = "relatorios" },
                    icon = { Icon(Icons.Filled.Assessment, contentDescription = "Relatórios") },
                    label = { Text("Relatórios", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("manager_nav_reports")
                )
                NavigationBarItem(
                    selected = managerTab == "equipe",
                    onClick = { managerTab = "equipe" },
                    icon = { Icon(Icons.Filled.People, contentDescription = "Equipe") },
                    label = { Text("Equipe", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("manager_nav_staff")
                )
            }
        },
        floatingActionButton = {
            if (managerTab == "equipe") {
                ExtendedFloatingActionButton(
                    onClick = { showAddEmployeeDialog = true },
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("Cadastrar", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_employee_fab")
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.exportReportToCSV(context) },
                    icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                    text = { Text("Exportar Planilha", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("export_csv_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (managerTab) {
                "relatorios" -> {
                    ManagerReportsPanel(viewModel = viewModel)
                }
                "equipe" -> {
                    ManagerStaffPanel(
                        viewModel = viewModel,
                        onEditEmployee = { emp -> employeeToEdit = emp }
                    )
                }
            }

            // Register Employee Dialog form popup
            if (showAddEmployeeDialog) {
                AddEmployeeDialog(
                    onDismiss = { showAddEmployeeDialog = false },
                    onConfirm = { name, pos, role, pin, email ->
                        viewModel.addEmployee(name, pos, role, pin, email)
                        showAddEmployeeDialog = false
                    }
                )
            }

            // Edit Employee Dialog form popup
            if (employeeToEdit != null) {
                EditEmployeeDialog(
                    employee = employeeToEdit!!,
                    onDismiss = { employeeToEdit = null },
                    onConfirm = { id, name, pos, role, pin, email ->
                        viewModel.editEmployee(id, name, pos, role, pin, email)
                        employeeToEdit = null
                    },
                    onDelete = { emp ->
                        viewModel.deleteEmployee(emp)
                        employeeToEdit = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerReportsPanel(viewModel: AttendanceViewModel) {
    val allRecords by viewModel.allTimeRecords.collectAsState()
    val allEmployees by viewModel.allEmployees.collectAsState()
    
    // Filters state
    var selectedDateStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Aggregate statistics today
    val todayRecords = remember(allRecords, selectedDateStr) {
        allRecords.filter { it.date == selectedDateStr }
    }

    // Delays counts (Entry after 8:05 AM is delay)
    val delaysCount = remember(todayRecords) {
        todayRecords.count { record ->
            record.entryTime?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val min = cal.get(Calendar.MINUTE)
                (hour > 8) || (hour == 8 && min > 5)
            } ?: false
        }
    }

    // Absences: Punctual employees who have not clocked in yet today (exclude managers)
    val absencesCount = remember(todayRecords, allEmployees) {
        val workers = allEmployees.filter { it.role == "FUNCIONARIO" }
        val clockedInIds = todayRecords.map { it.employeeId }.toSet()
        workers.filter { it.id !in clockedInIds }.size
    }

    val displayLogs = remember(todayRecords, allEmployees) {
        allEmployees.filter { it.role == "FUNCIONARIO" }.map { emp ->
            val log = todayRecords.find { it.employeeId == emp.id }
            emp to log
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date selector header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePickerDialog = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "DATA DE CONSULTA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = viewModel.formatFullDate(selectedDateStr),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = "Escolher Data",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Monitoring Summary stats cards Today
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Delays Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (delaysCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (delaysCount > 0) ErrorRed.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (delaysCount > 0) ErrorRed else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Atrasos Hoje", fontSize = 12.sp, color = Color.Gray)
                        Text("$delaysCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (delaysCount > 0) ErrorRed else Color.Black)
                    }
                }

                // Absences Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (absencesCount > 0) WarningOrange.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (absencesCount > 0) WarningOrange.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Filled.Snooze,
                            contentDescription = null,
                            tint = if (absencesCount > 0) WarningOrange else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Faltas / S. Registro", fontSize = 12.sp, color = Color.Gray)
                        Text("$absencesCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (absencesCount > 0) WarningOrange else Color.Black)
                    }
                }
            }
        }

        // Detailed Today's Clock Table
        item {
            Text(
                "Registros consolidados do contingente",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (displayLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sem funcionários cadastrados.", color = Color.Gray)
                }
            }
        } else {
            items(displayLogs) { (emp, log) ->
                ManagerLogItemCard(viewModel = viewModel, employee = emp, record = log, dateStr = selectedDateStr)
            }
        }
    }

    // Native Datepicker view selection
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        selectedDateStr = sdf.format(Date(millis))
                    }
                    showDatePickerDialog = false
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("Selecione a data de relatório", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
            )
        }
    }
}

@Composable
fun ManagerLogItemCard(
    viewModel: AttendanceViewModel,
    employee: Employee,
    record: TimeRecord?,
    dateStr: String
) {
    var showEditShiftDialog by remember { mutableStateOf(false) }

    // Check if employee clocked in late (after 08:05 AM is late)
    val isLate = remember(record) {
        record?.entryTime?.let {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            (hour > 8) || (hour == 8 && min > 5)
        } ?: false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header information row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(employee.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(employee.position, fontSize = 11.sp, color = Color.Gray)
                }

                // Delay / Missing badge warnings
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                record == null -> WarningOrange.copy(alpha = 0.1f)
                                isLate -> ErrorRed.copy(alpha = 0.1f)
                                else -> SuccessGreen.copy(alpha = 0.1f)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            record == null -> "FALTA / S. REGISTRO"
                            isLate -> "ATRASO DETECTADO"
                            else -> "PONTUAL / INTEGRADO"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            record == null -> WarningOrange
                            isLate -> ErrorRed
                            else -> SuccessGreen
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timestamps line breakdown
            if (record != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ClockUnitDisplay("Entrada", record.entryTime?.let { viewModel.formatTime(it) } ?: "--:--", isLate)
                    ClockUnitDisplay("Almoço", record.lunchOutTime?.let { viewModel.formatTime(it) } ?: "--:--")
                    ClockUnitDisplay("Retorno", record.lunchReturnTime?.let { viewModel.formatTime(it) } ?: "--:--")
                    ClockUnitDisplay("Saída", record.exitTime?.let { viewModel.formatTime(it) } ?: "--:--")
                }

                val hoursText = remember(record) {
                    viewModel.calculateWorkedAndLunchDurations(record).first
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total Trabalhado No Dia:",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        hoursText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (record.notes != null) {
                    Text(
                        "📝 OBS: ${record.notes}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = ErrorRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    "⚠️ Nenhum ponto batido neste dia.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Adjust/Define shift action row (interactive for shifts)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { showEditShiftDialog = true },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar Ponto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Definir/Ajustar Horário",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // WEEKLY SUMMARY BLOCK FOR THE CHOSEN EMPLOYEE
            val allRecords by viewModel.allTimeRecords.collectAsState()
            val satToggledState by viewModel.saturdayWorkingToggled.collectAsState()
            val empRecords = remember(allRecords, employee.id) {
                allRecords.filter { it.employeeId == employee.id }
            }
            val weeklySummary = remember(empRecords, satToggledState) {
                viewModel.getWeeklySummary(employee.id, empRecords)
            }
            val totalHours = remember(weeklySummary) {
                viewModel.getWeeklyTotalWorkedText(weeklySummary)
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RESUMO SEMANAL (SEG - SÁB)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "Total: $totalHours",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeklySummary.forEach { dayInfo ->
                    val displayLabel = if (dayInfo.isToday) "Hoje" else dayInfo.dayLabel
                    WeeklyBentoBar(
                        hours = dayInfo.workedFormatted,
                        label = displayLabel,
                        workedToday = dayInfo.workedFormatted,
                        isToday = dayInfo.isToday,
                        isSaturday = dayInfo.isSaturday,
                        saturdayWorking = dayInfo.saturdayWorking,
                        onClick = null, // Saturday toggle is NOT clickable for managers
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showEditShiftDialog) {
        DefineShiftHoursDialog(
            employee = employee,
            dateStr = dateStr,
            onDismiss = { showEditShiftDialog = false },
            onConfirm = { entryH, entryM, exitH, exitM ->
                viewModel.changeTimeRecordForEmployee(employee.id, dateStr, entryH, entryM, exitH, exitM)
                showEditShiftDialog = false
            }
        )
    }
}

@Composable
fun DefineShiftHoursDialog(
    employee: Employee,
    dateStr: String,
    onDismiss: () -> Unit,
    onConfirm: (entryHour: Int?, entryMin: Int?, exitHour: Int?, exitMin: Int?) -> Unit
) {
    var setEntry by remember { mutableStateOf(true) }
    var entryHour by remember { mutableStateOf("08") }
    var entryMinute by remember { mutableStateOf("00") }

    var setExit by remember { mutableStateOf(true) }
    var exitHour by remember { mutableStateOf("18") }
    var exitMinute by remember { mutableStateOf("00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Definir Horário de Turno", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Colaborador: ${employee.name}\nDia: $dateStr",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                // Entrance section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Horário de Entrada", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = setEntry, onCheckedChange = { setEntry = it })
                            Text("Definir", fontSize = 12.sp)
                        }
                    }
                    if (setEntry) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = entryHour,
                                onValueChange = { if (it.length <= 2 && it.all { ch -> ch.isDigit() }) entryHour = it },
                                label = { Text("Hora") },
                                placeholder = { Text("08") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            OutlinedTextField(
                                value = entryMinute,
                                onValueChange = { if (it.length <= 2 && it.all { ch -> ch.isDigit() }) entryMinute = it },
                                label = { Text("Min") },
                                placeholder = { Text("00") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Exit section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Horário de Saída", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = setExit, onCheckedChange = { setExit = it })
                            Text("Definir", fontSize = 12.sp)
                        }
                    }
                    if (setExit) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = exitHour,
                                onValueChange = { if (it.length <= 2 && it.all { ch -> ch.isDigit() }) exitHour = it },
                                label = { Text("Hora") },
                                placeholder = { Text("18") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            OutlinedTextField(
                                value = exitMinute,
                                onValueChange = { if (it.length <= 2 && it.all { ch -> ch.isDigit() }) exitMinute = it },
                                label = { Text("Min") },
                                placeholder = { Text("00") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val eh = if (setEntry) entryHour.toIntOrNull() else null
                    val em = if (setEntry) entryMinute.toIntOrNull() else null
                    val xh = if (setExit) exitHour.toIntOrNull() else null
                    val xm = if (setExit) exitMinute.toIntOrNull() else null

                    onConfirm(eh, em, xh, xm)
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ClockUnitDisplay(title: String, time: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 9.sp, color = Color.Gray)
        Text(
            time,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) ErrorRed else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ManagerStaffPanel(
    viewModel: AttendanceViewModel,
    onEditEmployee: (Employee) -> Unit
) {
    val allEmployees by viewModel.allEmployees.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Gerenciamento de Colaboradores",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        items(allEmployees) { employee ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditEmployee(employee) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                employee.name.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                        Column {
                            Text(employee.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${employee.position} • PIN: ${employee.pin}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (employee.role == "GERENTE") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                employee.role,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (employee.role == "GERENTE") MaterialTheme.colorScheme.secondary else Color.Gray
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEmployeeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, position: String, role: String, pin: String, email: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("FUNCIONARIO") }
    var pin by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar Colaborador", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_name_field")
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Cargo / Posição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_position_field")
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pin = it },
                    label = { Text("PIN de segurança") },
                    placeholder = { Text("Ex: 1234") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_pin_field")
                )
                
                Text("Nível de Acesso", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == "FUNCIONARIO",
                        onClick = { role = "FUNCIONARIO" },
                        label = { Text("Funcionário") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = role == "GERENTE",
                        onClick = { role = "GERENTE" },
                        label = { Text("Gerente") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, position, role, pin, email) },
                enabled = name.isNotBlank() && position.isNotBlank() && pin.length == 4,
                modifier = Modifier.testTag("add_confirm_button")
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEmployeeDialog(
    employee: Employee,
    onDismiss: () -> Unit,
    onConfirm: (id: Int, name: String, position: String, role: String, pin: String, email: String) -> Unit,
    onDelete: (Employee) -> Unit
) {
    var name by remember { mutableStateOf(employee.name) }
    var position by remember { mutableStateOf(employee.position) }
    var role by remember { mutableStateOf(employee.role) }
    var pin by remember { mutableStateOf(employee.pin) }
    var email by remember { mutableStateOf(employee.email) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Colaborador", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_name_field")
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Cargo / Posição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_position_field")
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pin = it },
                    label = { Text("PIN de segurança") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_pin_field")
                )

                Text("Nível de Acesso", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == "FUNCIONARIO",
                        onClick = { role = "FUNCIONARIO" },
                        label = { Text("Funcionário") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = role == "GERENTE",
                        onClick = { role = "GERENTE" },
                        label = { Text("Gerente") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onDelete(employee) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("delete_employee_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Excluir Colaborador(a)", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(employee.id, name, position, role, pin, email) },
                enabled = name.isNotBlank() && position.isNotBlank() && pin.length == 4,
                modifier = Modifier.testTag("edit_confirm_button")
            ) {
                Text("Salvar Alterações")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
