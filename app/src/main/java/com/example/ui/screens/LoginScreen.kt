package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AttendanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val allEmployees by viewModel.allEmployees.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    val rememberLogin by viewModel.rememberLogin.collectAsState()
    val savedName by viewModel.savedName.collectAsState()
    val savedPin by viewModel.savedPin.collectAsState()
    val companyCode by viewModel.companyCode.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 for Login, 1 for Register

    // Auto-fill and input states
    var loginName by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }

    var showSyncDialog by remember { mutableStateOf(false) }

    // Read saved credentials if remember me is true on initialization/change
    LaunchedEffect(key1 = rememberLogin, key2 = savedName, key3 = savedPin) {
        if (rememberLogin) {
            if (savedName.isNotBlank() && loginName.isBlank()) loginName = savedName
            if (savedPin.isNotBlank() && pinText.isBlank()) pinText = savedPin
        }
    }

    // Register states
    var regName by remember { mutableStateOf("") }
    var regPin by remember { mutableStateOf("") }
    var regPinVisible by remember { mutableStateOf(false) }
    var regEmail by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("FUNCIONARIO") } // FUNCIONARIO or GERENTE

    // Set tab to register if database is empty on start
    LaunchedEffect(allEmployees) {
        if (allEmployees.isEmpty()) {
            activeTab = 1
        }
    }

    if (showSyncDialog) {
        var localCodeInput by remember { mutableStateOf(companyCode) }
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = "Nuvem",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Sincronização em Nuvem", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Compartilhe o ponto e funcionários entre os aparelhos dos funcionários e do gerente em tempo real.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    OutlinedTextField(
                        value = localCodeInput,
                        onValueChange = { localCodeInput = it.uppercase() },
                        label = { Text("Código de Sincronização da Empresa") },
                        placeholder = { Text("Ex: GESPOOL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Código padrão configurado: GESPOOL",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (localCodeInput != "GESPOOL") {
                            TextButton(
                                onClick = { localCodeInput = "GESPOOL" },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Usar GESPOOL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isSyncing) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Sincronizando...", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    } else if (statusMessage != null) {
                        Text(
                            text = statusMessage ?: "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (statusMessage?.startsWith("❌") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.setCompanyCode(localCodeInput)
                            viewModel.syncWithCloud()
                        },
                        enabled = localCodeInput.isNotBlank() && !isSyncing
                    ) {
                        Text("Sincronizar", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { 
                            viewModel.setCompanyCode(localCodeInput)
                            showSyncDialog = false 
                        }
                    ) {
                        Text("Salvar & Sair")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "REGISTRO DE PONTO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showSyncDialog = true }) {
                        Icon(
                            imageVector = if (companyCode.isNotBlank()) Icons.Filled.CloudSync else Icons.Filled.CloudOff,
                            contentDescription = "Sincronização em Nuvem",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Clock / Scanner Avatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Relógio de Ponto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "REGISTRO DE PONTO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Controle de expediente inteligente e geolocalizado",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Tab row toggles between Login and Register
            item {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { 
                            if (allEmployees.isNotEmpty()) {
                                activeTab = 0 
                            }
                        },
                        text = { Text("Acessar Ponto", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                        enabled = allEmployees.isNotEmpty()
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Novo Cadastro", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Active Panel
            item {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "tab_fade_navigation"
                ) { tab ->
                    if (tab == 0) {
                        // ACCESS PANEL
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Identifique-se com Nome e PIN",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Name field
                            OutlinedTextField(
                                value = loginName,
                                onValueChange = { loginName = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_name_input"),
                                label = { Text("Nome Completo") },
                                placeholder = { Text("Ex: Carlos Santana") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Nome") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // PIN field
                            OutlinedTextField(
                                value = pinText,
                                onValueChange = { inputString ->
                                    if (inputString.length <= 6 && inputString.all { it.isDigit() }) {
                                        pinText = inputString
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_input_field"),
                                label = { Text("Código PIN") },
                                placeholder = { Text("4 a 6 dígitos") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "PIN") },
                                trailingIcon = {
                                    IconButton(onClick = { pinVisible = !pinVisible }) {
                                        Icon(
                                            imageVector = if (pinVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = "Alternar Visibilidade"
                                        )
                                    }
                                },
                                visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setRememberLogin(!rememberLogin) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = rememberLogin,
                                    onCheckedChange = { viewModel.setRememberLogin(it) },
                                    modifier = Modifier.testTag("remember_login_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Lembrar meu login",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }

                            if (loginError != null) {
                                Text(
                                    text = loginError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (loginName.isNotBlank() && pinText.isNotBlank()) {
                                        viewModel.loginWithNameAndPin(loginName, pinText)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_pin_button"),
                                shape = RoundedCornerShape(24.dp),
                                enabled = loginName.isNotBlank() && pinText.isNotBlank()
                            ) {
                                Text("ENTRAR", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    } else {
                        // SIGNUP PANEL
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (allEmployees.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Primeiro acesso! Por favor, cadastre um Gerente ou Funcionário abaixo para começar a usar o aplicativo.",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Text(
                                "Cadastro Inicial / Novo Usuário",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Reg Name
                            OutlinedTextField(
                                value = regName,
                                onValueChange = { regName = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_name_input"),
                                label = { Text("Nome Completo") },
                                placeholder = { Text("Ex: Carlos Santana") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Nome") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Reg PIN
                            OutlinedTextField(
                                value = regPin,
                                onValueChange = { inputString ->
                                    if (inputString.length <= 6 && inputString.all { it.isDigit() }) {
                                        regPin = inputString
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_pin_input"),
                                label = { Text("Definir Código PIN (4 a 6 dígitos)") },
                                placeholder = { Text("Senha numérica de acesso") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "PIN") },
                                trailingIcon = {
                                    IconButton(onClick = { regPinVisible = !regPinVisible }) {
                                        Icon(
                                            imageVector = if (regPinVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = "Alternar Visibilidade"
                                        )
                                    }
                                },
                                visualTransformation = if (regPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Perfil Radio-style Select Action
                            Text(
                                "Perfil / Função",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Start),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { regRole = "GERENTE" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (regRole == "GERENTE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (regRole == "GERENTE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Gerente", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { regRole = "FUNCIONARIO" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (regRole == "FUNCIONARIO") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (regRole == "FUNCIONARIO") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Funcionário", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            if (loginError != null) {
                                Text(
                                    text = loginError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (regName.isNotBlank() && regPin.length >= 4) {
                                        viewModel.registerFirstAccess(
                                            name = regName,
                                            role = regRole,
                                            pin = regPin,
                                            position = "",
                                            email = regEmail
                                        )
                                        // Auto-populate access login credentials
                                        loginName = regName
                                        pinText = regPin
                                        activeTab = 0
                                        
                                        // Reset registration values
                                        regName = ""
                                        regPin = ""
                                        regEmail = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_reg_button"),
                                shape = RoundedCornerShape(24.dp),
                                enabled = regName.isNotBlank() && regPin.length >= 4
                            ) {
                                Text("CADASTRAR", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
