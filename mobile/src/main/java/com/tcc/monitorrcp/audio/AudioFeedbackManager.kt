package com.tcc.monitorrcp.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

// Este Enum será usado pelo ViewModel e pelo Manager
enum class FeedbackStatus { OK, FAST, SLOW, NONE }

class AudioFeedbackManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    // [ALTERAÇÃO] A variável 'lastFeedbackStatus' FOI ADICIONADA DE VOLTA.
    // É ela que impede o feedback de se repetir.
    private var lastFeedbackStatus = FeedbackStatus.NONE

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ptBrLocale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
            val result = tts?.setLanguage(ptBrLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Linguagem (pt-BR) não suportada ou dados ausentes.")
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsInitialized = true
            Log.i("TTS", "AudioFeedbackManager inicializado.")
        } else {
            Log.e("TTS", "Falha ao inicializar TextToSpeech. Status: $status")
            isTtsInitialized = false
        }
    }

    /**
     * Toca o feedback sonoro APENAS se o status mudou desde a última chamada.
     */
    fun playFeedback(newStatus: FeedbackStatus) {
        // [ALTERAÇÃO] A verificação 'if' FOI ADICIONADA DE VOLTA.
        // Se o TTS não estiver pronto OU se o status for o mesmo de antes, não faz nada.
        if (!isTtsInitialized || tts == null || newStatus == lastFeedbackStatus) {
            return
        }

        // Atualiza o status
        lastFeedbackStatus = newStatus

        val textToSpeak = when (newStatus) {
            FeedbackStatus.OK -> "Seu ritmo médio está correto"
            FeedbackStatus.FAST -> "Seu ritmo médio está rápido demais"
            FeedbackStatus.SLOW -> "Seu ritmo médio está lento demais"
            FeedbackStatus.NONE -> null // Não fala nada para "Calculando..."
        }

        textToSpeak?.let {
            tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "feedback_rcp")
            Log.i("TTS", "Falando: $it")
        }
    }

    /**
     * Força a parada da fala e reseta o último estado.
     */
    fun stopAndReset() {
        tts?.stop()
        // [ALTERAÇÃO] Reseta o 'lastFeedbackStatus'
        lastFeedbackStatus = FeedbackStatus.NONE
    }

    /**
     * Libera os recursos do TTS. Deve ser chamado no onCleared do ViewModel.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
    }
}