package com.tcc.monitorrcp

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class ListenerService : WearableListenerService() {

    // Log para verificar se o serviço foi criado pelo sistema
    override fun onCreate() {
        super.onCreate()
        Log.d("RCP_DEBUG", "ListenerService FOI CRIADO PELO SISTEMA!")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sensor_data") {
            // Log para verificar se a mensagem foi recebida
            Log.d("RCP_DEBUG", "ListenerService RECEBEU UMA MENSAGEM!")

            val receivedData = String(messageEvent.data, Charsets.UTF_8)
            Log.d("RCP_DEBUG", "Dados recebidos: $receivedData")

            val intent = Intent("com.tcc.monitorrcp.DATA_RECEIVED")
            intent.putExtra("data", receivedData)
            sendBroadcast(intent)
        }
    }
}