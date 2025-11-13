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
import com.tcc.monitorrcp.model.DateFilter
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
import java.util.Calendar
import kotlin.math.roundToInt


// --- [MUDANÇA AQUI] UiState foi DRASTICAMENTE simplificado ---
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

    // Todos os 10 campos de filtro agora estão dentro deste objeto
    val filterState: HistoryFilterState = HistoryFilterState()
)
// --- FIM DA MUDANÇA ---

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

                val dataPoints = signalProcessor.parseData(chunkData)
                fullTestDataList.addAll(dataPoints)

                analyzeChunkAndProvideFeedback(dataPoints)
            }
        }
    }

    private val finalDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentScreen = _uiState.value.currentScreen
            if (currentScreen != Screen.DataScreen) {
                Log.w("RCP_DEBUG", "Dados FINAIS recebidos na tela $currentScreen, ignorando.")
                return
            }

            intent?.getStringExtra(ListenerService.EXTRA_DATA)?.let { finalData ->
                _uiState.update { it.copy(lastReceivedData = finalData) }

                val finalDataPoints = signalProcessor.parseData(finalData)
                fullTestDataList.addAll(finalDataPoints)

                val sortedData = fullTestDataList.sortedBy { it.timestamp }

                processAndSaveFinalData(sortedData)
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
                        medianDepth = it.medianDepth, // [REFACTOR] Renomeado
                        totalCompressions = it.totalCompressions,
                        correctFrequencyCount = it.correctFrequencyCount,
                        correctDepthCount = it.correctDepthCount,
                        slowFrequencyCount = it.slowFrequencyCount,
                        fastFrequencyCount = it.fastFrequencyCount,
                        correctRecoilCount = it.correctRecoilCount,
                        durationInMillis = it.durationInMillis,
                        interruptionCount = it.interruptionCount,
                        totalInterruptionTimeMs = it.totalInterruptionTimeMs
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

    // === Análise Rápida (Chunks) ===
    private fun analyzeChunkAndProvideFeedback(dataPoints: List<SensorDataPoint>) {
        val accData = dataPoints.filter { it.type == "ACC" }
        if (accData.size < 5) return

        val (freq, _) = signalProcessor.analyzeChunk(accData)

        val newStatus: FeedbackStatus
        val freqFeedback: String

        when {
            freq == 0.0 -> {
                freqFeedback = "Calculando..."
                newStatus = FeedbackStatus.NONE
            }
            freq in 100.0..120.0 -> {
                freqFeedback = "✅ Frequência OK"
                newStatus = FeedbackStatus.OK
            }
            freq < 100 -> {
                freqFeedback = "⚠️ Mais rápido!"
                newStatus = FeedbackStatus.SLOW
            }
            else -> {
                freqFeedback = "⚠️ Mais devagar!"
                newStatus = FeedbackStatus.FAST
            }
        }

        audioManager.playFeedback(newStatus)

        val msg = "$freqFeedback (${freq.roundToInt()} CPM)"
        Log.d("RCP_DEBUG", "analyzeChunk SUCESSO. Atualizando feedback para: $msg")
        _uiState.update { it.copy(intermediateFeedback = msg) }
    }

    // === Análise Final (Precisa) ===
    private fun processAndSaveFinalData(sortedData: List<SensorDataPoint>) {
        audioManager.stopAndReset()

        if (sortedData.size < 40) {
            _uiState.update { it.copy(intermediateFeedback = "Dados insuficientes para análise final.") }
            return
        }

        val result = signalProcessor.analyzeFinalData(sortedData, System.currentTimeMillis())

        _uiState.update { it.copy(lastTestResult = result, intermediateFeedback = "Teste finalizado!") }
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.newResultReceived(result, getApplication())
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

    // === Lógica de Filtros Avançados ===

    // --- [MUDANÇA AQUI] applyHistoryFilters agora lê do filterState ---
    private fun applyHistoryFilters() {
        _uiState.update { state ->
            val filters = state.filterState

            // 1. Filtra por Qualidade
            val qualityFilteredList = when (filters.appliedQuality) {
                TestQuality.TODOS -> fullHistoryList
                TestQuality.BOM -> fullHistoryList.filter { it.quality == TestQuality.BOM }
                TestQuality.REGULAR -> fullHistoryList.filter { it.quality == TestQuality.REGULAR }
            }

            // 2. Filtra por Duração MÍNIMA
            val durationMinMs = filters.appliedDurationMinMs
            val durationMinFilteredList = if (durationMinMs > 0L) {
                qualityFilteredList.filter { it.durationInMillis >= durationMinMs }
            } else {
                qualityFilteredList
            }

            // 3. Filtra por Duração MÁXIMA
            val durationMaxMs = filters.appliedDurationMaxMs
            val durationMaxFilteredList = if (durationMaxMs > 0L) {
                durationMinFilteredList.filter { it.durationInMillis <= durationMaxMs }
            } else {
                durationMinFilteredList
            }

            // 4. Filtra por Data
            val startDate = filters.appliedStartDateMs
            val endDate = filters.appliedEndDateMs
            val dateFilteredList = if (startDate != null && endDate != null) {
                val adjustedEndDate = endDate + (24 * 60 * 60 * 1000)
                durationMaxFilteredList.filter { it.timestamp in startDate..adjustedEndDate }
            } else {
                durationMaxFilteredList
            }

            // 5. Aplica a ordenação
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

    // --- [MUDANÇA AQUI] Funções de filtro agora atualizam o objeto filterState ---
    fun onShowFilterSheet() {
        _uiState.update {
            val filters = it.filterState
            it.copy(
                filterState = filters.copy(
                    isFilterSheetVisible = true,
                    // Reseta os campos pendentes para os valores aplicados
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
                // Reseta o objeto de filtro inteiro para o padrão
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
                    // Move os valores pendentes para os aplicados
                    appliedQuality = pendingFilters.pendingQuality,
                    appliedDurationMinMs = minMs,
                    appliedDurationMaxMs = maxMs,
                    appliedStartDateMs = pendingFilters.pendingStartDateMs,
                    appliedEndDateMs = pendingFilters.pendingEndDateMs,
                    // Esconde a sheet
                    isFilterSheetVisible = false
                )
            )
        }
        applyHistoryFilters()
    }
    // --- FIM DAS MUDANÇAS DE FILTRO ---


    fun exportTestResult(context: Context, testResult: TestResult, testNumber: Int) {
        val csvContent = buildString {
            appendLine("Metrica,Valor")
            appendLine("Teste Numero,$testNumber")
            appendLine("Timestamp,${testResult.timestamp}")
            appendLine("Duracao (formatada),${testResult.formattedDuration}")
            appendLine("Duracao (ms),${testResult.durationInMillis}")
            appendLine("Total Compressoes,${testResult.totalCompressions}")
            appendLine("Frequencia Mediana (cpm),${testFormatado(testResult.medianFrequency, 0)}")
            appendLine("Profundidade Mediana (cm),${testFormatado(testResult.medianDepth, 1)}") // [REFACTOR] Renomeado
            appendLine("Compressoes Freq Correta,${testResult.correctFrequencyCount}")
            appendLine("Compressoes Freq Lenta,${testResult.slowFrequencyCount}")
            appendLine("Compressoes Freq Rapida,${testResult.fastFrequencyCount}")
            appendLine("Compressoes Prof Correta,${testResult.correctDepthCount}")
            appendLine("Compressoes Recoil Correto,${testResult.correctRecoilCount}")
            appendLine("Total de Pausas,${testResult.interruptionCount}")
            appendLine("Tempo Total em Pausa (s),${testFormatado(testResult.totalInterruptionTimeMs / 1000.0, 1)}")
        }

        try {
            val cacheDir = File(context.cacheDir, "exports")
            cacheDir.mkdirs()

            val file = File(cacheDir, "RCP_Teste_$testNumber.csv")
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

    // Função auxiliar para formatar os números no CSV de forma consistente
    private fun testFormatado(value: Double, decimals: Int): String {
        return "%.${decimals}f".format(value)
    }
}