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

    private val SEND_INTERVAL = 10000L
    private var capturedData = mutableListOf("Timestamp,Type,X,Y,Z")
    private var timerJob: Job? = null
    private var chunkSendJob: Job? = null

    private var lastPeakTime: Long? = null
    // Inicializa com 9.8 (gravidade média da Terra), será ajustado pelo filtro
    private var gravityMagnitude = 9.8f
    private val alpha = 0.8f
    private val vibrator = application.getSystemService(Vibrator::class.java)

    // === CONTROLE DE PRECISÃO ===
    private var isFirstReading = true
    private var lastValidCompressionTime = 0L
    private var waitingForRecoil = false

    // [IMPORTANTE] Limiar de Magnitude para detectar IMPACTO (fundo da compressão)
    // Quando você empurra e bate embaixo, a magnitude sobe muito acima de 9.8G.
    // 3.0f significa que estamos procurando um pico de força de ~3m/s² acima da gravidade.
    private val IMPACT_THRESHOLD = 3.0f

    // Limiar para considerar que a mão subiu (alivio de pressão, magnitude cai ou volta ao normal)
    private val RECOIL_THRESHOLD = 0.5f

    // 300ms de intervalo mínimo = máx 200 CPM. Evita contagem dupla.
    private val MIN_COMPRESSION_INTERVAL = 300L

    fun startCapture() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
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
    }

    fun stopAndSendData() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
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

    override fun onSensorChanged(event: SensorEvent?) {
        if (_uiState.value.isCapturing && event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val timestamp = System.currentTimeMillis()

            // Salva os dados brutos para o celular fazer a análise completa depois
            capturedData.add("$timestamp,ACC,$x,$y,$z")

            // Calcula a Magnitude Total (independente da orientação do relógio)
            val currentMagnitude = sqrt(x*x + y*y + z*z)

            if (isFirstReading) {
                gravityMagnitude = currentMagnitude
                isFirstReading = false
            }

            // Analisa usando a magnitude em vez de apenas o eixo Z
            val feedback = analyzeCompressionMagnitude(currentMagnitude)
            if (feedback != null) {
                _uiState.update { it.copy(feedbackText = feedback) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ------------------------------------------------------------
    // NOVA ANÁLISE USANDO MAGNITUDE (FUSÃO DE EIXOS)
    // ------------------------------------------------------------
    private fun analyzeCompressionMagnitude(currentMag: Float): String? {
        val now = System.currentTimeMillis()

        // 1. Filtro Passa-Alta na Magnitude
        // gravityMagnitude aprende qual é a força constante (aprox 9.8)
        gravityMagnitude = alpha * gravityMagnitude + (1 - alpha) * currentMag
        // linearMagnitude é só a variação brusca (o movimento da RCP)
        val linearMagnitude = currentMag - gravityMagnitude

        if (!waitingForRecoil) {
            // ESTADO 1: Procurando IMPACTO (pico positivo de magnitude)
            // Durante a compressão, a desaceleração brusca no peito gera um pico de força > 9.8g
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
                            frequency < 100 -> "⚠️ Muito lento (${frequency.toInt()} cpm)"
                            frequency > 120 -> "⚠️ Muito rápido (${frequency.toInt()} cpm)"
                            else -> {
                                vibrateShort()
                                "✅ Ritmo OK (${frequency.toInt()} cpm)"
                            }
                        }
                    }
                }
            }
        } else {
            // ESTADO 2: Aguardando RETORNO (alivio da força)
            // Esperamos a magnitude cair de volta para perto do normal (ou abaixo dele durante a subida)
            if (linearMagnitude < RECOIL_THRESHOLD) {
                waitingForRecoil = false
            }
            // Timeout de segurança (se travar por 1.5s, reseta)
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
        repository.stopCapture()
    }
}