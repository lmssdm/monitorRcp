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

/**
 * Controla a lógica do relógio.
 * Faz a contagem regressiva.
 * Gera o metrônomo tátil (vibração periódica).
 * Faz uma análise preliminar leve (algoritmo simplificado) para mostrar feedback na tela do relógio ("Muito rápido", "Ok").
 * Gerencia o buffer de dados para envio em pacotes.
 */
data class WearUiState(
    val isCapturing: Boolean = false,
    val elapsedTimeInMillis: Long = 0L,
    val feedbackText: String = "Toque para Iniciar"
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

    private val SEND_INTERVAL = 3000L
    private var capturedData = mutableListOf("Timestamp,Type,X,Y,Z")
    private var timerJob: Job? = null
    private var chunkSendJob: Job? = null

    // Metrónomo para ritmo de 110 BPM (60.000ms / 110 ≈ 545ms)
    private var metronomeJob: Job? = null
    private val METRONOME_INTERVAL_MS = 545L

    private var lastPeakTime: Long? = null
    private var gravityMagnitude = 9.8f
    private val alpha = 0.8f
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private var isFirstReading = true
    private var lastValidCompressionTime = 0L
    private var waitingForRecoil = false

    private val IMPACT_THRESHOLD = 6.0f
    private val RECOIL_THRESHOLD = 0.5f
    private val MIN_COMPRESSION_INTERVAL = 300L

    fun startCapture() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
        metronomeJob?.cancel()
        viewModelScope.launch {

            val countdown = listOf("3", "2", "1")
            for (count in countdown) {
                _uiState.update { it.copy(feedbackText = "Prepare-se: $count...") }
                vibrateShort()
                delay(1000)
            }

            try {
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) { }

            capturedData.clear()
            capturedData.add("Timestamp,Type,X,Y,Z")
            lastPeakTime = null
            lastValidCompressionTime = 0L
            isFirstReading = true
            waitingForRecoil = false
            gravityMagnitude = 9.8f

            _uiState.update {
                it.copy(
                    isCapturing = true,
                    elapsedTimeInMillis = 0L,
                    feedbackText = "INICIADO!"
                )
            }

            repository.startCapture()

            val startTime = System.currentTimeMillis()

            timerJob = launch {
                while (isActive) {
                    val currentElapsedTime = System.currentTimeMillis() - startTime
                    _uiState.update { it.copy(elapsedTimeInMillis = currentElapsedTime) }
                    delay(100)
                }
            }

            chunkSendJob = launch {
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

            startMetronome()
        }
    }

    private fun startMetronome() {
        metronomeJob = viewModelScope.launch {
            while (isActive) {
                vibrateShort()
                delay(METRONOME_INTERVAL_MS)
            }
        }
    }

    fun stopAndSendData() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
        metronomeJob?.cancel()

        repository.stopCapture()

        if (capturedData.size > 1) {
            val finalData = ArrayList(capturedData)
            repository.sendEndOfTestData(finalData)
        } else {
            repository.sendEndOfTestData(listOf("Timestamp,Type,X,Y,Z"))
        }

        _uiState.update {
            WearUiState(
                isCapturing = false,
                elapsedTimeInMillis = 0L,
                feedbackText = "Toque para Iniciar"
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (!_uiState.value.isCapturing || event == null) return

        val timestamp = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {

                capturedData.add("$timestamp,ACC,$x,$y,$z")

                val currentMagnitude = sqrt(x*x + y*y + z*z)

                if (isFirstReading) {
                    gravityMagnitude = currentMagnitude
                    isFirstReading = false
                }

                val feedback = analyzeCompressionMagnitude(currentMagnitude)
                if (feedback != null) {
                    _uiState.update { it.copy(feedbackText = feedback) }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                capturedData.add("$timestamp,GYR,$x,$y,$z")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun analyzeCompressionMagnitude(currentMag: Float): String? {
        val now = System.currentTimeMillis()

        // Filtro Passa-Alta simples
        gravityMagnitude = alpha * gravityMagnitude + (1 - alpha) * currentMag
        val linearMagnitude = currentMag - gravityMagnitude

        if (!waitingForRecoil) {
            // ESTADO 1: Descida (Compressão)
            // IMPACT_THRESHOLD = 6.0f evita detectar a própria vibração
            if (linearMagnitude > IMPACT_THRESHOLD && (now - lastValidCompressionTime > MIN_COMPRESSION_INTERVAL)) {

                // Timeout: Se passou >2s, resetamos para não dar feedback errado
                if (now - lastValidCompressionTime > 2000) {
                    lastValidCompressionTime = now
                    lastPeakTime = now
                    waitingForRecoil = true
                    return null
                }

                waitingForRecoil = true
                lastValidCompressionTime = now

                // Cálculo de CPM
                val last = lastPeakTime
                lastPeakTime = now
                if (last != null) {
                    val interval = now - last
                    if (interval > 0) {
                        val frequency = 60_000.0 / interval
                        return when {
                            frequency < 100 -> "⚠️ Muito lento (${frequency.toInt()} cpm)"
                            frequency > 120 -> "⚠️ Muito rápido (${frequency.toInt()} cpm)"
                            else -> "✅ Ritmo OK (${frequency.toInt()} cpm)"
                        }
                    }
                }
            }
        } else {
            if (linearMagnitude < RECOIL_THRESHOLD) {
                waitingForRecoil = false
            }
            if (now - lastValidCompressionTime > 1000) {
                waitingForRecoil = false
            }
        }
        return null
    }

    private fun vibrateShort() {
        try {
            // Vibração muito curta (20ms) para não interferir no acelerómetro
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        chunkSendJob?.cancel()
        metronomeJob?.cancel()
        repository.stopCapture()
    }
}