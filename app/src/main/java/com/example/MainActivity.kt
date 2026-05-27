package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EmployeeDashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ManagerDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AttendanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: AttendanceViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                
                val loggedUser by viewModel.loggedInUser.collectAsState()

                // Register standard edge-to-edge safe surface
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = loggedUser,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen_navigation"
                    ) { user ->
                        if (user == null) {
                            LoginScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            if (user.role == "GERENTE") {
                                ManagerDashboardScreen(
                                    viewModel = viewModel,
                                    manager = user,
                                    onBackToLogin = { viewModel.logout() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                EmployeeDashboardScreen(
                                    viewModel = viewModel,
                                    employee = user,
                                    onBackToLogin = { viewModel.logout() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
