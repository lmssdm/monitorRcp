package com.tcc.monitorrcp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// A anotação @Entity diz ao Room que esta classe é uma tabela no banco de dados.
@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // Chave primária que se auto-incrementa

    val timestamp: Long,
    val medianFrequency: Double,
    val averageDepth: Double,
    val totalCompressions: Int,
    val correctFrequencyCount: Int,
    val correctDepthCount: Int
)