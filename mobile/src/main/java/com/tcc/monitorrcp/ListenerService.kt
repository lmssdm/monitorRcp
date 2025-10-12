package com.tcc.monitorrcp

import android.content.Intent // <-- IMPORT FALTANTE
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService // <-- CORREÇÃO DO NOME DA CLASSE

class ListenerService : WearableListenerService() { // <-- NOME CORRIGIDO AQUI

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sensor_data") {
            Log.d("RCP_DEBUG", "ListenerService RECEBEU UMA MENSAGEM!")

            val receivedData = String(messageEvent.data, Charsets.UTF_8)
            Log.d("RCP_DEBUG", "Dados recebidos: $receivedData")

            val intent = Intent("com.tcc.monitorrcp.DATA_RECEIVED")
            intent.putExtra("data", receivedData)
            sendBroadcast(intent)
        }
    }
}