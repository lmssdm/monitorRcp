package com.tcc.monitorrcp.data

import android.content.Context
import com.tcc.monitorrcp.model.TestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Usamos 'object' para criar um Singleton. Isso garante que teremos apenas UMA
// instância do nosso "gerente de estoque" para todo o app.
object DataRepository {

    // Um StateFlow é um "observável" que guarda o último valor.
    // A UI vai "escutar" essa variável para saber quando um novo resultado chegou.
    private val _lastTestResult = MutableStateFlow<TestResult?>(null)
    val lastTestResult = _lastTestResult.asStateFlow() // Versão pública, apenas para leitura

    // StateFlow para a lista de histórico
    private val _history = MutableStateFlow<List<TestResult>>(emptyList())
    val history = _history.asStateFlow()

    // Função que o ListenerService vai chamar quando receber novos dados
    fun newResultReceived(result: TestResult) {
        _lastTestResult.value = result
    }

    // Carrega o histórico do SharedPreferences
    fun loadHistory(context: Context) {
        val prefs = context.getSharedPreferences("RCP_HISTORY", Context.MODE_PRIVATE)
        val historyString = prefs.getString("results", "") ?: ""

        val historyList = historyString.split(';')
            .filter { it.isNotBlank() }
            .mapNotNull {
                try {
                    val parts = it.split(',')
                    TestResult(
                        timestamp = parts[0].toLong(),
                        medianFrequency = parts[1].toDouble(),
                        averageDepth = parts[2].toDouble(),
                        totalCompressions = parts[3].toInt(),
                        correctFrequencyCount = parts[4].toInt(),
                        correctDepthCount = parts[5].toInt()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.timestamp }

        _history.value = historyList
    }

    // Salva um novo resultado no histórico e atualiza a lista
    fun saveNewResultToHistory(context: Context, newResult: TestResult) {
        val prefs = context.getSharedPreferences("RCP_HISTORY", Context.MODE_PRIVATE)
        val historyString = prefs.getString("results", "") ?: ""
        val newResultString = "${newResult.timestamp},${newResult.medianFrequency},${newResult.averageDepth},${newResult.totalCompressions},${newResult.correctFrequencyCount},${newResult.correctDepthCount}"

        val updatedHistory = if (historyString.isEmpty()) newResultString else "$historyString;$newResultString"
        prefs.edit().putString("results", updatedHistory).apply()

        // Após salvar, recarregamos o histórico para que a UI seja notificada
        loadHistory(context)
    }
}