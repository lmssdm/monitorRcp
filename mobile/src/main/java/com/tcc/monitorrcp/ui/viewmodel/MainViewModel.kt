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
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// A classe UiState NÃO está mais neste arquivo. Ela está em UiState.kt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val dataRepository = DataRepository

    // === Variáveis da Máquina de Estados para Análise Final ===
    private var waitingForRecoil = false
    private var lastValidCompressionTime = 0L

    // --- ALTERAÇÃO 1: AJUSTE DO LIMIAR ---
    // Limiar de impacto baixo (0.6f)
    private val IMPACT_THRESHOLD_FINAL = 0.6f

    // --- ALTERAÇÃO 2: LIMIAR DE RECUO ---
    // Limiar de recuo BEM mais baixo (0.1f) para criar uma "janela" maior
    private val RECOIL_THRESHOLD_FINAL = 0.1f
    private val MIN_COMPRESSION_INTERVAL_FINAL = 300L

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

                // Salva o arquivo CSV completo no cache para depuração
                saveRawDataForDebugging(getApplication(), finalData)

                processAndSaveFinalData(finalData)

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

        val timestampsRaw = accData.map { it.timestamp }
        val magnitudes = accData.map { it.magnitude() }

        // 1. Interpolação Cúbica (Spline) para suavizar e regularizar a taxa de amostragem
        val (interpTimes, interpSignal) = interpolateCubicSplineSafe(magnitudes, timestampsRaw, 2)
        // Recalcula a frequência de amostragem baseada nos dados interpolados
        val fsInterp = interpSignal.size / ((interpTimes.last() - interpTimes.first()) / 1000.0)

        // 2. Filtragem: Remove drift DC e componentes de baixa frequência (movimento do corpo)
        val unbiased = removeMeanDrift(interpSignal)

        // --- ALTERAÇÃO 3: AJUSTE DO FILTRO ---
        // Mantemos o filtro em 0.5 Hz para cortar o ruído
        val filtered = highPassFilter(unbiased, fsInterp, 0.5)


        // --- LÓGICA DE DETECÇÃO DE PICO MODIFICADA ---
        waitingForRecoil = false
        lastValidCompressionTime = 0L
        val validPeakIndices = mutableListOf<Int>() // Lista para guardar os ÍNDICES dos picos válidos

        for (i in filtered.indices) {
            val currentMag = filtered[i]
            val now = interpTimes[i] // Usa o timestamp interpolado

            if (!waitingForRecoil) {
                // ESTADO 1: Procurando IMPACTO (pico positivo)
                if (currentMag > IMPACT_THRESHOLD_FINAL && (now - lastValidCompressionTime > MIN_COMPRESSION_INTERVAL_FINAL)) {
                    validPeakIndices.add(i) // Guarda o ÍNDICE deste pico
                    waitingForRecoil = true
                    lastValidCompressionTime = now

                    // Imprime o valor do pico que ele acabou de contar
                    Log.d("RCP_PEAK_DEBUG", "Pico detectado! Valor: $currentMag")
                }
            } else {
                // ESTADO 2: Aguardando RECUO (vale)
                if (currentMag < RECOIL_THRESHOLD_FINAL) {
                    waitingForRecoil = false
                }
                // Timeout de segurança (se travar por 1.5s, reseta)
                if (now - lastValidCompressionTime > 1500) {
                    waitingForRecoil = false
                }
            }
        }

        val peaks = validPeakIndices
        val total = peaks.size
        // --- FIM DA MODIFICAÇÃO ---

        // 4. Cálculos métricos (continuam iguais, agora usam os picos mais precisos)
        val freqs = calculateIndividualFrequenciesInterp(interpTimes, peaks)
        val depths = calculateIndividualDepthsInterp(interpSignal, interpTimes, peaks) // Usa interpSignal (sem filtro)

        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = if (depths.isNotEmpty()) depths.average() else 0.0
        val correctFreq = freqs.count { it in 100.0..120.0 }
        val correctDepth = depths.count { it in 5.0..6.0 }

        val result = TestResult(
            System.currentTimeMillis(), medFreq, avgDepth, total, correctFreq, correctDepth
        )

        Log.d("RCP_DEBUG", "ANÁLISE FINAL: Total de picos contados: $total")

        _uiState.update { it.copy(lastTestResult = result, intermediateFeedback = "Teste finalizado!") }
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.newResultReceived(result, getApplication())
        }
    }

    /**
     * Salva a string de dados brutos em um arquivo .csv no diretório de cache do app.
     * O caminho do arquivo será impresso no Logcat para depuração.
     */
    private fun saveRawDataForDebugging(context: Context, data: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "RCP_RAW_DATA_${timestamp}.csv"
            val cacheDir = context.cacheDir
            val file = File(cacheDir, fileName)

            file.writeText(data)

            // Imprime o caminho absoluto no Logcat para ser fácil de achar
            Log.d("RCP_DEBUG", "Dados brutos de depuração salvos em: ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e("RCP_DEBUG", "Falha ao salvar arquivo de depuração", e)
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
            if (sorted.size < 5) return timestamps to data // Proteção

            val x = sorted.map { it.first / 1000.0 }.toDoubleArray() // segundos
            val y = sorted.map { it.second.toDouble() }.toDoubleArray()

            val spline = SplineInterpolator().interpolate(x, y)

            val totalDur = x.last() - x.first()
            if (totalDur <= 0) return timestamps to data // Proteção

            val newCount = (sorted.size) * upsampleFactor

            val newTimes = DoubleArray(newCount) { i -> x.first() + i * (totalDur / (newCount -1)) } // (newCount - 1) para incluir o último ponto
            val newValues = newTimes.map { spline.value(it).toFloat() }
            val newTimestamps = newTimes.map { (it * 1000).roundToLong() }

            Log.d("RCP_SPLINE", "Spline finalizada. Gerados ${newValues.size} pontos.")
            newTimestamps to newValues
        } catch (e: Exception) {
            Log.e("RCP_SPLINE", "Falha na Spline, usando dados originais: ${e.message}")
            timestamps to data
        }
    }

    // Algoritmo de detecção de picos baseado em proeminência (Usado apenas para CHUNK agora)
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
        if (data.isEmpty() || fs <= 0.0) return data // Proteção contra divisão por zero
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
                if (i + 1 >= data.size) break // Proteção
                val dt = (data[i + 1].timestamp - data[i].timestamp) / 1000.0
                if (dt < 0) continue // Proteção
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

    // Modificado para usar 'signal' (dados brutos interpolados) para integração
    private fun calculateIndividualDepthsInterp(signal: List<Float>, times: List<Long>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { start, end ->
            var v = 0.0
            var pos = 0.0
            var maxPos = 0.0
            // Usa o sinal interpolado mas NÃO filtrado (unbiased) para calcular profundidade
            val dataToIntegrate = removeMeanDrift(signal)

            for (i in start until end) {
                // Adiciona verificação de índice para segurança
                if (i + 1 >= times.size || i >= dataToIntegrate.size) break

                val dt = (times[i + 1] - times[i]) / 1000.0
                if (dt < 0) continue // Proteção contra timestamps inválidos
                v += dataToIntegrate[i] * dt
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