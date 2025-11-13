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
import com.tcc.monitorrcp.model.DateFilter
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.model.TestQuality
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.theme.MonitorRcpTheme // Corrigido o nome do Tema
import com.tcc.monitorrcp.ui.viewmodel.MainViewModel
import com.tcc.monitorrcp.ui.viewmodel.UiState
import com.tcc.monitorrcp.ui.screens.DataScreen
import com.tcc.monitorrcp.ui.screens.HistoryScreen
import com.tcc.monitorrcp.ui.screens.HomeScreen
import com.tcc.monitorrcp.ui.screens.InstructionsScreen
import com.tcc.monitorrcp.ui.screens.LoginScreen
import com.tcc.monitorrcp.ui.screens.SplashScreen
import com.tcc.monitorrcp.ui.screens.HistoryDetailScreen
import com.tcc.monitorrcp.ui.viewmodel.HistoryFilterState // Importa a nova classe

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataRepository.initDatabase(applicationContext)

        setContent {
            // [CORREÇÃO] Nome do tema corrigido para MonitorRcpTheme
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
                        onTestSelected = { test, testNumber -> viewModel.onSelectTest(test, testNumber) },
                        onBackFromDetail = { viewModel.onDeselectTest() },
                        onToggleSortOrder = { viewModel.onToggleSortOrder() },
                        onBack = { viewModel.onNavigateTo(Screen.HomeScreen) },
                        onSplashScreenTimeout = { viewModel.onSplashScreenTimeout() },

                        // [REATORAÇÃO] Passa o objeto de estado do filtro
                        filterState = uiState.filterState,

                        // Funções de evento de filtro
                        onShowFilterSheet = { viewModel.onShowFilterSheet() },
                        onDismissFilterSheet = { viewModel.onDismissFilterSheet() },
                        onPendingQualityChanged = { viewModel.onPendingQualityFilterChanged(it) },
                        onPendingDurationMinChanged = { viewModel.onPendingDurationMinChanged(it) },
                        onPendingDurationMaxChanged = { viewModel.onPendingDurationMaxChanged(it) },
                        onShowDatePicker = { viewModel.onShowDatePicker() },
                        onDismissDatePicker = { viewModel.onDismissDatePicker() },
                        onDateRangeSelected = { start, end -> viewModel.onDateRangeSelected(start, end) },
                        onClearFilters = { viewModel.onClearFilters() },
                        onApplyFilters = { viewModel.onApplyFilters() },

                        // Passa a função de exportar
                        onExport = {
                            if (uiState.selectedTest != null && uiState.selectedTestNumber != null) {
                                viewModel.exportTestResult(
                                    context = applicationContext,
                                    testResult = uiState.selectedTest!!,
                                    testNumber = uiState.selectedTestNumber!!
                                )
                            }
                        }
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
    onTestSelected: (TestResult, Int) -> Unit,
    onBackFromDetail: () -> Unit,
    onToggleSortOrder: () -> Unit,

    // [REATORAÇÃO] Recebe o objeto de estado
    filterState: HistoryFilterState,

    // Funções de evento
    onShowFilterSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onPendingQualityChanged: (TestQuality) -> Unit,
    onPendingDurationMinChanged: (String) -> Unit,
    onPendingDurationMaxChanged: (String) -> Unit,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit,
    onClearFilters: ()-> Unit,
    onApplyFilters: ()-> Unit,

    onExport: () -> Unit
) {
    when (uiState.currentScreen) {
        Screen.SplashScreen -> SplashScreen(onTimeout = onSplashScreenTimeout)

        Screen.LoginScreen -> {
            // [CORREÇÃO] Chamada corrigida para usar os parâmetros que LoginScreen realmente tem
            LoginScreen(onLogin = onLogin)
        }

        Screen.HomeScreen -> {
            // [CORREÇÃO] Chamada corrigida para usar os parâmetros que HomeScreen realmente tem
            HomeScreen(
                name = uiState.userName ?: "",
                onStartClick = onStartClick,
                onHistoryClick = onHistoryClick,
                onInstructionsClick = onInstructionsClick
            )
        }

        Screen.DataScreen -> {
            BackHandler { onBack() }
            // [CORREÇÃO] Chamada corrigida para usar os parâmetros que DataScreen realmente tem
            DataScreen(
                result = uiState.lastTestResult,
                intermediateFeedback = uiState.intermediateFeedback,
                onBack = onBack
            )
        }

        Screen.HistoryScreen -> {
            BackHandler { onBack() }
            // [REATORAÇÃO] Chamada para HistoryScreen agora é muito mais limpa
            HistoryScreen(
                history = uiState.history,
                onBack = onBack,
                onTestClick = { test, testNumber -> onTestSelected(test, testNumber) },
                isSortDescending = uiState.isSortDescending,
                onToggleSortOrder = onToggleSortOrder,

                // Passa o objeto de estado e os callbacks
                filterState = filterState,
                onShowFilterSheet = onShowFilterSheet,
                onDismissFilterSheet = onDismissFilterSheet,
                onPendingQualityChanged = onPendingQualityChanged,
                onPendingDurationMinChanged = onPendingDurationMinChanged,
                onPendingDurationMaxChanged = onPendingDurationMaxChanged,
                onShowDatePicker = onShowDatePicker,
                onDismissDatePicker = onDismissDatePicker,
                onDateRangeSelected = onDateRangeSelected,
                onClearFilters = onClearFilters,
                onApplyFilters = onApplyFilters
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
                testNumber = uiState.selectedTestNumber,
                onBack = onBackFromDetail,
                onExport = onExport // Passa o callback
            )
        }
    }
}