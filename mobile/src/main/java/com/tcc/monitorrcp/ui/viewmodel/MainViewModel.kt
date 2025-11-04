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
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class UiState(
    val currentScreen: Screen = Screen.SplashScreen,
    val userName: String? = null,
    val lastReceivedData: String = "Aguardando início...",
    val intermediateFeedback: String = "Inicie o teste no relógio.",
    val lastTestResult: TestResult? = null,
    val history: List<TestResult> = emptyList(),
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val dataRepository = DataRepository

    // === Receivers ===
    private val dataChunkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(ListenerService.EXTRA_DATA)?.let { chunkData ->
                _uiState.update { it.copy(lastReceivedData = chunkData) }
                analyzeChunkAndProvideFeedback(chunkData)
            }
        }
    }

    private val finalDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(ListenerService.EXTRA_DATA)?.let { finalData ->
                _uiState.update { it.copy(lastReceivedData = finalData) }
                processAndSaveFinalData(finalData)
                if (_uiState.value.currentScreen != Screen.DataScreen) {
                    _uiState.update { it.copy(currentScreen = Screen.DataScreen) }
                }
            }
        }
    }

    init {
        dataRepository.initDatabase(getApplication())
        Log.d("RCP_DEBUG_DB", "Banco Room inicializado")

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

    // === UI Events ===
    fun onLogin(name: String) {
        val normalizedName = name.replace("\n", "").trim().replace(Regex("\\s+"), " ").uppercase()
        if (normalizedName.isNotBlank()) {
            _uiState.update { it.copy(userName = normalizedName, currentScreen = Screen.HomeScreen) }
        } else {
            _uiState.update { it.copy(errorMessage = "Por favor, insira um nome válido.") }
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

    // === Processamento ===
    private fun analyzeChunkAndProvideFeedback(chunkData: String) {
        val dataPoints = parseData(chunkData)
        if (dataPoints.size < 5) return
        val accData = dataPoints.filter { it.type == "ACC" }
        if (accData.isEmpty()) return

        val (freq, depth) = calculateMetricsForChunk(accData)

        // Feedback calibrado
        val freqFeedback = when {
            freq == 0.0 -> "Calculando..."
            freq in 100.0..120.0 -> "Frequência OK"
            freq < 100 -> "Mais rápido!"
            else -> "Mais devagar!"
        }

        val depthFeedback = when {
            depth == 0.0 -> "Calculando..."
            depth in 5.0..6.0 -> "Profundidade OK"
            depth < 5.0 -> "Mais fundo!"
            else -> "Menos fundo!"
        }

        val msg = "$freqFeedback | $depthFeedback (${freq.roundToInt()} CPM, ${"%.1f".format(depth)} cm)"
        _uiState.update { it.copy(intermediateFeedback = msg) }
    }

    private fun processAndSaveFinalData(finalData: String) {
        val dataPoints = parseData(finalData)
        val accData = dataPoints.filter { it.type == "ACC" }
        if (accData.size < 10) {
            _uiState.update { it.copy(intermediateFeedback = "Dados insuficientes.") }
            return
        }

        val duration = (accData.last().timestamp - accData.first().timestamp) / 1000.0
        if (duration <= 0) return

        val fs = accData.size / duration
        val magnitudes = accData.map { it.magnitude() }
        val timestamps = accData.map { it.timestamp }

        val (interpTimes, interpSignal) = interpolateCubicSplineSafe(magnitudes, timestamps, 2)
        val unbiased = removeMeanDrift(interpSignal)
        val fsInterp = interpSignal.size / ((interpTimes.last() - interpTimes.first()) / 1000.0)
        val filtered = highPassFilter(unbiased, fsInterp, 0.5)

        val peaks = findPeaksWithProminence(filtered, 1.0f, (fsInterp * 0.4).toInt())
        val total = peaks.size

        val freqs = calculateIndividualFrequencies(accData, peaks)
        val depths = calculateIndividualDepths(accData, peaks)
        val correctFreq = freqs.count { it in 100.0..120.0 }
        val correctDepth = depths.count { it in 5.0..6.0 }

        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = if (depths.isNotEmpty()) depths.average() else 0.0

        val result = TestResult(
            System.currentTimeMillis(),
            medFreq,
            avgDepth,
            total,
            correctFreq,
            correctDepth
        )

        _uiState.update { it.copy(lastTestResult = result, intermediateFeedback = "Teste concluído!") }

        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.newResultReceived(result, getApplication())
        }
    }

    // === FUNÇÃO CORRIGIDA E CALIBRADA ===
    private fun calculateMetricsForChunk(accData: List<SensorDataPoint>): Pair<Double, Double> {
        if (accData.size < 5) return 0.0 to 0.0

        val durationSec = (accData.last().timestamp - accData.first().timestamp) / 1000.0
        if (durationSec <= 0) return 0.0 to 0.0

        val fs = accData.size / durationSec
        val magnitudes = accData.map { it.magnitude() }
        val timestamps = accData.map { it.timestamp }

        val (interpTimes, interpSignal) = interpolateCubicSplineSafe(magnitudes, timestamps, 2)
        val unbiased = removeMeanDrift(interpSignal)
        val fsInterp = interpSignal.size / ((interpTimes.last() - interpTimes.first()) / 1000.0)
        val filtered = highPassFilter(unbiased, fsInterp, 0.5)

        val peaks = findPeaksWithProminence(filtered, 1.0f, (fsInterp * 0.4).toInt())

        // Filtros calibrados
        val freqs = calculateIndividualFrequencies(accData, peaks)
        val depths = calculateIndividualDepths(accData, peaks).map { it.coerceIn(0.0, 10.0) }

        val medianFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = if (depths.isNotEmpty()) depths.average() else 0.0

        return medianFreq to avgDepth
    }

    // === Utilitários ===
    private fun interpolateCubicSplineSafe(
        data: List<Float>,
        timestamps: List<Long>,
        upsampleFactor: Int = 2
    ): Pair<List<Long>, List<Float>> {
        return try {
            if (data.size < 4 || timestamps.size != data.size)
                return timestamps to data

            val sorted = timestamps.zip(data).sortedBy { it.first }
            val x = sorted.map { it.first / 1000.0 }.toDoubleArray()
            val y = sorted.map { it.second.toDouble() }.toDoubleArray()
            val spline = SplineInterpolator().interpolate(x, y)
            val totalDur = x.last() - x.first()
            val newCount = (x.size - 1) * upsampleFactor
            val newTimes = DoubleArray(newCount) { i -> x.first() + i * (totalDur / newCount) }
            val newValues = newTimes.map { spline.value(it) }
            val newTimestamps = newTimes.map { (it * 1000).roundToLong() }
            newTimestamps to newValues.map { it.toFloat() }
        } catch (e: Exception) {
            Log.e("SPLINE", "Erro na interpolação cúbica: ${e.message}")
            timestamps to data
        }
    }

    private fun parseData(csvData: String): List<SensorDataPoint> =
        csvData.split("\n").drop(1).mapNotNull { line ->
            try {
                val p = line.split(",")
                if (p.size == 5)
                    SensorDataPoint(p[0].toLong(), p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat())
                else null
            } catch (_: Exception) { null }
        }

    private fun removeMeanDrift(data: List<Float>): List<Float> {
        if (data.isEmpty()) return data
        val mean = data.average().toFloat()
        return data.map { it - mean }
    }

    private fun highPassFilter(data: List<Float>, fs: Double, cutoff: Double): List<Float> {
        if (data.isEmpty()) return data
        val rc = 1.0 / (2 * Math.PI * cutoff)
        val dt = 1.0 / fs
        val alpha = rc / (rc + dt)
        val out = MutableList(data.size) { 0f }
        out[0] = data[0]
        for (i in 1 until data.size) {
            out[i] = (alpha * (out[i - 1] + data[i] - data[i - 1])).toFloat()
        }
        return out
    }

    private fun findPeaksWithProminence(data: List<Float>, minProm: Float, minDist: Int): List<Int> {
        if (data.size < 3) return emptyList()
        val peaks = (1 until data.lastIndex).filter { data[it] > data[it - 1] && data[it] > data[it + 1] }
        val valid = mutableListOf<Int>()
        var last = -minDist
        for (i in peaks) {
            val left = (0 until i).map { data[it] }.minOrNull() ?: data[i]
            val right = ((i + 1) until data.size).map { data[it] }.minOrNull() ?: data[i]
            val prom = data[i] - maxOf(left, right)
            if (prom > minProm && i - last >= minDist) {
                valid.add(i)
                last = i
            }
        }
        return valid
    }

    private fun calculateIndividualFrequencies(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        if (peaks.size < 2) return emptyList()
        return peaks.windowed(2).mapNotNull { (i1, i2) ->
            val dt = (data[i2].timestamp - data[i1].timestamp) / 1000.0
            if (dt > 0.1) 60.0 / dt else null
        }
    }

    private fun calculateIndividualDepths(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        if (peaks.size < 2) return emptyList()
        val alpha = 0.8f
        var gZ = data.first().z
        val linZ = data.map {
            gZ = alpha * gZ + (1 - alpha) * it.z
            it.z - gZ
        }
        return peaks.windowed(2).mapNotNull { (s, e) ->
            if (s < 0 || e >= linZ.size || s >= e) return@mapNotNull null
            var v = 0.0
            var x = 0.0
            var xMax = 0.0
            for (j in s until e - 1) {
                val dt = (data[j + 1].timestamp - data[j].timestamp) / 1000.0
                val a = (linZ[j] + linZ[j + 1]) / 2.0
                v += a * dt
                x += v * dt
                if (abs(x) > xMax) xMax = abs(x)
            }
            // Calibração: limite entre 5 e 6 cm como meta ideal
            (xMax * 100).takeIf { it in 0.5..10.0 }
        }
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val s = sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2.0 else s[m]
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(dataChunkReceiver)
            getApplication<Application>().unregisterReceiver(finalDataReceiver)
        } catch (_: Exception) { }
    }
}
