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
}