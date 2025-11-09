package com.tcc.monitorrcp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.tcc.monitorrcp.data.DataRepository
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.ui.theme.MonitorRcpTheme
import com.tcc.monitorrcp.ui.viewmodel.MainViewModel
import com.tcc.monitorrcp.ui.viewmodel.UiState
// [MUDANÇA] Import necessário para a Melhoria #4
import androidx.activity.compose.BackHandler

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
    onSplashScreenTimeout: () -> Unit
) {
    when (uiState.currentScreen) {
        Screen.SplashScreen -> SplashScreen(onTimeout = onSplashScreenTimeout)

        Screen.LoginScreen -> {
            // "Voltar" da tela de login fecha o app (comportamento padrão)
            LoginScreen(onLogin = onLogin)
        }

        Screen.HomeScreen -> {
            // "Voltar" da tela de início fecha o app (comportamento padrão)
            HomeScreen(
                name = uiState.userName ?: "",
                onStartClick = onStartClick,
                onHistoryClick = onHistoryClick,
                onInstructionsClick = onInstructionsClick
            )
        }

        Screen.DataScreen -> {
            // [MUDANÇA #4] Intercepta o botão "voltar" e navega para a Home
            BackHandler { onBack() }
            DataScreen(
                result = uiState.lastTestResult,
                // [MUDANÇA #3] Passa o feedback ao vivo para a tela
                intermediateFeedback = uiState.intermediateFeedback,
                onBack = onBack
            )
        }

        Screen.HistoryScreen -> {
            // [MUDANÇA #4] Intercepta o botão "voltar" e navega para a Home
            BackHandler { onBack() }
            HistoryScreen(
                history = uiState.history,
                onBack = onBack
            )
        }

        Screen.InstructionsScreen -> {
            // [MUDANÇA #4] Intercepta o botão "voltar" e navega para a Home
            BackHandler { onBack() }
            InstructionsScreen(onBack = onBack)
        }
    }
}