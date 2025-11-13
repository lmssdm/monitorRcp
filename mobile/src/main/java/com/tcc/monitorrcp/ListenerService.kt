package com.tcc.monitorrcp

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class ListenerService : WearableListenerService() {

    // [BOAS PRÁTICAS] Adiciona uma TAG constante para facilitar a depuração
    companion object {
        private const val TAG = "ListenerService"
        const val ACTION_DATA_CHUNK_RECEIVED = "com.tcc.monitorrcp.DATA_CHUNK_RECEIVED"
        const val ACTION_FINAL_DATA_RECEIVED = "com.tcc.monitorrcp.FINAL_DATA_RECEIVED"
        const val EXTRA_DATA = "data"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // [BOAS PRÁTICAS] Log padronizado
        Log.d(TAG, "Mensagem recebida com path: ${messageEvent.path}")

        try {
            when (messageEvent.path) {
                "/sensor_data",                // novo path vindo do relógio
                "/sensor_data_chunk" -> {
                    // [BOAS PRÁTICAS] Usa StandardCharsets e verifica se os dados não estão vazios
                    val receivedData = String(messageEvent.data, StandardCharsets.UTF_8)
                    if (receivedData.isNotBlank()) {
                        Log.d(TAG, "Dados de CHUNK recebidos (size=${receivedData.length})")
                        sendDataBroadcast(ACTION_DATA_CHUNK_RECEIVED, receivedData)
                    } else {
                        Log.w(TAG, "Recebido chunk de dados vazio.")
                    }
                }
                "/sensor_data_final" -> {
                    val receivedData = String(messageEvent.data, StandardCharsets.UTF_8)
                    if (receivedData.isNotBlank()) {
                        Log.d(TAG, "Dados FINAIS recebidos: ${receivedData.lines().size - 1} pontos")
                        sendDataBroadcast(ACTION_FINAL_DATA_RECEIVED, receivedData)
                    } else {
                        Log.w(TAG, "Recebidos dados FINAIS vazios.")
                    }
                }
                else -> {
                    Log.w(TAG, "Mensagem recebida com path não esperado: ${messageEvent.path}")
                }
            }
        } catch (e: Exception) {
            // [BOAS PRÁTICAS] Captura qualquer erro (ex: falha na descodificação do Charset)
            // para impedir que o serviço falhe.
            Log.e(TAG, "Falha ao processar mensagem recebida", e)
        }
    }

    private fun sendDataBroadcast(action: String, data: String) {
        val intent = Intent(action)
        intent.putExtra(EXTRA_DATA, data)
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast enviado com action: $action")
    }
}