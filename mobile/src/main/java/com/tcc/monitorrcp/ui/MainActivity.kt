package com.tcc.monitorrcp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tcc.monitorrcp.data.DataRepository
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.theme.MonitorRcpTheme
import com.tcc.monitorrcp.ui.viewmodel.MainViewModel
import com.tcc.monitorrcp.ui.viewmodel.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Inicializa o banco Room antes de usar o repositório
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
        Screen.LoginScreen -> LoginScreen(onLogin = onLogin)
        Screen.HomeScreen -> HomeScreen(
            name = uiState.userName ?: "",
            onStartClick = onStartClick,
            onHistoryClick = onHistoryClick,
            onInstructionsClick = onInstructionsClick
        )
        Screen.DataScreen -> DataScreen(
            result = uiState.lastTestResult,
            data = uiState.lastReceivedData,
            onBack = onBack
        )

        // ✅ Tela de histórico COM GRÁFICOS (do Screens.kt)
        Screen.HistoryScreen -> HistoryScreen(
            history = uiState.history,
            onBack = onBack
        )

        Screen.InstructionsScreen -> InstructionsScreen(onBack = onBack)
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun HistoryScreenSimple(
//    history: List<TestResult>,
//    onBack: () -> Unit
//) {
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Histórico de Testes") },
//                navigationIcon = {
//                    IconButton(onClick = onBack) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                            contentDescription = "Voltar"
//                        )
//                    }
//                }
//            )
//        }
//    ) { padding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(padding)
//                .padding(16.dp)
//        ) {
//            if (history.isEmpty()) {
//                Box(
//                    modifier = Modifier.fillMaxSize(),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text("Nenhum teste registrado ainda.")
//                }
//            } else {
//                Text(
//                    text = "Resultados (${history.size})",
//                    style = MaterialTheme.typography.titleMedium,
//                    modifier = Modifier.padding(bottom = 8.dp)
//                )
//
//                LazyColumn(modifier = Modifier.fillMaxSize()) {
//                    items(history) { result ->
//                        HistoryItem(result)
//                        Divider()
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun HistoryItem(result: TestResult) {
//    val date = remember(result.timestamp) {
//        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
//            .format(java.util.Date(result.timestamp))
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 8.dp)
//    ) {
//        Text("Data: $date", fontWeight = FontWeight.Bold)
//        Text("Frequência Mediana: %.1f cpm".format(result.medianFrequency))
//        Text("Profundidade Média: %.1f cm".format(result.averageDepth))
//        Text("Total de Compressões: ${result.totalCompressions}")
//        Text("Freq. Correta: ${result.correctFrequencyCount}")
//        Text("Prof. Correta: ${result.correctDepthCount}")
//    }
//}
