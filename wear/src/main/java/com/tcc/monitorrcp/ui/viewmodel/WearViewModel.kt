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
import kotlin.math.abs

// ------------------------------------------------------------
// Estado da UI do relógio
// ------------------------------------------------------------
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

// ------------------------------------------------------------
// ViewModel do relógio (Wear OS)
// ------------------------------------------------------------
class WearViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState = _uiState.asStateFlow()

    private val repository = SensorRepository(application, viewModelScope, this)

    // Intervalo de envio em milissegundos (10 segundos)
    private val SEND_INTERVAL = 10000L

    // Lista para acumular dados
    private var capturedData = mutableListOf("Timestamp,Type,X,Y,Z")
    private var timerJob: Job? = null
    private var chunkSendJob: Job? = null

    // ------------------------------------------------------------
    // Variáveis para feedback em tempo real
    // ------------------------------------------------------------
    private var lastPeakTime: Long? = null
    private var gravityZ = 0f
    private val alpha = 0.8f // fator de suavização do filtro
    private val vibrator = application.getSystemService(Vibrator::class.java)

    // ------------------------------------------------------------
    // Início da captura
    // ------------------------------------------------------------
    fun startCapture() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
        capturedData.clear()
        capturedData.add("Timestamp,Type,X,Y,Z")

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

        // Envio periódico em chunks
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

    // ------------------------------------------------------------
    // Parar e enviar o último bloco
    // ------------------------------------------------------------
    fun stopAndSendData() {
        timerJob?.cancel()
        chunkSendJob?.cancel()
        repository.stopCapture()

        if (capturedData.size > 1) {
            val finalData = ArrayList(capturedData)
            Log.d("WearViewModel", "Enviando dados finais com ${finalData.size - 1} pontos.")
            repository.sendEndOfTestData(finalData)
        } else {
            Log.d("WearViewModel", "Enviando mensagem final vazia.")
            repository.sendEndOfTestData(listOf("Timestamp,Type,X,Y,Z"))
        }

        _uiState.value = WearUiState()
        resetFeedback()
    }

    // ------------------------------------------------------------
    // Processa eventos de sensor em tempo real
    // ------------------------------------------------------------
    override fun onSensorChanged(event: SensorEvent?) {
        if (_uiState.value.isCapturing && event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val timestamp = System.currentTimeMillis()
            val values = event.values
            capturedData.add("$timestamp,ACC,${values[0]},${values[1]},${values[2]}")

            // Avalia compressão atual
            val feedback = analyzeCompression(values[2])
            if (feedback != null) {
                _uiState.update { it.copy(feedbackText = feedback) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não necessário para este caso
    }

    // ------------------------------------------------------------
    // Análise simples em tempo real das compressões
    // ------------------------------------------------------------
    private fun analyzeCompression(z: Float): String? {
        // Filtro passa-alta simples
        gravityZ = alpha * gravityZ + (1 - alpha) * z
        val linearZ = z - gravityZ

        // Detecta movimento de compressão (aceleração descendente)
        if (linearZ < -2.0f) {
            val now = System.currentTimeMillis()
            val last = lastPeakTime
            lastPeakTime = now

            if (last != null) {
                val interval = now - last
                val frequency = 60_000.0 / interval // compressões por minuto
                val depthEstimate = abs(linearZ) * 0.5 // estimativa simples de profundidade (cm)

                return when {
                    frequency < 100 -> "⚠️ Muito lento (${frequency.toInt()} cpm)"
                    frequency > 120 -> "⚠️ Muito rápido (${frequency.toInt()} cpm)"
                    depthEstimate < 5 -> "⚠️ Muito raso (~${"%.1f".format(depthEstimate)} cm)"
                    depthEstimate > 6 -> "⚠️ Muito fundo (~${"%.1f".format(depthEstimate)} cm)"
                    else -> {
                        vibrateShort()
                        "✅ Compressão correta!"
                    }
                }
            }
        }
        return null
    }

    private fun vibrateShort() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e("WearViewModel", "Erro ao vibrar: ${e.message}")
        }
    }

    private fun resetFeedback() {
        _uiState.update { it.copy(feedbackText = "Aguardando compressões...") }
    }

    // ------------------------------------------------------------
    // Finalização
    // ------------------------------------------------------------
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        chunkSendJob?.cancel()
        repository.stopCapture()
        resetFeedback()
    }
}
