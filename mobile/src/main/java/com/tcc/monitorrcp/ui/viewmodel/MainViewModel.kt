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
import com.tcc.monitorrcp.model.Screen
import com.tcc.monitorrcp.model.SensorDataPoint
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

// Estado da UI: Uma única classe que representa tudo que a tela pode mostrar.
data class UiState(
    val currentScreen: Screen = Screen.SplashScreen,
    val userName: String? = null,
    val lastDataString: String = "A aguardar dados do relógio...",
    val lastTestResult: TestResult? = null,
    val history: List<TestResult> = emptyList()
)

// O ViewModel precisa do "contexto" do aplicativo para acessar SharedPreferences,
// por isso usamos AndroidViewModel.
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow() // A UI vai observar isso

    private val dataRepository = DataRepository

    // O BroadcastReceiver agora vive aqui, dentro do ViewModel.
    // Ele é mais seguro aqui porque está ligado ao ciclo de vida do ViewModel.
    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("data")?.let { csvData ->
                _uiState.update { it.copy(lastDataString = csvData) }
                processAndAnalyzeData(csvData)
                // Navega para a tela de dados automaticamente
                _uiState.update { it.copy(currentScreen = Screen.DataScreen) }
            }
        }
    }

    init {
        // Ao iniciar o ViewModel, carregamos o histórico
        viewModelScope.launch {
            dataRepository.loadHistory(getApplication())
            dataRepository.history.collect { historyList ->
                _uiState.update { it.copy(history = historyList) }
            }
        }

        // Registramos o BroadcastReceiver
        val filter = IntentFilter("com.tcc.monitorrcp.DATA_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(dataReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            application.registerReceiver(dataReceiver, filter)
        }
    }

    // --- Funções de Eventos da UI ---
    // A UI vai chamar essas funções quando o usuário interagir.

    fun onLogin(name: String) {
        _uiState.update { it.copy(userName = name, currentScreen = Screen.HomeScreen) }
    }

    fun onStartTestClick() {
        _uiState.update {
            it.copy(
                lastTestResult = null,
                lastDataString = "A aguardar novo teste do relógio...",
                currentScreen = Screen.DataScreen
            )
        }
    }

    fun onNavigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onSplashScreenTimeout() {
        if (_uiState.value.currentScreen == Screen.SplashScreen) {
            _uiState.update { it.copy(currentScreen = Screen.LoginScreen) }
        }
    }

    // --- Lógica de Negócio (Movida da MainActivity) ---
    private fun processAndAnalyzeData(csvData: String) {
        val dataPoints = parseData(csvData)
        if (dataPoints.isEmpty()) return

        val accelerometerData = dataPoints.filter { it.type == "ACC" }
        val accelerationMagnitudes = accelerometerData.map { it.magnitude() }
        val smoothedMagnitudes = movingAverage(accelerationMagnitudes, 3)

        val minPeakHeight = 10.5f
        val minPeakDistance = 4
        val allPeakIndices = findPeaks(smoothedMagnitudes, minPeakHeight, minPeakDistance)
        val validPeakIndices = if (allPeakIndices.size > 2) {
            allPeakIndices.subList(1, allPeakIndices.size - 1)
        } else {
            allPeakIndices
        }
        val totalCompressions = validPeakIndices.size

        val individualFrequencies = calculateIndividualFrequencies(accelerometerData, validPeakIndices)
        val individualDepths = calculateIndividualDepths(accelerometerData, validPeakIndices)
        val correctFrequencyCount = individualFrequencies.count { it in 100.0..120.0 }
        val correctDepthCount = individualDepths.count { it in 4.0..6.0 }
        val medianFrequency = if (individualFrequencies.isNotEmpty()) individualFrequencies.median() else 0.0
        val averageDepth = if (individualDepths.isNotEmpty()) individualDepths.average() else 0.0

        val newResult = TestResult(System.currentTimeMillis(), medianFrequency, averageDepth, totalCompressions, correctFrequencyCount, correctDepthCount)

        // Atualiza o estado da UI com o novo resultado
        _uiState.update { it.copy(lastTestResult = newResult) }

        // Salva no histórico usando o Repository
        dataRepository.saveNewResultToHistory(getApplication(), newResult)

        Log.d("RCP_ANALYSIS", "Resultado: ${newResult.totalCompressions} compressões (Freq Mediana: $medianFrequency, Prof Média: $averageDepth)")
    }

    // --- Funções Auxiliares (Movidas da MainActivity) ---
    private fun parseData(csvData: String): List<SensorDataPoint> {
        return csvData.split("\n").drop(1).mapNotNull { line ->
            try {
                val parts = line.split(",")
                if (parts.size == 5) {
                    SensorDataPoint(parts[0].toLong(), parts[1], parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat())
                } else { null }
            } catch (e: Exception) { null }
        }
    }

    private fun movingAverage(data: List<Float>, windowSize: Int): List<Float> {
        if (windowSize <= 1) return data
        val result = mutableListOf<Float>()
        for (i in data.indices) {
            val window = data.subList(maxOf(0, i - windowSize + 1), i + 1)
            result.add(window.average().toFloat())
        }
        return result
    }

    private fun findPeaks(data: List<Float>, minHeight: Float, minDistance: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        var i = 1
        while (i < data.size - 1) {
            if (data[i] > data[i - 1] && data[i] > data[i + 1] && data[i] >= minHeight) {
                if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                    peaks.add(i)
                }
            }
            i++
        }
        return peaks
    }

    private fun calculateIndividualFrequencies(accelerometerData: List<SensorDataPoint>, peakIndices: List<Int>): List<Double> {
        if (peakIndices.size < 2) return emptyList()
        return (0 until peakIndices.size - 1).map { i ->
            val startTime = accelerometerData[peakIndices[i]].timestamp
            val endTime = accelerometerData[peakIndices[i+1]].timestamp
            val duration = (endTime - startTime) / 1000.0
            if (duration > 0) 60.0 / duration else 0.0
        }
    }

    private fun calculateIndividualDepths(accelerometerData: List<SensorDataPoint>, peakIndices: List<Int>): List<Double> {
        if (peakIndices.size < 2) return emptyList()
        val alpha = 0.8f
        var gravityZ = 0f
        val linearAccelerationZ = accelerometerData.map {
            gravityZ = alpha * gravityZ + (1 - alpha) * it.z
            it.z - gravityZ
        }
        return (0 until peakIndices.size - 1).map { i ->
            val cycleAcceleration = linearAccelerationZ.slice(peakIndices[i]..peakIndices[i+1])
            val cycleTimestamps = accelerometerData.slice(peakIndices[i]..peakIndices[i+1]).map { it.timestamp }
            var velocity = 0.0
            var depth = 0.0
            var maxDepth = 0.0
            for (j in 0 until cycleAcceleration.size - 1) {
                val dt = (cycleTimestamps[j+1] - cycleTimestamps[j]) / 1000.0
                velocity += cycleAcceleration[j] * dt
                depth += velocity * dt
                if (abs(depth) > maxDepth) maxDepth = abs(depth)
            }
            maxDepth * 100
        }.filter { it > 1.0 && it < 10.0 }
    }

    private fun List<Double>.median(): Double {
        if (this.isEmpty()) return 0.0
        val sorted = this.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    // É importante limpar o receiver quando o ViewModel for destruído.
    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(dataReceiver)
    }
}