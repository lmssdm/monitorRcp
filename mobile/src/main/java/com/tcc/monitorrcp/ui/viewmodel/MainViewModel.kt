package com.tcc.monitorrcp.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.monitorrcp.analysis.SignalProcessor
import com.tcc.monitorrcp.audio.AudioFeedbackManager
import com.tcc.monitorrcp.audio.FeedbackStatus
import com.tcc.monitorrcp.ListenerService
import com.tcc.monitorrcp.data.DataRepository
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.model.SensorDataPoint
import com.tcc.monitorrcp.model.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val selectedTestNumber: Int? = null, // ALTERAÇÃO AQUI
    val isSortDescending: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val dataRepository = DataRepository

    private val signalProcessor = SignalProcessor
    private val audioManager = AudioFeedbackManager(application)

    private val fullTestDataList = mutableListOf<SensorDataPoint>()

    private val dataChunkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val currentScreen = _uiState.value.currentScreen
            if (currentScreen != Screen.DataScreen && currentScreen != Screen.HomeScreen) {
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
            if (currentScreen != Screen.DataScreen && currentScreen != Screen.HomeScreen) {
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
                val historyList = entities.map {
                    TestResult(
                        timestamp = it.timestamp,
                        medianFrequency = it.medianFrequency,
                        averageDepth = it.averageDepth,
                        totalCompressions = it.totalCompressions,
                        correctFrequencyCount = it.correctFrequencyCount,
                        correctDepthCount = it.correctDepthCount,
                        slowFrequencyCount = it.slowFrequencyCount,
                        fastFrequencyCount = it.fastFrequencyCount,
                        durationInMillis = it.durationInMillis
                    )
                }

                _uiState.update {
                    it.copy(
                        history = if (it.isSortDescending) {
                            historyList
                        } else {
                            historyList.reversed()
                        }
                    )
                }
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

    fun onNavigateTo(screen: Screen) { _uiState.update { it.copy(currentScreen = screen) } }
    fun onSplashScreenTimeout() {
        if (_uiState.value.currentScreen == Screen.SplashScreen) {
            _uiState.update { it.copy(currentScreen = Screen.LoginScreen) }
        }
    }
    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }

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

    // ALTERAÇÃO AQUI
    fun onSelectTest(test: TestResult, testNumber: Int) {
        _uiState.update {
            it.copy(
                selectedTest = test,
                selectedTestNumber = testNumber, // Guarda o número do teste
                currentScreen = Screen.HistoryDetailScreen
            )
        }
    }

    // ALTERAÇÃO AQUI
    fun onDeselectTest() {
        _uiState.update {
            it.copy(
                selectedTest = null,
                selectedTestNumber = null, // Limpa o número do teste
                currentScreen = Screen.HistoryScreen
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
}