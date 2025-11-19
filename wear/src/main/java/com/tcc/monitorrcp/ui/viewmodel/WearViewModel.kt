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

    // Limites ajustados: 6.0f ignora a vibração do relógio
    private val IMPACT_THRESHOLD = 6.0f
    private val RECOIL_THRESHOLD = 0.5f
    private val MIN_COMPRESSION_INTERVAL = 300L

    fun startCapture() {
        // Cancela jobs anteriores para evitar sobreposição
        timerJob?.cancel()
        chunkSendJob?.cancel()
        metronomeJob?.cancel()

        // Lança uma corrotina para gerir a contagem regressiva antes de começar
        viewModelScope.launch {

            // --- FASE 1: CONTAGEM REGRESSIVA ---
            val countdown = listOf("3", "2", "1")
            for (count in countdown) {
                _uiState.update { it.copy(feedbackText = "Prepare-se: $count...") }
                vibrateShort() // Um "bip" tátil curto
                delay(1000)    // Espera 1 segundo
            }

            // --- FASE 2: INÍCIO REAL ---

            // Vibração longa para indicar "VALENDO!"
            try {
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) { }

            // Reseta variáveis APÓS a contagem (para garantir limpeza)
            capturedData.clear()
            capturedData.add("Timestamp,Type,X,Y,Z")
            lastPeakTime = null
            lastValidCompressionTime = 0L
            isFirstReading = true
            waitingForRecoil = false
            gravityMagnitude = 9.8f

            // Atualiza UI para modo captura
            _uiState.update {
                it.copy(
                    isCapturing = true,
                    elapsedTimeInMillis = 0L,
                    feedbackText = "INICIADO!"
                )
            }

            // Liga os sensores físicos
            repository.startCapture()

            // Inicia os Jobs (Timer, Envio de Chunks, Metrónomo)
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
                    // Só envia se tivermos dados novos (cabeçalho + pelo menos 1 ponto)
                    if (_uiState.value.isCapturing && capturedData.size > 1) {
                        val dataToSend = ArrayList(capturedData)
                        capturedData.clear()
                        capturedData.add("Timestamp,Type,X,Y,Z")
                        Log.d("WearViewModel", "Enviando chunk com ${dataToSend.size - 1} pontos.")
                        repository.sendSensorDataChunk(dataToSend)
                    }
                }
            }

            // Inicia o metrónomo (vibração de guia)
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

        // Envia o último pacote de dados
        if (capturedData.size > 1) {
            val finalData = ArrayList(capturedData)
            repository.sendEndOfTestData(finalData)
        } else {
            repository.sendEndOfTestData(listOf("Timestamp,Type,X,Y,Z"))
        }

        // Reseta UI
        _uiState.update {
            WearUiState(
                isCapturing = false,
                elapsedTimeInMillis = 0L,
                feedbackText = "Toque para Iniciar"
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Se a contagem regressiva ainda estiver a decorrer (isCapturing = false), ignoramos os sensores
        if (!_uiState.value.isCapturing || event == null) return

        val timestamp = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Guarda dados para envio ao telemóvel
                capturedData.add("$timestamp,ACC,$x,$y,$z")

                // Análise Local para Feedback no Relógio
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

    // ------------------------------------------------------------
    // Lógica de Feedback Local
    // ------------------------------------------------------------
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
            // ESTADO 2: Subida (Recoil)
            if (linearMagnitude < RECOIL_THRESHOLD) {
                waitingForRecoil = false
            }
            // Timeout de segurança
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