package com.tcc.monitorrcp.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// O 'listener' é um parâmetro para que o repositório possa enviar os dados do sensor
// de volta para quem o está usando (nosso ViewModel).
class SensorRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val listener: SensorEventListener
) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    fun startCapture() {
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_UI)
        Toast.makeText(context, "Captura iniciada!", Toast.LENGTH_SHORT).show()
    }

    fun stopCapture() {
        sensorManager.unregisterListener(listener)
        Toast.makeText(context, "Captura parada. A enviar...", Toast.LENGTH_SHORT).show()
    }

    fun sendSensorData(data: List<String>) {
        if (data.size <= 1) { // A lista deve ter mais que apenas o cabeçalho
            Toast.makeText(context, "Nenhum dado capturado.", Toast.LENGTH_LONG).show()
            return
        }

        coroutineScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Toast.makeText(context, "Falha: Telemóvel não encontrado.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val nodeId = nodes.first().id
                val dataToSend = data.joinToString("\n").toByteArray(Charsets.UTF_8)

                messageClient.sendMessage(nodeId, "/sensor_data", dataToSend).apply {
                    addOnSuccessListener { Toast.makeText(context, "Dados enviados!", Toast.LENGTH_LONG).show() }
                    addOnFailureListener { Toast.makeText(context, "Falha ao enviar.", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}