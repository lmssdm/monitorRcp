package com.tcc.monitorrcp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// @Dao diz ao Room que esta interface define as operações do banco de dados.
@Dao
interface HistoryDao {

    // Insere um novo resultado de teste. Se já existir, substitui.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)

    // Pega todos os resultados da tabela, ordenados pelo mais recente.
    // Retorna um Flow, que emite a lista automaticamente sempre que os dados mudam.
    @Query("SELECT * FROM history_table ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    // Deleta um item pelo seu timestamp
    @Query("DELETE FROM history_table WHERE timestamp = :timestamp")
    suspend fun deleteByTimestamp(timestamp: Long)

    // --- [MUDANÇA AQUI] Adiciona uma query para atualizar o nome ---
    @Query("UPDATE history_table SET name = :newName WHERE timestamp = :timestamp")
    suspend fun updateNameByTimestamp(timestamp: Long, newName: String)
    // --- FIM DA MUDANÇA ---
}