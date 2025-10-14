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
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import kotlin.math.abs

data class UiState(
    val currentScreen: Screen = Screen.SplashScreen,
    val userName: String? = null,
    val lastDataString: String = "A aguardar dados do relógio...",
    val lastTestResult: TestResult? = null,
    val history: List<TestResult> = emptyList(),
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val dataRepository = DataRepository

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("data")?.let { csvData ->
                _uiState.update { it.copy(lastDataString = csvData) }
                processAndAnalyzeData(csvData)
                _uiState.update { it.copy(currentScreen = Screen.DataScreen) }
            }
        }
    }

    init {
        // ✅ Inicializa o banco Room
        dataRepository.initDatabase(getApplication())
        Log.d("RCP_DEBUG_DB", "Banco Room inicializado")

        // ✅ Observa mudanças no histórico em tempo real
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.getHistoryFlow().collectLatest { entities ->
                val historyList = entities.map {
                    TestResult(
                        timestamp = it.timestamp,
                        medianFrequency = it.medianFrequency,
                        averageDepth = it.averageDepth,
                        totalCompressions = it.totalCompressions,
                        correctFrequencyCount = it.correctFrequencyCount,
                        correctDepthCount = it.correctDepthCount
                    )
                }.sortedByDescending { it.timestamp }

                _uiState.update { it.copy(history = historyList) }
                Log.d("RCP_DEBUG_DB", "Histórico atualizado: ${historyList.size} registros")
            }
        }

        // ✅ Registra o receiver para receber dados do smartwatch
        val filter = IntentFilter("com.tcc.monitorrcp.DATA_RECEIVED")
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(dataReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(dataReceiver, filter)
        }
    }

    // --- Eventos de UI ---
    fun onLogin(name: String) {
        // 🔒 Normaliza o nome e impede quebra de linha (ENTER)
        val normalizedName = name
            .replace("\n", "") // impede quebra de linha
            .trim() // remove espaços no início e fim
            .replace(Regex("\\s+"), " ") // reduz espaços duplos
            .uppercase() // converte para caixa alta

        if (normalizedName.isNotBlank()) {
            _uiState.update {
                it.copy(userName = normalizedName, currentScreen = Screen.HomeScreen)
            }
            Log.d("RCP_USER", "Nome do usuário: $normalizedName")
        } else {
            _uiState.update {
                it.copy(errorMessage = "Por favor, insira um nome válido.")
            }
        }
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

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- Processamento e análise de dados ---
    private fun processAndAnalyzeData(csvData: String) {
        val dataPoints = parseData(csvData)
        if (dataPoints.size < 10) {
            _uiState.update { it.copy(errorMessage = "Dados insuficientes para análise precisa.") }
            return
        }

        val accelerometerData = dataPoints.filter { it.type == "ACC" }
        if (accelerometerData.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nenhum dado de acelerômetro encontrado.") }
            return
        }

        val durationSeconds =
            (accelerometerData.last().timestamp - accelerometerData.first().timestamp) / 1000.0
        if (durationSeconds <= 0) {
            _uiState.update { it.copy(errorMessage = "Duração inválida nos dados do sensor.") }
            return
        }

        val samplingRate = accelerometerData.size / durationSeconds
        val magnitudes = accelerometerData.map { it.magnitude() }
        val unbiasedMagnitudes = removeMeanDrift(magnitudes)
        val filteredMagnitudes = highPassFilter(unbiasedMagnitudes, samplingRate, 0.5)

        val minPeakProminence = 1.5f
        val minPeakDistance = (samplingRate * 0.4).toInt()
        val allPeakIndices =
            findPeaksWithProminence(filteredMagnitudes, minPeakProminence, minPeakDistance)
        val validPeakIndices =
            if (allPeakIndices.size > 2) allPeakIndices.subList(1, allPeakIndices.size - 1)
            else allPeakIndices
        val totalCompressions = validPeakIndices.size

        val individualFrequencies =
            calculateIndividualFrequencies(accelerometerData, validPeakIndices)
        val individualDepths = calculateIndividualDepths(accelerometerData, validPeakIndices)
        val correctFrequencyCount = individualFrequencies.count { it in 100.0..120.0 }
        val correctDepthCount = individualDepths.count { it in 5.0..6.0 }
        val medianFrequency =
            if (individualFrequencies.isNotEmpty()) individualFrequencies.median() else 0.0
        val averageDepth =
            if (individualDepths.isNotEmpty()) individualDepths.average() else 0.0

        val newResult = TestResult(
            System.currentTimeMillis(),
            medianFrequency,
            averageDepth,
            totalCompressions,
            correctFrequencyCount,
            correctDepthCount
        )

        _uiState.update { it.copy(lastTestResult = newResult) }

        // ✅ Salva no banco local (Room)
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.newResultReceived(newResult, getApplication())
            Log.d("RCP_DEBUG_DB", "Novo resultado salvo no banco: ${newResult.timestamp}")
        }

        Log.d(
            "RCP_ANALYSIS",
            "Compressões: $totalCompressions | Freq: $medianFrequency | Prof: $averageDepth"
        )
    }

    // --- Funções auxiliares ---
    private fun removeMeanDrift(data: List<Float>): List<Float> {
        if (data.isEmpty()) return emptyList()
        val mean = data.average().toFloat()
        return data.map { it - mean }
    }

    private fun highPassFilter(
        data: List<Float>,
        samplingRate: Double,
        cutoffFrequency: Double
    ): List<Float> {
        if (data.isEmpty()) return emptyList()
        val rc = 1.0 / (2.0 * Math.PI * cutoffFrequency)
        val dt = 1.0 / samplingRate
        val alpha = rc / (rc + dt)
        val filtered = mutableListOf<Float>()
        filtered.add(data[0])
        for (i in 1 until data.size) {
            val value = alpha * (filtered[i - 1] + data[i] - data[i - 1])
            filtered.add(value.toFloat())
        }
        return filtered
    }

    private fun interpolateSignal(dataPoints: List<SensorDataPoint>): List<Float> {
        if (dataPoints.size < 5) return dataPoints.map { it.magnitude() }
        val interpolator = SplineInterpolator()
        val timestamps = dataPoints.map { it.timestamp.toDouble() }.toDoubleArray()
        val magnitudes = dataPoints.map { it.magnitude().toDouble() }.toDoubleArray()
        val spline: PolynomialSplineFunction = interpolator.interpolate(timestamps, magnitudes)
        return dataPoints.map { spline.value(it.timestamp.toDouble()).toFloat() }
    }

    private fun findPeaksWithProminence(
        data: List<Float>,
        minProminence: Float,
        minDistance: Int
    ): List<Int> {
        if (data.isEmpty()) return emptyList()
        val allPeaks =
            (1 until data.size - 1).filter { data[it] > data[it - 1] && data[it] > data[it + 1] }
        if (allPeaks.isEmpty()) return emptyList()
        val prominences = allPeaks.map { peak ->
            val value = data[peak]
            var leftMin = value
            for (i in peak - 1 downTo 0) {
                if (data[i] < leftMin) leftMin = data[i]
                if (data[i] > value) break
            }
            var rightMin = value
            for (i in peak + 1 until data.size) {
                if (data[i] < rightMin) rightMin = data[i]
                if (data[i] > value) break
            }
            value - maxOf(leftMin, rightMin)
        }
        val filteredPeaks = allPeaks.zip(prominences)
            .filter { (_, prominence) -> prominence >= minProminence }
            .map { (idx, _) -> idx }

        val result = mutableListOf<Int>()
        var last = -minDistance
        for (idx in filteredPeaks) {
            if (idx - last >= minDistance) {
                result.add(idx)
                last = idx
            }
        }
        return result
    }

    private fun parseData(csvData: String): List<SensorDataPoint> =
        csvData.split("\n").drop(1).mapNotNull { line ->
            try {
                val parts = line.split(",")
                if (parts.size == 5)
                    SensorDataPoint(
                        parts[0].toLong(),
                        parts[1],
                        parts[2].toFloat(),
                        parts[3].toFloat(),
                        parts[4].toFloat()
                    )
                else null
            } catch (e: Exception) {
                Log.e("PARSE_DATA", "Linha inválida: $line")
                null
            }
        }

    private fun calculateIndividualFrequencies(
        data: List<SensorDataPoint>,
        peaks: List<Int>
    ): List<Double> {
        if (peaks.size < 2) return emptyList()
        return (0 until peaks.size - 1).map {
            val start = data[peaks[it]].timestamp
            val end = data[peaks[it + 1]].timestamp
            val duration = (end - start) / 1000.0
            if (duration > 0) 60.0 / duration else 0.0
        }
    }

    private fun calculateIndividualDepths(
        data: List<SensorDataPoint>,
        peaks: List<Int>
    ): List<Double> {
        if (peaks.size < 2) return emptyList()
        val alpha = 0.8f
        var gravityZ = 0f
        val linearZ = data.map {
            gravityZ = alpha * gravityZ + (1 - alpha) * it.z
            it.z - gravityZ
        }
        return (0 until peaks.size - 1).map { i ->
            val accel = linearZ.slice(peaks[i]..peaks[i + 1])
            val times = data.slice(peaks[i]..peaks[i + 1]).map { it.timestamp }
            var velocity = 0.0
            var depth = 0.0
            var maxDepth = 0.0
            for (j in 0 until accel.size - 1) {
                val dt = (times[j + 1] - times[j]) / 1000.0
                velocity += accel[j] * dt
                depth += velocity * dt
                if (abs(depth) > maxDepth) maxDepth = abs(depth)
            }
            maxDepth * 100
        }.filter { it in 1.0..10.0 }
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(dataReceiver)
    }
}
