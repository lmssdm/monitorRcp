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

    // === Receivers de Broadcast ===
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
                // Força a navegação para a tela de dados se ainda não estiver nela
                if (_uiState.value.currentScreen != Screen.DataScreen) {
                    _uiState.update { it.copy(currentScreen = Screen.DataScreen) }
                }
            }
        }
    }

    init {
        // Inicializa banco de dados
        dataRepository.initDatabase(getApplication())

        // Coleta histórico em segundo plano
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

        // Registra receivers para comunicação com o serviço do Wear
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

        val (freq, depth) = calculateMetricsForChunk(accData)

        val freqFeedback = when {
            freq == 0.0 -> "Calculando..."
            freq in 100.0..120.0 -> "✅ Frequência OK"
            freq < 100 -> "⚠️ Mais rápido!"
            else -> "⚠️ Mais devagar!"
        }

        val depthFeedback = when {
            depth == 0.0 -> "..."
            depth in 5.0..6.0 -> "✅ Profundidade OK"
            depth < 5.0 -> "⚠️ Mais fundo!"
            else -> "⚠️ Menos fundo!"
        }

        val msg = "$freqFeedback | $depthFeedback (${freq.roundToInt()} CPM, ${"%.1f".format(depth)} cm)"
        _uiState.update { it.copy(intermediateFeedback = msg) }
    }

    private fun calculateMetricsForChunk(accData: List<SensorDataPoint>): Pair<Double, Double> {
        val duration = (accData.last().timestamp - accData.first().timestamp) / 1000.0
        if (duration <= 0) return 0.0 to 0.0
        val fs = accData.size / duration

        val mags = accData.map { it.magnitude() }
        val filtered = highPassFilter(removeMeanDrift(mags), fs, 0.5)

        // Detecção menos rigorosa para feedback rápido (proeminência 3.0f)
        val peaks = findPeaksWithProminence(filtered, 3.0f, (fs * 0.3).toInt())

        val freqs = calculateIndividualFrequencies(accData, peaks)
        val depths = calculateIndividualDepths(accData, peaks)

        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = if (depths.isNotEmpty()) depths.average() else 0.0

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

        // 1. Interpolação Cúbica (Spline) para suavizar e regularizar a taxa de amostragem
        val (interpTimes, interpSignal) = interpolateCubicSplineSafe(magnitudes, timestamps, 2)
        // Recalcula a frequência de amostragem baseada nos dados interpolados
        val fsInterp = interpSignal.size / ((interpTimes.last() - interpTimes.first()) / 1000.0)

        // 2. Filtragem: Remove drift DC e componentes de baixa frequência (movimento do corpo)
        val unbiased = removeMeanDrift(interpSignal)
        val filtered = highPassFilter(unbiased, fsInterp, 0.5)

        // 3. Detecção de Picos RIGOROSA para contagem final precisa
        // minProm = 5.0f: Exige que a compressão seja forte e clara
        // minDist = 0.35s: Limita a frequência máxima detectável a ~170 CPM para evitar ruído
        val peaks = findPeaksWithProminence(filtered, 5.0f, (fsInterp * 0.35).toInt())
        val total = peaks.size

        // 4. Cálculos métricos usando os dados interpolados
        val freqs = calculateIndividualFrequenciesInterp(interpTimes, peaks)
        val depths = calculateIndividualDepthsInterp(interpSignal, interpTimes, peaks)

        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = if (depths.isNotEmpty()) depths.average() else 0.0
        val correctFreq = freqs.count { it in 100.0..120.0 }
        val correctDepth = depths.count { it in 5.0..6.0 }

        val result = TestResult(
            System.currentTimeMillis(), medFreq, avgDepth, total, correctFreq, correctDepth
        )

        _uiState.update { it.copy(lastTestResult = result, intermediateFeedback = "Teste finalizado!") }
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.newResultReceived(result, getApplication())
        }
    }

    // === Funções Matemáticas e Auxiliares ===

    // Interpolação Cúbica (Spline) com proteção contra erros
    private fun interpolateCubicSplineSafe(data: List<Float>, timestamps: List<Long>, upsampleFactor: Int = 2): Pair<List<Long>, List<Float>> {
        return try {
            if (data.size < 5) return timestamps to data
            Log.d("RCP_SPLINE", "Iniciando Spline com ${data.size} pontos.")
            // Garante que os timestamps sejam únicos e ordenados para a Spline não falhar
            val sorted = timestamps.zip(data).sortedBy { it.first }.distinctBy { it.first }
            val x = sorted.map { it.first / 1000.0 }.toDoubleArray() // segundos
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

    // Algoritmo de detecção de picos baseado em proeminência
    private fun findPeaksWithProminence(data: List<Float>, minProm: Float, minDist: Int): List<Int> {
        val peaks = (1 until data.lastIndex).filter { data[it] > data[it - 1] && data[it] > data[it + 1] }
        val valid = mutableListOf<Int>()
        var last = -minDist
        for (i in peaks) {
            if (i - last < minDist) continue
            // Calcula proeminência local simplificada
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

    // Filtro Passa-Alta (Butterworth 1ª ordem simplificado)
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

    // --- Calculadoras para dados RAW (Chunk) ---
    private fun calculateIndividualFrequencies(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { a, b ->
            val dt = (data[b].timestamp - data[a].timestamp) / 1000.0
            if (dt > 0.2) 60.0 / dt else null
        }.filterNotNull()
    }

    private fun calculateIndividualDepths(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        // Estimativa rápida de profundidade por integração dupla simples
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
            (maxPos * 100).coerceIn(0.0, 12.0) // Retorna em cm
        }
    }

    // --- Calculadoras para dados INTERPOLADOS (Final) ---
    private fun calculateIndividualFrequenciesInterp(times: List<Long>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { a, b ->
            val dt = (times[b] - times[a]) / 1000.0
            if (dt > 0.2) 60.0 / dt else null
        }.filterNotNull()
    }

    private fun calculateIndividualDepthsInterp(signal: List<Float>, times: List<Long>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { start, end ->
            var v = 0.0
            var pos = 0.0
            var maxPos = 0.0
            for (i in start until end) {
                val dt = (times[i + 1] - times[i]) / 1000.0
                v += signal[i] * dt
                pos += v * dt
                maxPos = maxOf(maxPos, abs(pos))
            }
            (maxPos * 100).coerceIn(0.0, 15.0) // Cm com limite de segurança
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