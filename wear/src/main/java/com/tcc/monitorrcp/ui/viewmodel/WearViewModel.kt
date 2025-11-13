package com.tcc.monitorrcp.ui.viewmodel

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.monitorrcp.data.SensorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

data class WearUiState(
    val isCapturing: Boolean = false,
    val elapsedTimeInMillis: Long = 0L,
    val feedbackText: String = "Aguardando compressões..."
) {
    val timerText: String
        get() {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeInMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeInMillis) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

class WearViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState = _uiState.asStateFlow()

    private val repository = SensorRepository(application, viewModelScope, this)

    private val SEND_INTERVAL = 5000L
    private var capturedData = mutableListOf("Timestamp,Type,X,Y,Z")
    private var timerJob: Job? = null
    private var chunkSendJob: Job? = null

    // --- [MUDANÇA AQUI] Job para o Metrônomo ---
    private var metronomeJob: Job? = null
    // Intervalo para 110 bpm (60000ms / 110 = 545ms)
    private val METRONOME_INTERVAL_MS = 545L
    // --- FIM DA MUDANÇA ---

    private var lastPeakTime: Long? = null
    private var gravityMagnitude = 9.8f
    private val alpha = 0.8f
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private var isFirstReading = true
    private var lastValidCompressionTime = 0L
    private var waitingForRecoil = false

    private val IMPACT_THRESHOLD = 3.0f
    private val RECOIL_THRESHOLD = 0.5f
    private val MIN_COMPRESSION_INTERVAL = 300L

    fun startCapture() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
        // --- [MUDANÇA AQUI] Cancela o metrônomo anterior ---
        metronomeJob?.cancel()
        // --- FIM DA MUDANÇA ---

        capturedData.clear()
        capturedData.add("Timestamp,Type,X,Y,Z")

        lastPeakTime = null
        lastValidCompressionTime = 0L
        isFirstReading = true
        waitingForRecoil = false
        gravityMagnitude = 9.8f

        _uiState.update { it.copy(isCapturing = true, elapsedTimeInMillis = 0L, feedbackText = "Iniciando captura...") }
        repository.startCapture()

        val startTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val currentElapsedTime = System.currentTimeMillis() - startTime
                _uiState.update { it.copy(elapsedTimeInMillis = currentElapsedTime) }
                delay(100)
            }
        }

        chunkSendJob = viewModelScope.launch {
            while (isActive) {
                delay(SEND_INTERVAL)
                if (_uiState.value.isCapturing && capturedData.size > 1) {
                    val dataToSend = ArrayList(capturedData)
                    capturedData.clear()
                    capturedData.add("Timestamp,Type,X,Y,Z")
                    Log.d("WearViewModel", "Enviando chunk com ${dataToSend.size - 1} pontos.")
                    repository.sendSensorDataChunk(dataToSend)
                }
            }
        }

        // --- [MUDANÇA AQUI] Inicia o metrônomo ---
        startMetronome()
        // --- FIM DA MUDANÇA ---
    }

    // --- [MUDANÇA AQUI] Nova função para o metrônomo ---
    private fun startMetronome() {
        metronomeJob = viewModelScope.launch {
            while (isActive) {
                // Toca a vibração primeiro
                vibrateShort()
                // Espera o intervalo
                delay(METRONOME_INTERVAL_MS)
            }
        }
    }

    // --- FIM DA MUDANÇA ---

    fun stopAndSendData() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
        // --- [MUDANÇA AQUI] Para o metrônomo ao parar ---
        metronomeJob?.cancel()
        // --- FIM DA MUDANÇA ---

        repository.stopCapture()

        if (capturedData.size > 1) {
            val finalData = ArrayList(capturedData)
            repository.sendEndOfTestData(finalData)
        } else {
            repository.sendEndOfTestData(listOf("Timestamp,Type,X,Y,Z"))
        }
        _uiState.value = WearUiState()
        resetFeedback()
    }

    // [ALTERAÇÃO] Esta função foi modificada para capturar AMBOS os sensores
    override fun onSensorChanged(event: SensorEvent?) {
        if (!_uiState.value.isCapturing || event == null) return

        val timestamp = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Usa um 'when' para lidar com os diferentes tipos de sensor
        when (event.sensor.type) {

            // Caso 1: Acelerómetro
            Sensor.TYPE_ACCELEROMETER -> {
                // Salva os dados brutos para o telemóvel fazer a análise completa (Profundidade)
                capturedData.add("$timestamp,ACC,$x,$y,$z")

                // Calcula a Magnitude Total
                val currentMagnitude = sqrt(x*x + y*y + z*z)

                if (isFirstReading) {
                    gravityMagnitude = currentMagnitude
                    isFirstReading = false
                }

                // Analisa a magnitude para o feedback LOCAL (vibração e texto no relógio)
                val feedback = analyzeCompressionMagnitude(currentMagnitude)
                if (feedback != null) {
                    _uiState.update { it.copy(feedbackText = feedback) }
                }
            }

            // [ALTERAÇÃO] Caso 2: Giroscópio
            Sensor.TYPE_GYROSCOPE -> {
                // Apenas guarda os dados brutos para o telemóvel.
                // Não é necessário para o feedback local do relógio.
                capturedData.add("$timestamp,GYR,$x,$y,$z")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ------------------------------------------------------------
    // ANÁLISE LOCAL (só para feedback do relógio, não afeta o telemóvel)
    // ------------------------------------------------------------
    private fun analyzeCompressionMagnitude(currentMag: Float): String? {
        val now = System.currentTimeMillis()

        // 1. Filtro Passa-Alta na Magnitude
        gravityMagnitude = alpha * gravityMagnitude + (1 - alpha) * currentMag
        val linearMagnitude = currentMag - gravityMagnitude

        if (!waitingForRecoil) {
            // ESTADO 1: Procurando IMPACTO (pico positivo de magnitude)
            if (linearMagnitude > IMPACT_THRESHOLD && (now - lastValidCompressionTime > MIN_COMPRESSION_INTERVAL)) {
                waitingForRecoil = true
                lastValidCompressionTime = now

                // Feedback de Frequência
                val last = lastPeakTime
                lastPeakTime = now
                if (last != null) {
                    val interval = now - last
                    if (interval > 0) {
                        val frequency = 60_000.0 / interval
                        return when {
                            // --- [MUDANÇA AQUI] Removida a vibração daqui ---
                            // O metrônomo agora controla a vibração.
                            // Manter a vibração aqui faria o relógio vibrar duas vezes.
                            frequency < 100 -> "⚠️ Muito lento (${frequency.toInt()} cpm)"
                            frequency > 120 -> "⚠️ Muito rápido (${frequency.toInt()} cpm)"
                            else -> "✅ Ritmo OK (${frequency.toInt()} cpm)"
                            // --- FIM DA MUDANÇA ---
                        }
                    }
                }
            }
        } else {
            // ESTADO 2: Aguardando RETORNO (alivio da força)
            if (linearMagnitude < RECOIL_THRESHOLD) {
                waitingForRecoil = false
            }
            if (now - lastValidCompressionTime > 1500) {
                waitingForRecoil = false
            }
        }
        return null
    }

    private fun vibrateShort() {
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    private fun resetFeedback() {
        _uiState.update { it.copy(feedbackText = "Aguardando compressões...") }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        chunkSendJob?.cancel()
        metronomeJob?.cancel()
        repository.stopCapture()
    }
}