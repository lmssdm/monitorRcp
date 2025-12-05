package com.tcc.monitorrcp.data

import android.content.Context
import com.tcc.monitorrcp.database.AppDatabase
import com.tcc.monitorrcp.database.HistoryEntity
import com.tcc.monitorrcp.model.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 *Gerencia o acesso ao banco de dados e notifica a UI sobre novos resultados.
 */
object DataRepository {

    private val _lastTestResult = MutableStateFlow<TestResult?>(null)
    val lastTestResult = _lastTestResult

    private var historyDaoInitialized = false
    private lateinit var db: AppDatabase

    fun initDatabase(context: Context) {
        if (!historyDaoInitialized) {
            db = AppDatabase.getDatabase(context)
            historyDaoInitialized = true
        }
    }
    fun getHistoryFlow(): Flow<List<HistoryEntity>> {
        return db.historyDao().getAllHistory()
    }
    fun newResultReceived(result: TestResult, context: Context) {
        _lastTestResult.value = result

        CoroutineScope(Dispatchers.IO).launch {
            val entity = HistoryEntity(
                timestamp = result.timestamp,
                medianFrequency = result.medianFrequency,
                medianDepth = result.medianDepth,
                totalCompressions = result.totalCompressions,
                correctFrequencyCount = result.correctFrequencyCount,
                correctDepthCount = result.correctDepthCount,
                slowFrequencyCount = result.slowFrequencyCount,
                fastFrequencyCount = result.fastFrequencyCount,
                correctRecoilCount = result.correctRecoilCount,
                durationInMillis = result.durationInMillis,
                interruptionCount = result.interruptionCount,
                totalInterruptionTimeMs = result.totalInterruptionTimeMs,
                name = result.name
            )
            db.historyDao().insert(entity)
        }
    }
    suspend fun deleteTestByTimestamp(timestamp: Long) {
        if (historyDaoInitialized) {
            db.historyDao().deleteByTimestamp(timestamp)
        }
    }
    suspend fun updateTestName(timestamp: Long, newName: String) {
        if (historyDaoInitialized) {
            db.historyDao().updateNameByTimestamp(timestamp, newName)
        }
    }
}