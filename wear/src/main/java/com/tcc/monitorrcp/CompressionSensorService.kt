package com.tcc.monitorrcp

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.pow

class CompressionSensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lastCompressionTime = 0L
    private var compressionCount = 0
    private val scope = CoroutineScope(Dispatchers.Default)

    // Janela de amostragem e sensibilidade
    private val recentSamples = ArrayDeque<FloatArray>()
    private val sampleWindow = 30
    private val threshold = 0.8f           // sensibilidade mínima para detectar compressão
    private val minInterval = 400L         // intervalo mínimo entre compressões (ms)
    private val profundidadeFator = 4.0f   // fator empírico para profundidade (cm simulados)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        Log.d("RCP_WATCH", "Serviço de compressões iniciado (modo automático de eixo).")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values.clone() // [x, y, z]
        recentSamples.add(values)
        if (recentSamples.size > sampleWindow) recentSamples.removeFirst()

        // Calcula variância de cada eixo para descobrir o dominante
        val variances = FloatArray(3) { i ->
            val axisValues = recentSamples.map { it[i] }
            val mean = axisValues.average().toFloat()
            val variance = axisValues.sumOf { (it - mean).toDouble().pow(2.0) } / axisValues.size
            variance.toFloat()
        }

        val dominantAxis = variances.indices.maxByOrNull { variances[it] } ?: 2
        val axisLabel = when (dominantAxis) {
            0 -> "X"
            1 -> "Y"
            else -> "Z"
        }

        val accelValue = values[dominantAxis]
        val max = recentSamples.maxOfOrNull { it[dominantAxis] } ?: accelValue
        val min = recentSamples.minOfOrNull { it[dominantAxis] } ?: accelValue
        val delta = abs(max - min)

        val now = System.currentTimeMillis()
        val interval = now - lastCompressionTime

        // Detecta compressão válida e descarta ruídos
        if (accelValue < -threshold && interval > minInterval) {
            // Atualiza o tempo antes de calcular o CPM
            lastCompressionTime = now
            compressionCount++

            // Cálculo seguro do CPM (descarta valores fora da faixa real)
            val cpm = if (interval in 300..1200) {
                (60000.0 / interval.toDouble()).toInt() // 50–200 CPM
            } else {
                0
            }

            val profundidadeCm = (delta * profundidadeFator).coerceIn(0f, 6f)

            val data = """
                {
                    "compressions": $compressionCount,
                    "cpm": $cpm,
                    "profundidade": ${"%.2f".format(profundidadeCm)},
                    "eixo": "$axisLabel"
                }
            """.trimIndent()

            sendToPhone(data)
            Log.d("RCP_WATCH", "Eixo $axisLabel | Compressão $compressionCount | $cpm CPM | ${profundidadeCm}cm")
        }
    }

    private fun sendToPhone(data: String) {
        scope.launch {
            try {
                val client = Wearable.getMessageClient(applicationContext)
                client.sendMessage("phone", "/sensor_data", data.toByteArray(StandardCharsets.UTF_8))
                    .addOnSuccessListener {
                        Log.d("RCP_WATCH", "Dados enviados ao celular.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("RCP_WATCH", "Falha ao enviar dados: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("RCP_WATCH", "Erro inesperado: ${e.message}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }
}