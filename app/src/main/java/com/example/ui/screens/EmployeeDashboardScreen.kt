package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Employee
import com.example.data.entity.TimeRecord
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDashboardScreen(
    viewModel: AttendanceViewModel,
    employee: Employee,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf("registro") } // "registro", "perfil"
    var showHistorySheet by remember { mutableStateOf(false) }

    val statusMessage by viewModel.statusMessage.collectAsState()
    val todayRecord by viewModel.todayRecord.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Trigger snackbar alerts dynamically
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Registro de Ponto",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    val companyCode by viewModel.companyCode.collectAsState()
                    val isSyncing by viewModel.isSyncing.collectAsState()
                    
                    if (companyCode.isNotBlank()) {
                        IconButton(onClick = { viewModel.syncWithCloud() }, enabled = !isSyncing) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Sincronizar Nuvem",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = { 
                            showNotificationsDialog = true 
                            viewModel.markAllNotificationsAsRead(employee.id)
                        }) {
                            Icon(
                                imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Notificações",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(9.dp)
                                    .background(Color.Red, shape = CircleShape)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "registro",
                    onClick = { currentTab = "registro" },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Registro") },
                    label = { Text("Registro", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_item_registro")
                )
                NavigationBarItem(
                    selected = currentTab == "perfil",
                    onClick = { currentTab = "perfil" },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
                    label = { Text("Perfil", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_item_perfil")
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
            when (currentTab) {
                "registro" -> {
                    PunchClockTerminal(
                        viewModel = viewModel,
                        employee = employee,
                        todayRecord = todayRecord,
                        onOpenHistory = { showHistorySheet = true }
                    )
                }
                "perfil" -> {
                    EmployeeProfileTab(
                        viewModel = viewModel,
                        employee = employee,
                        onLogout = {
                            viewModel.logout()
                            onBackToLogin()
                        }
                    )
                }
            }

            // Sheet overlay for detailed time clock history
            if (showHistorySheet) {
                ModalBottomSheet(
                    onDismissRequest = { showHistorySheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    EmployeeHistoryLookupSheet(
                        viewModel = viewModel,
                        employee = employee,
                        onDismiss = { showHistorySheet = false }
                    )
                }
            }

            // Dialog for showing detailed notifications / shift shifts changes
            if (showNotificationsDialog) {
                AlertDialog(
                    onDismissRequest = { showNotificationsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Avisos de Escala", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        if (notifications.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nenhuma notificação de escala recebida.",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(notifications) { notif ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (!notif.isRead) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            }
                                        ),
                                        border = if (!notif.isRead) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                        } else null
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = notif.message,
                                                fontSize = 12.sp,
                                                fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val timeLabel = remember(notif.timestamp) {
                                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(notif.timestamp))
                                            }
                                            Text(
                                                text = timeLabel,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showNotificationsDialog = false }) {
                            Text("Fechar")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PunchClockTerminal(
    viewModel: AttendanceViewModel,
    employee: Employee,
    todayRecord: TimeRecord?,
    onOpenHistory: () -> Unit
) {
    var workedTimeTicker by remember { mutableStateOf("00h 00m 00s") }
    var currentDateString by remember { mutableStateOf("Carregando...") }

    val satToggledState by viewModel.saturdayWorkingToggled.collectAsState()
    val mondayStr = remember { viewModel.getWeekMondayStr() }
    val isSaturdayToday = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY }
    val isSatWorking = remember(employee.id, mondayStr, satToggledState) {
        viewModel.isSaturdayWorking(employee.id, mondayStr)
    }
    val punchesEnabled = !isSaturdayToday || isSatWorking

    val userTimeRecords by viewModel.userTimeRecords.collectAsState()
    val tomorrowStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }
    val tomorrowRecord = remember(userTimeRecords, tomorrowStr) {
        userTimeRecords.find { it.date == tomorrowStr }
    }

    LaunchedEffect(todayRecord) {
        while (true) {
            val cal = Calendar.getInstance()
            currentDateString = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(cal.time)
            
            var workedMillis: Long = 0
            if (todayRecord != null) {
                val entry = todayRecord.entryTime
                val lunchOut = todayRecord.lunchOutTime
                val lunchRet = todayRecord.lunchReturnTime
                val exit = todayRecord.exitTime

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
            
            val totalSecs = workedMillis / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            
            workedTimeTicker = String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds)
            
            kotlinx.coroutines.delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-time Clock, Weekday, and Date header in the upper part
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDateString.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isSaturdayToday && !isSatWorking) "Folga (Fim de Semana)" else "Tempo de trabalho hoje",
                            fontSize = 11.sp,
                            color = if (isSaturdayToday && !isSatWorking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    
                    Text(
                        text = if (isSaturdayToday && !isSatWorking) "FOLGA" else workedTimeTicker,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSaturdayToday && !isSatWorking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
        }

        // Tomorrow's shift agenda warning alert
        if (tomorrowRecord != null && (tomorrowRecord.entryTime != null || tomorrowRecord.exitTime != null)) {
            val tomorrowEntryStr = tomorrowRecord.entryTime?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Folga"
            val tomorrowExitStr = tomorrowRecord.exitTime?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Folga"

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Avisar Amanhã",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Alerta de Horário: Amanhã",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "O seu gerente programou o seu horário de trabalho para amanhã de forma definida: Entrada às $tomorrowEntryStr - Saída às $tomorrowExitStr.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // If today is Saturday and declared as day off (Folga)
        if (isSaturdayToday && !isSatWorking) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = "Folga",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Dia de Folga Escolhido",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Os registros estão desativados devido à folga agendada para este sábado. Para trabalhar, mude para 'Trabalhar' no Resumo Semanal (Aba Perfil).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Grid (Interactive)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClockOptionButton(
                        label = "ENTRADA",
                        icon = Icons.AutoMirrored.Filled.Login,
                        isActive = punchesEnabled && (todayRecord == null),
                        onClick = { viewModel.clockIn() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clock_in_button")
                    )

                    ClockOptionButton(
                        label = "SAÍDA ALMOÇO",
                        icon = Icons.Default.Restaurant,
                        isActive = punchesEnabled && (todayRecord != null && todayRecord.lunchOutTime == null && todayRecord.exitTime == null),
                        onClick = { viewModel.lunchOut() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lunch_out_button")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClockOptionButton(
                        label = "RETORNO ALMOÇO",
                        icon = Icons.Default.Replay,
                        isActive = punchesEnabled && (todayRecord != null && todayRecord.lunchOutTime != null && todayRecord.lunchReturnTime == null),
                        onClick = { viewModel.lunchReturn() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lunch_return_button")
                    )

                    ClockOptionButton(
                        label = "SAÍDA FIM DIA",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        isActive = punchesEnabled && (todayRecord != null && todayRecord.exitTime == null && (todayRecord.lunchOutTime == null || todayRecord.lunchReturnTime != null)),
                        onClick = { viewModel.clockOut() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clock_out_button")
                    )
                }
            }
        }

        // Today's Clock details
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Registros de hoje",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Ver histórico",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenHistory() }
                            .padding(4.dp)
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        // Entry Item
                        PontoLogItem(
                            title = "Entrada",
                            icon = Icons.AutoMirrored.Filled.Login,
                            timestamp = todayRecord?.entryTime,
                            location = todayRecord?.entryLocation,
                            statusText = if (todayRecord?.entryTime != null) "No horário" else "Pendente",
                            isStatusActive = todayRecord?.entryTime != null
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Lunch Out Item
                        PontoLogItem(
                            title = "Saída Almoço",
                            icon = Icons.Default.Restaurant,
                            timestamp = todayRecord?.lunchOutTime,
                            location = todayRecord?.lunchOutLocation,
                            statusText = if (todayRecord?.lunchOutTime != null) "Registrado" else "Pendente",
                            isStatusActive = todayRecord?.lunchOutTime != null
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Lunch Return Item
                        PontoLogItem(
                            title = "Retorno Almoço",
                            icon = Icons.Default.Replay,
                            timestamp = todayRecord?.lunchReturnTime,
                            location = todayRecord?.lunchReturnLocation,
                            statusText = if (todayRecord?.lunchReturnTime != null) "Registrado" else "Pendente",
                            isStatusActive = todayRecord?.lunchReturnTime != null
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Clock-out Item
                        PontoLogItem(
                            title = "Saída Fim do Turno",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            timestamp = todayRecord?.exitTime,
                            location = todayRecord?.exitLocation,
                            statusText = if (todayRecord?.exitTime != null) "Registrado" else "Pendente",
                            isStatusActive = todayRecord?.exitTime != null
                        )
                    }
                }
            }
        }

        // Summary hours count banner
        item {
            val workedText = remember(workedTimeTicker) {
                workedTimeTicker.substringBeforeLast(" ")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "TOTAL DE HORAS HOJE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            workedText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClockOptionButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val borderStroke = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Surface(
        onClick = { if (isActive) onClick() },
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = containerColor,
        contentColor = contentColor,
        border = borderStroke,
        tonalElevation = if (isActive) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PontoLogItem(
    title: String,
    icon: ImageVector,
    timestamp: Long?,
    location: String?,
    statusText: String,
    isStatusActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isStatusActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = 0.2f
                        ), CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isStatusActive) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (timestamp != null && location != null) {
                    Text(
                        "📍 $location (Offline/Local)",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timestamp?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "--:--",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isStatusActive) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
            Text(
                text = statusText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isStatusActive) MaterialTheme.colorScheme.secondary else Color.Gray.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EmployeeProfileTab(
    viewModel: AttendanceViewModel,
    employee: Employee,
    onLogout: () -> Unit
) {
    var calendarSync by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile picture & info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(96.dp)) {
                    // Profile Circle init
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = employee.name.take(2).uppercase(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Edit Badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .align(Alignment.BottomEnd)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    employee.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    employee.position,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        // Bento Hour Progress Card
        item {
            val userTimeRecords by viewModel.userTimeRecords.collectAsState()
            val satToggledState by viewModel.saturdayWorkingToggled.collectAsState()
            val mondayStr = remember { viewModel.getWeekMondayStr() }

            val weeklySummary = remember(userTimeRecords, satToggledState) {
                viewModel.getWeeklySummary(employee.id, userTimeRecords)
            }
            val totalHours = remember(weeklySummary) {
                viewModel.getWeeklyTotalWorkedText(weeklySummary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "RESUMO SEMANAL (SEG - SÁB)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "Total: $totalHours",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
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
                            onClick = if (dayInfo.isSaturday) {
                                { viewModel.toggleSaturdayWorking(employee.id, mondayStr) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Google Agenda sync
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Column {
                            Text("Sincronizar com Google Agenda", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Importar horários automaticamente", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = calendarSync,
                        onCheckedChange = { calendarSync = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // Simulated Reminders list
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Lembretes de Ponto", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ReminderOptionRow("Início de Turno", "Notificar às 07:55")
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ReminderOptionRow("Saída para Almoço", "Desativado")
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ReminderOptionRow("Fim de Turno", "Notificar às 17:55")
                }
            }
        }

        // Log out button
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAIR DA CONTA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun WeeklyBentoBar(
    hours: String,
    label: String,
    workedToday: String,
    isToday: Boolean = false,
    isSaturday: Boolean = false,
    saturdayWorking: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Colors of Saturday: Blue for worked status, Red for day off (folga)
    val bg = when {
        isSaturday -> {
            if (saturdayWorking) {
                Color(0xFF2E66B4) // Modern blue for working Sábado
            } else {
                Color(0xFFCF3C3C) // Cozy red for off/folga Sábado
            }
        }
        isToday -> {
            MaterialTheme.colorScheme.primaryContainer
        }
        else -> {
            MaterialTheme.colorScheme.surfaceVariant
        }
    }

    val contentColor = when {
        isSaturday -> Color.White
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .then(clickableModifier)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Text(
                    text = if (isSaturday) {
                        if (saturdayWorking) "TRAB" else "FOLGA"
                    } else {
                        hours
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = contentColor
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = workedToday,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ReminderOptionRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeHistoryLookupSheet(
    viewModel: AttendanceViewModel,
    employee: Employee,
    onDismiss: () -> Unit
) {
    val historyFilter by viewModel.historyFilter.collectAsState()
    val rawRecords by viewModel.userTimeRecords.collectAsState()
    val customStart by viewModel.customStartDate.collectAsState()
    val customEnd by viewModel.customEndDate.collectAsState()

    // Filter local records dynamically using helper
    val filteredRecords = remember(rawRecords, historyFilter, customStart, customEnd) {
        viewModel.filterUserTimeRecords(rawRecords, historyFilter, customStart, customEnd)
    }

    var showDatePickerRange by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Histórico de Ponto",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Fechar")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Brazil Local Filter Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "HOJE" to "Hoje",
                "SEMANA" to "Semana",
                "MES" to "Mês",
                "PERSONALIZADO" to "Período"
            ).forEach { (key, display) ->
                val selected = historyFilter == key
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (key == "PERSONALIZADO") {
                            showDatePickerRange = true
                        } else {
                            viewModel.setHistoryFilter(key)
                        }
                    },
                    label = { Text(display, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (historyFilter == "PERSONALIZADO" && customStart != null && customEnd != null) {
            Text(
                text = "Filtrado de ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(customStart!!))} até ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(customEnd!!))}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { showDatePickerRange = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // History Table Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Data", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Ent", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Alm", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Ret", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Saí", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Total", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nenhum registro encontrado.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredRecords) { record ->
                    HistoryRowItem(viewModel, record)
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }

        // Native M3 DateRange Picker triggers
        if (showDatePickerRange) {
            val dateRangePickerState = rememberDateRangePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePickerRange = false },
                confirmButton = {
                    TextButton(onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            viewModel.setCustomPeriod(start, end)
                        }
                        showDatePickerRange = false
                    }) {
                        Text("Confirmar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerRange = false }) {
                        Text("Cancelar")
                    }
                }
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.weight(1f),
                    title = {
                        Text(
                            text = "Selecione o período",
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun HistoryRowItem(viewModel: AttendanceViewModel, record: TimeRecord) {
    val dateText = viewModel.reformatDateToBR(record.date)
    val entry = record.entryTime?.let { viewModel.formatTime(it) } ?: "--:--"
    val lunchOut = record.lunchOutTime?.let { viewModel.formatTime(it) } ?: "--:--"
    val lunchReturn = record.lunchReturnTime?.let { viewModel.formatTime(it) } ?: "--:--"
    val exit = record.exitTime?.let { viewModel.formatTime(it) } ?: "--:--"

    val totalWorkedText = remember(record) {
        viewModel.calculateWorkedAndLunchDurations(record).first
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(dateText, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(entry, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(lunchOut, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Text(lunchReturn, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Text(exit, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(
            totalWorkedText,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}
