package com.tcc.monitorrcp

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class ListenerService : WearableListenerService() {

    companion object {
        const val ACTION_DATA_CHUNK_RECEIVED = "com.tcc.monitorrcp.DATA_CHUNK_RECEIVED"
        const val ACTION_FINAL_DATA_RECEIVED = "com.tcc.monitorrcp.FINAL_DATA_RECEIVED"
        const val EXTRA_DATA = "data"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // [ADICIONE ESTA LINHA]
        Log.d("RCP_DEBUG", "ListenerService RECEBEU MENSAGEM com path: ${messageEvent.path}")

        when (messageEvent.path) {
            "/sensor_data",                // novo path vindo do relógio
            "/sensor_data_chunk" -> {
                val receivedData = String(messageEvent.data, Charsets.UTF_8)
                Log.d("RCP_DEBUG", "Dados recebidos (chunk ou simples): $receivedData")
                sendDataBroadcast(ACTION_DATA_CHUNK_RECEIVED, receivedData)
            }
            "/sensor_data_final" -> {
                val receivedData = String(messageEvent.data, Charsets.UTF_8)
                Log.d("RCP_DEBUG", "Dados FINAIS recebidos: ${receivedData.lines().size - 1} pontos")
                sendDataBroadcast(ACTION_FINAL_DATA_RECEIVED, receivedData)
            }
            else -> {
                Log.w("RCP_DEBUG", "Mensagem recebida com path não esperado: ${messageEvent.path}")
            }
        }
    }

    private fun sendDataBroadcast(action: String, data: String) {
        val intent = Intent(action)
        intent.putExtra(EXTRA_DATA, data)
        sendBroadcast(intent)
        Log.d("RCP_DEBUG", "Broadcast enviado com action: $action")
    }
}
