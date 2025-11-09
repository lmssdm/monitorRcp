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
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.getHistoryFlow().collectLatest { entities ->
                val historyList = entities.map {
                    TestResult(
                        it.timestamp, it.medianFrequency, it.averageDepth,
                        it.totalCompressions, it.correctFrequencyCount, it.correctDepthCount
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
    }

    fun onNavigateTo(screen: Screen) { _uiState.update { it.copy(currentScreen = screen) } }
    fun onSplashScreenTimeout() {
        if (_uiState.value.currentScreen == Screen.SplashScreen) {
            _uiState.update { it.copy(currentScreen = Screen.LoginScreen) }
        }
    }
    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }

    // === Análise Rápida (Chunks) ===
    private fun analyzeChunkAndProvideFeedback(chunkData: String) {
        val dataPoints = parseData(chunkData)
        val accData = dataPoints.filter { it.type == "ACC" }
        if (accData.size < 5) return

        // [MUDANÇA] Desativamos o 'depth' do cálculo de chunk
        val (freq, _) = calculateMetricsForChunk(accData)

        val freqFeedback = when {
            freq == 0.0 -> "Calculando..."
            freq in 100.0..120.0 -> "✅ Frequência OK"
            freq < 100 -> "⚠️ Mais rápido!"
            else -> "⚠️ Mais devagar!"
        }

        // [MUDANÇA] Mensagem agora só mostra a frequência
        val msg = "$freqFeedback (${freq.roundToInt()} CPM)"
        Log.d("RCP_DEBUG", "analyzeChunk SUCESSO. Atualizando feedback para: $msg")
        _uiState.update { it.copy(intermediateFeedback = msg) }
    }

    private fun calculateMetricsForChunk(accData: List<SensorDataPoint>): Pair<Double, Double> {
        val duration = (accData.last().timestamp - accData.first().timestamp) / 1000.0
        if (duration <= 0) return 0.0 to 0.0
        val fs = accData.size / duration

        val mags = accData.map { it.magnitude() }
        val filtered = highPassFilter(removeMeanDrift(mags), fs, 0.5)

        val peaks = findPeaksWithProminence(filtered, 3.0f, (fs * 0.3).toInt())
        val freqs = calculateIndividualFrequencies(accData, peaks)

        // [MUDANÇA] Profundidade não é calculada no chunk, retorna 0.0
        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = 0.0

        return medFreq to avgDepth
    }

    // === Análise Final (Precisa) ===
    private fun processAndSaveFinalData(finalData: String) {
        val dataPoints = parseData(finalData)
        val accData = dataPoints.filter { it.type == "ACC" }
        if (accData.size < 20) {
            _uiState.update { it.copy(intermediateFeedback = "Dados insuficientes para análise final.") }
            return
        }

        val timestamps = accData.map { it.timestamp }
        val magnitudes = accData.map { it.magnitude() }

        val (interpTimes, interpSignal) = interpolateCubicSplineSafe(magnitudes, timestamps, 2)
        val fsInterp = interpSignal.size / ((interpTimes.last() - interpTimes.first()) / 1000.0)

        val unbiased = removeMeanDrift(interpSignal)
        val filtered = highPassFilter(unbiased, fsInterp, 0.5)

        // [MUDANÇA] Filtro de pico menos rigoroso (3.0f) para corrigir a contagem total
        val peaks = findPeaksWithProminence(filtered, 3.0f, (fsInterp * 0.35).toInt())
        val total = peaks.size

        val freqs = calculateIndividualFrequenciesInterp(interpTimes, peaks)

        // [MUDANÇA] Cálculo de profundidade desativado temporariamente
        // val depths = calculateIndividualDepthsInterp(interpSignal, interpTimes, peaks)
        val depths = emptyList<Double>()
        val avgDepth = 0.0
        val correctDepth = 0

        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        // val avgDepth = if (depths.isNotEmpty()) depths.average() else 0.0
        val correctFreq = freqs.count { it in 100.0..120.0 }
        // val correctDepth = depths.count { it in 5.0..6.0 }

        val result = TestResult(
            System.currentTimeMillis(), medFreq, avgDepth, total, correctFreq, correctDepth
        )

        _uiState.update { it.copy(lastTestResult = result, intermediateFeedback = "Teste finalizado!") }
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.newResultReceived(result, getApplication())
        }
    }

    // === Funções Matemáticas e Auxiliares ===

    private fun interpolateCubicSplineSafe(data: List<Float>, timestamps: List<Long>, upsampleFactor: Int = 2): Pair<List<Long>, List<Float>> {
        return try {
            if (data.size < 5) return timestamps to data
            Log.d("RCP_SPLINE", "Iniciando Spline com ${data.size} pontos.")
            val sorted = timestamps.zip(data).sortedBy { it.first }.distinctBy { it.first }
            val x = sorted.map { it.first / 1000.0 }.toDoubleArray()
            val y = sorted.map { it.second.toDouble() }.toDoubleArray()
            val spline = SplineInterpolator().interpolate(x, y)
            val totalDur = x.last() - x.first()
            val newCount = (sorted.size - 1) * upsampleFactor
            val newTimes = DoubleArray(newCount) { i -> x.first() + i * (totalDur / newCount) }
            val newValues = newTimes.map { spline.value(it).toFloat() }
            val newTimestamps = newTimes.map { (it * 1000).roundToLong() }
            Log.d("RCP_SPLINE", "Spline finalizada. Gerados ${newValues.size} pontos.")
            newTimestamps to newValues
        } catch (e: Exception) {
            Log.e("RCP_SPLINE", "Falha na Spline, usando dados originais: ${e.message}")
            timestamps to data
        }
    }

    private fun findPeaksWithProminence(data: List<Float>, minProm: Float, minDist: Int): List<Int> {
        val peaks = (1 until data.lastIndex).filter { data[it] > data[it - 1] && data[it] > data[it + 1] }
        val valid = mutableListOf<Int>()
        var last = -minDist
        for (i in peaks) {
            if (i - last < minDist) continue
            val windowStart = (i - minDist * 2).coerceAtLeast(0)
            val windowEnd = (i + minDist * 2).coerceAtMost(data.size)
            val localMin = data.subList(windowStart, windowEnd).minOrNull() ?: 0f
            if ((data[i] - localMin) > minProm) {
                valid.add(i)
                last = i
            }
        }
        return valid
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

    private fun removeMeanDrift(data: List<Float>): List<Float> {
        if (data.isEmpty()) return data
        val mean = data.average().toFloat()
        return data.map { it - mean }
    }

    private fun calculateIndividualFrequencies(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { a, b ->
            val dt = (data[b].timestamp - data[a].timestamp) / 1000.0
            if (dt > 0.2) 60.0 / dt else null
        }.filterNotNull()
    }

    private fun calculateIndividualDepths(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        // Esta função não está sendo mais usada no chunk, mas mantida aqui.
        val alpha = 0.8f
        var g = data.first().magnitude()
        val linear = data.map {
            g = alpha * g + (1 - alpha) * it.magnitude()
            it.magnitude() - g
        }
        return peaks.zipWithNext { start, end ->
            var v = 0.0
            var pos = 0.0
            var maxPos = 0.0
            for (i in start until end - 1) {
                val dt = (data[i + 1].timestamp - data[i].timestamp) / 1000.0
                v += linear[i] * dt
                pos += v * dt
                maxPos = maxOf(maxPos, abs(pos))
            }
            (maxPos * 100).coerceIn(0.0, 12.0)
        }
    }

    private fun calculateIndividualFrequenciesInterp(times: List<Long>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { a, b ->
            val dt = (times[b] - times[a]) / 1000.0
            if (dt > 0.2) 60.0 / dt else null
        }.filterNotNull()
    }

    private fun calculateIndividualDepthsInterp(signal: List<Float>, times: List<Long>, peaks: List<Int>): List<Double> {
        // Esta função está desativada na chamada principal (processAndSaveFinalData)
        return peaks.zipWithNext { start, end ->
            var v = 0.0
            var pos = 0.0
            var maxPos = 0.0
            for (i in start until end) {
                if (i >= times.size - 1) break
                val dt = (times[i + 1] - times[i]) / 1000.0
                if (dt <= 0) continue
                v += signal[i] * dt
                pos += v * dt
                maxPos = maxOf(maxPos, abs(pos))
            }
            (maxPos * 100).coerceIn(0.0, 15.0)
        }
    }

    private fun List<Double>.median() = sorted().let {
        if (it.isEmpty()) 0.0 else if (it.size % 2 == 0) (it[it.size / 2 - 1] + it[it.size / 2]) / 2.0 else it[it.size / 2]
    }

    private fun parseData(csvData: String): List<SensorDataPoint> =
        csvData.split("\n").drop(1).mapNotNull {
            try {
                val p = it.split(",")
                if (p.size >= 5) SensorDataPoint(p[0].toLong(), p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat()) else null
            } catch (_: Exception) { null }
        }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(dataChunkReceiver)
            getApplication<Application>().unregisterReceiver(finalDataReceiver)
        } catch (_: Exception) { }
    }
}