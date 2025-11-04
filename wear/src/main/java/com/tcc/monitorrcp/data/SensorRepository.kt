package com.tcc.monitorrcp.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log // Import Log
import android.widget.Toast
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        // Trocamos a frequência de captura para a mais rápida possível.
        val samplingPeriodUs = SensorManager.SENSOR_DELAY_FASTEST
        // Regista o listener passado pelo ViewModel
        sensorManager.registerListener(listener, accelerometer, samplingPeriodUs)
        sensorManager.registerListener(listener, gyroscope, samplingPeriodUs)
        Toast.makeText(context, "Captura iniciada (alta frequência)!", Toast.LENGTH_SHORT).show()
    }

    fun stopCapture() {
        sensorManager.unregisterListener(listener)
        Toast.makeText(context, "Captura parada.", Toast.LENGTH_SHORT).show() // Mensagem mais simples
    }

    // Função interna para enviar dados com um path específico
    private fun sendDataInternal(data: List<String>, path: String) {
        if (data.size <= 1 && path == "/sensor_data_final") { // Permite enviar msg final vazia, mas não chunks vazios
            Log.w("SensorRepository", "Tentando enviar dados vazios para $path. Permitido apenas para final.")
            if (path != "/sensor_data_final") return
        }
        if (data.isEmpty() && path == "/sensor_data_final"){
            Log.w("SensorRepository", "Enviando mensagem final sem dados.")
            // É importante enviar algo para o mobile saber que acabou
        }

        coroutineScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.e("SensorRepository", "Falha: Telemóvel não encontrado para enviar $path.")
                    Toast.makeText(context, "Falha: Telemóvel não encontrado.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val nodeId = nodes.first().id
                val dataToSend = data.joinToString("\n").toByteArray(Charsets.UTF_8)

                Log.d("SensorRepository", "Enviando ${dataToSend.size} bytes para $path")
                messageClient.sendMessage(nodeId, path, dataToSend).apply {
                    addOnSuccessListener {
                        Log.i("SensorRepository", "Dados ($path) enviados com sucesso!")
                        // Removido Toast daqui para não poluir
                    }
                    addOnFailureListener {
                        Log.e("SensorRepository", "Falha ao enviar dados ($path).", it)
                        Toast.makeText(context, "Falha ao enviar.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SensorRepository", "Erro ao enviar dados ($path): ${e.message}", e)
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Função para enviar chunks intermédios
    fun sendSensorDataChunk(chunk: List<String>) {
        if (chunk.size > 1) { // Só envia se tiver dados além do header
            sendDataInternal(chunk, "/sensor_data_chunk")
        } else {
            Log.d("SensorRepository", "Chunk vazio ou só com header, não enviado.")
        }
    }

    // Função para enviar o último chunk e sinalizar o fim
    fun sendEndOfTestData(finalChunk: List<String>) {
        sendDataInternal(finalChunk, "/sensor_data_final")
        Toast.makeText(context, "Dados finais enviados!", Toast.LENGTH_LONG).show()
    }
}