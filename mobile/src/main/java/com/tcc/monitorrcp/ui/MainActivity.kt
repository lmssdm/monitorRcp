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
                        onTestSelected = { test, testNumber -> viewModel.onSelectTest(test, testNumber) },
                        onBackFromDetail = { viewModel.onDeselectTest() },
                        onToggleSortOrder = { viewModel.onToggleSortOrder() },
                        onBack = { viewModel.onNavigateTo(Screen.HomeScreen) },
                        onSplashScreenTimeout = { viewModel.onSplashScreenTimeout() },

                        // Passa todas as funções do BottomSheet
                        isFilterSheetVisible = uiState.isFilterSheetVisible,
                        onShowFilterSheet = { viewModel.onShowFilterSheet() },
                        onDismissFilterSheet = { viewModel.onDismissFilterSheet() },
                        pendingQuality = uiState.pendingQualityFilter,

                        // Passa os callbacks do DatePicker
                        isDatePickerVisible = uiState.isDatePickerVisible,
                        onShowDatePicker = { viewModel.onShowDatePicker() },
                        onDismissDatePicker = { viewModel.onDismissDatePicker() },
                        onDateRangeSelected = { start, end -> viewModel.onDateRangeSelected(start, end) },

                        onPendingQualityChanged = { viewModel.onPendingQualityFilterChanged(it) },

                        onApplyFilters = { viewModel.onApplyFilters() },
                        onClearFilters = { viewModel.onClearFilters() },
                        appliedQualityFilter = uiState.appliedQualityFilter,

                        // Passa os estados e callbacks de Duração (String)
                        pendingDurationMin = uiState.pendingDurationMinSec,
                        pendingDurationMax = uiState.pendingDurationMaxSec,
                        onPendingDurationMinChanged = { viewModel.onPendingDurationMinChanged(it) },
                        onPendingDurationMaxChanged = { viewModel.onPendingDurationMaxChanged(it) },
                        appliedDurationFilterMinMs = uiState.appliedDurationFilterMinMs,
                        appliedDurationFilterMaxMs = uiState.appliedDurationFilterMaxMs,

                        // Passa os estados de data
                        pendingStartDate = uiState.pendingStartDateMillis,
                        pendingEndDate = uiState.pendingEndDateMillis,

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

    // Todos os novos parâmetros
    isFilterSheetVisible: Boolean,
    onShowFilterSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    pendingQuality: TestQuality,
    onPendingQualityChanged: (TestQuality) -> Unit,

    // Parâmetros do DatePicker
    isDatePickerVisible: Boolean,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit,

    onApplyFilters: () -> Unit,
    onClearFilters: ()-> Unit,
    appliedQualityFilter: TestQuality,

    // Novos parâmetros de Duração
    pendingDurationMin: String,
    pendingDurationMax: String,
    onPendingDurationMinChanged: (String) -> Unit,
    onPendingDurationMaxChanged: (String) -> Unit,
    appliedDurationFilterMinMs: Long,
    appliedDurationFilterMaxMs: Long,

    // Novos parâmetros de Data
    pendingStartDate: Long?,
    pendingEndDate: Long?,

    // Parâmetro de Exportar
    onExport: () -> Unit
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
                onTestClick = { test, testNumber -> onTestSelected(test, testNumber) },
                isSortDescending = uiState.isSortDescending,
                onToggleSortOrder = onToggleSortOrder,

                isFilterSheetVisible = isFilterSheetVisible,
                onShowFilterSheet = onShowFilterSheet,
                onDismissFilterSheet = onDismissFilterSheet,

                pendingQuality = pendingQuality,
                onPendingQualityChanged = onPendingQualityChanged,

                pendingDurationMin = pendingDurationMin,
                pendingDurationMax = pendingDurationMax,
                onPendingDurationMinChanged = onPendingDurationMinChanged,
                onPendingDurationMaxChanged = onPendingDurationMaxChanged,

                onApplyFilters = onApplyFilters,
                onClearFilters = onClearFilters,

                appliedQualityFilter = appliedQualityFilter,
                appliedDurationFilterMinMs = appliedDurationFilterMinMs,
                appliedDurationFilterMaxMs = appliedDurationFilterMaxMs,

                isDatePickerVisible = isDatePickerVisible,
                onShowDatePicker = onShowDatePicker,
                onDismissDatePicker = onDismissDatePicker,
                onDateRangeSelected = onDateRangeSelected,
                pendingStartDate = pendingStartDate,
                pendingEndDate = pendingEndDate
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