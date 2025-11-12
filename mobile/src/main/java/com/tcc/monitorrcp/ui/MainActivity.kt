package com.tcc.monitorrcp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.tcc.monitorrcp.data.DataRepository
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.theme.MonitorRcpTheme
import com.tcc.monitorrcp.ui.viewmodel.MainViewModel
import com.tcc.monitorrcp.ui.viewmodel.UiState
import com.tcc.monitorrcp.ui.screens.DataScreen
import com.tcc.monitorrcp.ui.screens.HistoryScreen
import com.tcc.monitorrcp.ui.screens.HomeScreen
import com.tcc.monitorrcp.ui.screens.InstructionsScreen
import com.tcc.monitorrcp.ui.screens.LoginScreen
import com.tcc.monitorrcp.ui.screens.SplashScreen
import com.tcc.monitorrcp.ui.screens.HistoryDetailScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataRepository.initDatabase(applicationContext)

        setContent {
            MonitorRcpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    AppNavigator(
                        uiState = uiState,
                        onLogin = { name -> viewModel.onLogin(name) },
                        onStartClick = { viewModel.onStartTestClick() },
                        onHistoryClick = { viewModel.onNavigateTo(Screen.HistoryScreen) },
                        onInstructionsClick = { viewModel.onNavigateTo(Screen.InstructionsScreen) },
                        // ALTERAÇÃO AQUI: Atualiza a chamada
                        onTestSelected = { test, testNumber -> viewModel.onSelectTest(test, testNumber) },
                        onBackFromDetail = { viewModel.onDeselectTest() },
                        onToggleSortOrder = { viewModel.onToggleSortOrder() },
                        onBack = { viewModel.onNavigateTo(Screen.HomeScreen) },
                        onSplashScreenTimeout = { viewModel.onSplashScreenTimeout() }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigator(
    uiState: UiState,
    onLogin: (String) -> Unit,
    onStartClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onInstructionsClick: () -> Unit,
    onBack: () -> Unit,
    onSplashScreenTimeout: () -> Unit,
    onTestSelected: (TestResult, Int) -> Unit, // ALTERAÇÃO AQUI: Assinatura atualizada
    onBackFromDetail: () -> Unit,
    onToggleSortOrder: () -> Unit
) {
    when (uiState.currentScreen) {
        Screen.SplashScreen -> SplashScreen(onTimeout = onSplashScreenTimeout)

        Screen.LoginScreen -> {
            LoginScreen(onLogin = onLogin)
        }

        Screen.HomeScreen -> {
            HomeScreen(
                name = uiState.userName ?: "",
                onStartClick = onStartClick,
                onHistoryClick = onHistoryClick,
                onInstructionsClick = onInstructionsClick
            )
        }

        Screen.DataScreen -> {
            BackHandler { onBack() }
            DataScreen(
                result = uiState.lastTestResult,
                intermediateFeedback = uiState.intermediateFeedback,
                onBack = onBack
            )
        }

        Screen.HistoryScreen -> {
            BackHandler { onBack() }
            HistoryScreen(
                history = uiState.history,
                onBack = onBack,
                // ALTERAÇÃO AQUI: Lambda atualizada
                onTestClick = { test, testNumber -> onTestSelected(test, testNumber) },
                isSortDescending = uiState.isSortDescending,
                onToggleSortOrder = onToggleSortOrder
            )
        }

        Screen.InstructionsScreen -> {
            BackHandler { onBack() }
            InstructionsScreen(onBack = onBack)
        }

        Screen.HistoryDetailScreen -> {
            BackHandler { onBackFromDetail() }
            HistoryDetailScreen(
                test = uiState.selectedTest,
                testNumber = uiState.selectedTestNumber, // ALTERAÇÃO AQUI: Passa o número
                onBack = onBackFromDetail
            )
        }
    }
}