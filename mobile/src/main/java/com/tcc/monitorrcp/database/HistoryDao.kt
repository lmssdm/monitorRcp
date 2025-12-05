package com.tcc.monitorrcp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
/**
 * Define os comandos SQL (INSERT, DELETE, UPDATE, SELECT) para manipular a tabela de histórico.
 */
@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)
    @Query("SELECT * FROM history_table ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history_table WHERE timestamp = :timestamp")
    suspend fun deleteByTimestamp(timestamp: Long)

    @Query("UPDATE history_table SET name = :newName WHERE timestamp = :timestamp")
    suspend fun updateNameByTimestamp(timestamp: Long, newName: String)
}