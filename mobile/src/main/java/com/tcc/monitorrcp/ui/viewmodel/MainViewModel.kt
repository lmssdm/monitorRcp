package com.tcc.monitorrcp.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.monitorrcp.analysis.SignalProcessor
import com.tcc.monitorrcp.audio.AudioFeedbackManager
import com.tcc.monitorrcp.audio.FeedbackStatus
import com.tcc.monitorrcp.ListenerService
import com.tcc.monitorrcp.data.DataRepository
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.model.SensorDataPoint
import com.tcc.monitorrcp.model.TestQuality
import com.tcc.monitorrcp.model.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

data class UiState(
    val currentScreen: Screen = Screen.SplashScreen,
    val userName: String? = null,
    val lastReceivedData: String = "Aguardando início...",
    val intermediateFeedback: String = "Inicie o teste no relógio.",
    val lastTestResult: TestResult? = null,
    val history: List<TestResult> = emptyList(),
    val errorMessage: String? = null,
    val selectedTest: TestResult? = null,
    val selectedTestNumber: Int? = null,
    val isSortDescending: Boolean = true,
    val filterState: HistoryFilterState = HistoryFilterState(),

    val testToEditName: TestResult? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val dataRepository = DataRepository

    private val signalProcessor = SignalProcessor
    private val audioManager = AudioFeedbackManager(application)

    private val fullTestDataList = mutableListOf<SensorDataPoint>()

    private var fullHistoryList = listOf<TestResult>()

    private val dataChunkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentScreen = _uiState.value.currentScreen
            if (currentScreen != Screen.DataScreen) {
                Log.w("RCP_DEBUG", "Dados de chunk recebidos na tela $currentScreen, ignorando.")
                return
            }

            intent?.getStringExtra(ListenerService.EXTRA_DATA)?.let { chunkData ->
                _uiState.update { it.copy(lastReceivedData = chunkData) }

                // Parse rápido para chunks pequenos é tranquilo na Main Thread
                val dataPoints = signalProcessor.parseData(chunkData)
                fullTestDataList.addAll(dataPoints)

                analyzeChunkAndProvideFeedback(dataPoints)
            }
        }
    }

    // [CORREÇÃO 1] Receiver otimizado para não travar a UI
    private val finalDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentScreen = _uiState.value.currentScreen
            if (currentScreen != Screen.DataScreen) {
                Log.w("RCP_DEBUG", "Dados FINAIS recebidos na tela $currentScreen, ignorando.")
                return
            }

            intent?.getStringExtra(ListenerService.EXTRA_DATA)?.let { finalData ->

                // 1. Atualiza UI imediatamente
                _uiState.update { it.copy(
                    lastReceivedData = "Processando dados finais...",
                    intermediateFeedback = "Processando resultados..."
                ) }
                audioManager.stopAndReset()

                // 2. Salva o que já temos acumulado dos chunks (Thread Segura)
                val accumulatedData = ArrayList(fullTestDataList)

                // 3. Limpa a lista principal para ficar pronta para o próximo teste
                fullTestDataList.clear()

                // 4. Joga o processamento pesado (parse da String gigante + math) para Background (IO)
                viewModelScope.launch(Dispatchers.IO) {

                    // Parsing pesado aqui (não trava a tela)
                    val finalDataPoints = signalProcessor.parseData(finalData)

                    // Junta tudo
                    val allData = accumulatedData + finalDataPoints
                    val sortedData = allData.sortedBy { it.timestamp }

                    processAndSaveFinalData(sortedData)
                }
            }
        }
    }

    init {
        dataRepository.initDatabase(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.getHistoryFlow().collectLatest { entities ->
                fullHistoryList = entities.map {
                    TestResult(
                        timestamp = it.timestamp,
                        medianFrequency = it.medianFrequency,
                        medianDepth = it.medianDepth,
                        totalCompressions = it.totalCompressions,
                        correctFrequencyCount = it.correctFrequencyCount,
                        correctDepthCount = it.correctDepthCount,
                        slowFrequencyCount = it.slowFrequencyCount,
                        fastFrequencyCount = it.fastFrequencyCount,
                        correctRecoilCount = it.correctRecoilCount,
                        durationInMillis = it.durationInMillis,
                        interruptionCount = it.interruptionCount,
                        totalInterruptionTimeMs = it.totalInterruptionTimeMs,
                        name = it.name
                    )
                }
                applyHistoryFilters()
            }
        }

        val app = getApplication<Application>()
        val chunkFilter = IntentFilter(ListenerService.ACTION_DATA_CHUNK_RECEIVED)
        val finalFilter = IntentFilter(ListenerService.ACTION_FINAL_DATA_RECEIVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(dataChunkReceiver, chunkFilter, Context.RECEIVER_NOT_EXPORTED)
            app.registerReceiver(finalDataReceiver, finalFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(dataChunkReceiver, chunkFilter)
            app.registerReceiver(finalDataReceiver, finalFilter)
        }
    }

    // === Eventos de UI ===
    fun onLogin(name: String) {
        val normalizedName = name.trim().replace(Regex("\\s+"), " ").uppercase()
        if (normalizedName.length >= 3) {
            _uiState.update { it.copy(userName = normalizedName, currentScreen = Screen.HomeScreen) }
        } else {
            _uiState.update { it.copy(errorMessage = "Nome inválido (mínimo 3 letras).") }
        }
    }

    fun onStartTestClick() {
        _uiState.update {
            it.copy(
                lastTestResult = null,
                intermediateFeedback = "Inicie a captura no relógio...",
                lastReceivedData = "Aguardando dados...",
                currentScreen = Screen.DataScreen
            )
        }
        audioManager.stopAndReset()
        fullTestDataList.clear()
    }

    fun onNavigateTo(screen: Screen) {
        if (_uiState.value.currentScreen == Screen.DataScreen && screen != Screen.DataScreen) {
            audioManager.stopAndReset()
            Log.d("RCP_DEBUG", "Saindo da DataScreen. Parando áudio e resetando.")
            fullTestDataList.clear()
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onSplashScreenTimeout() {
        if (_uiState.value.currentScreen == Screen.SplashScreen) {
            _uiState.update { it.copy(currentScreen = Screen.LoginScreen) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // === [CORREÇÃO 2] Análise Rápida com Feedback Melhorado ===
    private fun analyzeChunkAndProvideFeedback(dataPoints: List<SensorDataPoint>) {
        val accData = dataPoints.filter { it.type == "ACC" }
        if (accData.size < 5) return

        val (freq, _) = signalProcessor.analyzeChunk(accData)

        val newStatus: FeedbackStatus
        val freqFeedback: String

        when {
            // Se a frequência for muito baixa, assumimos que parou ou não começou
            freq < 10.0 -> {
                freqFeedback = "Inicie as compressões"
                newStatus = FeedbackStatus.NONE
            }
            freq in 100.0..120.0 -> {
                freqFeedback = "✅ Ritmo Correto"
                newStatus = FeedbackStatus.OK
            }
            freq < 100 -> {
                freqFeedback = "⚠️ Acelere o ritmo!"
                newStatus = FeedbackStatus.SLOW
            }
            else -> { // > 120
                freqFeedback = "⚠️ Reduza o ritmo!"
                newStatus = FeedbackStatus.FAST
            }
        }

        audioManager.playFeedback(newStatus)

        // Exibe feedback visual
        val displayFreq = if (freq < 10.0) "--" else freq.roundToInt().toString()
        val msg = "$freqFeedback ($displayFreq CPM)"

        Log.d("RCP_DEBUG", "analyzeChunk: $msg")
        _uiState.update { it.copy(intermediateFeedback = msg) }
    }

    // === Análise Final (Processamento em Background) ===
    private fun processAndSaveFinalData(sortedData: List<SensorDataPoint>) {

        // Log seguro em background
        Log.d("RCP_CALIBRACAO", "--- PROCESSANDO ${sortedData.size} PONTOS ---")

        if (sortedData.size < 40) {
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update { it.copy(intermediateFeedback = "Dados insuficientes para análise final.") }
            }
            return
        }

        // Cálculo matemático pesado
        val result = signalProcessor.analyzeFinalData(sortedData, System.currentTimeMillis())

        // Salva no banco (Room já lida com threads se configurado, mas estamos em IO)
        dataRepository.newResultReceived(result.copy(name = ""), getApplication())

        // Volta para Main Thread apenas para mostrar o resultado na tela
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    lastTestResult = result,
                    intermediateFeedback = "Teste finalizado!"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(dataChunkReceiver)
            getApplication<Application>().unregisterReceiver(finalDataReceiver)
        } catch (_: Exception) { }

        audioManager.shutdown()
    }

    // === Funções de Navegação de Detalhes ===
    fun onSelectTest(test: TestResult, testNumber: Int) {
        _uiState.update {
            it.copy(
                selectedTest = test,
                selectedTestNumber = testNumber,
                currentScreen = Screen.HistoryDetailScreen
            )
        }
    }

    fun onDeselectTest() {
        _uiState.update {
            it.copy(
                selectedTest = null,
                selectedTestNumber = null,
                currentScreen = Screen.HistoryScreen
            )
        }
    }

    fun onDeleteTest(test: TestResult) {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.deleteTestByTimestamp(test.timestamp)
        }
    }

    fun onShowEditNameDialog() {
        val test = _uiState.value.selectedTest
        _uiState.update { it.copy(testToEditName = test) }
    }

    fun onDismissEditNameDialog() {
        _uiState.update { it.copy(testToEditName = null) }
    }

    fun onUpdateTestName(newName: String) {
        val name = newName.trim()
        val test = _uiState.value.testToEditName ?: return

        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.updateTestName(test.timestamp, name)
        }

        val updatedTest = test.copy(name = name)

        _uiState.update {
            it.copy(
                testToEditName = null,
                selectedTest = updatedTest
            )
        }
    }

    // === Lógica de Filtros ===
    private fun applyHistoryFilters() {
        _uiState.update { state ->
            val filters = state.filterState

            val qualityFilteredList = when (filters.appliedQuality) {
                TestQuality.TODOS -> fullHistoryList
                TestQuality.BOM -> fullHistoryList.filter { it.quality == TestQuality.BOM }
                TestQuality.REGULAR -> fullHistoryList.filter { it.quality == TestQuality.REGULAR }
            }

            val durationMinMs = filters.appliedDurationMinMs
            val durationMinFilteredList = if (durationMinMs > 0L) {
                qualityFilteredList.filter { it.durationInMillis >= durationMinMs }
            } else {
                qualityFilteredList
            }

            val durationMaxMs = filters.appliedDurationMaxMs
            val durationMaxFilteredList = if (durationMaxMs > 0L) {
                durationMinFilteredList.filter { it.durationInMillis <= durationMaxMs }
            } else {
                durationMinFilteredList
            }

            val startDate = filters.appliedStartDateMs
            val endDate = filters.appliedEndDateMs
            val dateFilteredList = if (startDate != null && endDate != null) {
                val adjustedEndDate = endDate + (24 * 60 * 60 * 1000)
                durationMaxFilteredList.filter { it.timestamp in startDate..adjustedEndDate }
            } else {
                durationMaxFilteredList
            }

            state.copy(
                history = if (state.isSortDescending) {
                    dateFilteredList
                } else {
                    dateFilteredList.reversed()
                }
            )
        }
    }

    fun onToggleSortOrder() {
        _uiState.update {
            it.copy(
                isSortDescending = !it.isSortDescending,
                history = it.history.reversed()
            )
        }
    }

    fun onShowFilterSheet() {
        _uiState.update {
            val filters = it.filterState
            it.copy(
                filterState = filters.copy(
                    isFilterSheetVisible = true,
                    pendingQuality = filters.appliedQuality,
                    pendingStartDateMs = filters.appliedStartDateMs,
                    pendingEndDateMs = filters.appliedEndDateMs,
                    pendingDurationMinSec = (filters.appliedDurationMinMs / 1000).toString().takeIf { it != "0" } ?: "",
                    pendingDurationMaxSec = (filters.appliedDurationMaxMs / 1000).toString().takeIf { it != "0" } ?: ""
                )
            )
        }
    }

    fun onDismissFilterSheet() {
        _uiState.update { it.copy(filterState = it.filterState.copy(isFilterSheetVisible = false)) }
    }

    fun onPendingQualityFilterChanged(newFilter: TestQuality) {
        _uiState.update { it.copy(filterState = it.filterState.copy(pendingQuality = newFilter)) }
    }

    fun onPendingDurationMinChanged(newDuration: String) {
        if (newDuration.all { it.isDigit() }) {
            _uiState.update { it.copy(filterState = it.filterState.copy(pendingDurationMinSec = newDuration)) }
        }
    }

    fun onPendingDurationMaxChanged(newDuration: String) {
        if (newDuration.all { it.isDigit() }) {
            _uiState.update { it.copy(filterState = it.filterState.copy(pendingDurationMaxSec = newDuration)) }
        }
    }

    fun onShowDatePicker() {
        _uiState.update { it.copy(filterState = it.filterState.copy(isDatePickerVisible = true)) }
    }

    fun onDismissDatePicker() {
        _uiState.update { it.copy(filterState = it.filterState.copy(isDatePickerVisible = false)) }
    }

    fun onDateRangeSelected(startMillis: Long?, endMillis: Long?) {
        _uiState.update {
            it.copy(
                filterState = it.filterState.copy(
                    pendingStartDateMs = startMillis,
                    pendingEndDateMs = endMillis,
                    isDatePickerVisible = false
                )
            )
        }
    }

    fun onClearFilters() {
        _uiState.update {
            it.copy(
                filterState = HistoryFilterState()
            )
        }
        applyHistoryFilters()
    }

    fun onApplyFilters() {
        _uiState.update {
            val pendingFilters = it.filterState
            val minMs = (pendingFilters.pendingDurationMinSec.toLongOrNull() ?: 0L) * 1000
            val maxMs = (pendingFilters.pendingDurationMaxSec.toLongOrNull() ?: 0L) * 1000

            it.copy(
                filterState = pendingFilters.copy(
                    appliedQuality = pendingFilters.pendingQuality,
                    appliedDurationMinMs = minMs,
                    appliedDurationMaxMs = maxMs,
                    appliedStartDateMs = pendingFilters.pendingStartDateMs,
                    appliedEndDateMs = pendingFilters.pendingEndDateMs,
                    isFilterSheetVisible = false
                )
            )
        }
        applyHistoryFilters()
    }

    // === [CORREÇÃO 3] Exportação CSV com Ponto e Vírgula ===
    fun exportTestResult(context: Context, testResult: TestResult, testNumber: Int) {
        val csvContent = buildString {
            // Cabeçalho usando ; para compatibilidade com Excel PT-BR
            appendLine("Metrica;Valor")

            val testName = testResult.name.ifBlank { "Teste $testNumber" }
            appendLine("Nome Teste;$testName")
            appendLine("Timestamp;${testResult.timestamp}")
            appendLine("Duracao (formatada);${testResult.formattedDuration}")
            appendLine("Duracao (ms);${testResult.durationInMillis}")
            appendLine("Total Compressoes;${testResult.totalCompressions}")
            appendLine("Frequencia Mediana (cpm);${testFormatado(testResult.medianFrequency, 0)}")
            appendLine("Profundidade Mediana (cm);${testFormatado(testResult.medianDepth, 1)}")
            appendLine("Compressoes Freq Correta;${testResult.correctFrequencyCount}")
            appendLine("Compressoes Freq Lenta;${testResult.slowFrequencyCount}")
            appendLine("Compressoes Freq Rapida;${testResult.fastFrequencyCount}")
            appendLine("Compressoes Prof Correta;${testResult.correctDepthCount}")
            appendLine("Compressoes Recoil Correto;${testResult.correctRecoilCount}")
            appendLine("Total de Pausas;${testResult.interruptionCount}")
            appendLine("Tempo Total em Pausa (s);${testFormatado(testResult.totalInterruptionTimeMs / 1000.0, 1)}")
        }

        try {
            val cacheDir = File(context.cacheDir, "exports")
            cacheDir.mkdirs()

            val safeFileName = testResult.name.ifBlank { "Teste_$testNumber" }
                .replace(Regex("[^A-Za-z0-9_]"), "_")
            val file = File(cacheDir, "RCP_$safeFileName.csv")

            file.writeText(csvContent)

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Exportar Relatório CSV")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)

        } catch (e: Exception) {
            Log.e("MainViewModel", "Falha ao exportar CSV", e)
            _uiState.update { it.copy(errorMessage = "Falha ao exportar ficheiro: ${e.message}") }
        }
    }

    private fun testFormatado(value: Double, decimals: Int): String {
        return "%.${decimals}f".format(value)
    }
}