package com.tcc.monitorrcp.ui.viewmodel

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.monitorrcp.data.SensorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// A classe de estado para a UI do relógio
data class WearUiState(
    val isCapturing: Boolean = false,
    val elapsedTimeInMillis: Long = 0L
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

    // O ViewModel agora é o SensorEventListener
    private val repository = SensorRepository(application, viewModelScope, this)

    private var capturedData = mutableListOf<String>()
    private var timerJob: Job? = null

    fun startCapture() {
        timerJob?.cancel() // Cancela qualquer timer anterior
        capturedData.clear()
        capturedData.add("Timestamp,Type,X,Y,Z") // Header CSV

        _uiState.update { it.copy(isCapturing = true) }
        repository.startCapture()

        val startTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
            while (true) {
                val currentElapsedTime = System.currentTimeMillis() - startTime
                _uiState.update { it.copy(elapsedTimeInMillis = currentElapsedTime) }
                delay(100)
            }
        }
    }

    fun stopAndSendData() {
        timerJob?.cancel()
        repository.stopCapture()
        repository.sendSensorData(capturedData)
        _uiState.value = WearUiState() // Reseta o estado para o inicial
    }

    // --- Métodos do SensorEventListener ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (_uiState.value.isCapturing && event != null) {
            val timestamp = System.currentTimeMillis()
            val type = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) "ACC" else "GYR"
            val values = event.values
            capturedData.add("$timestamp,$type,${values[0]},${values[1]},${values[2]}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não precisamos implementar isso para este caso
    }
}