package com.tcc.monitorrcp.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val timestamp: Long,
    val medianFrequency: Double,
    val medianDepth: Double, // [REFACTOR] Renomeado de averageDepth
    val totalCompressions: Int,
    val correctFrequencyCount: Int,
    val correctDepthCount: Int,

    @ColumnInfo(defaultValue = "0")
    val slowFrequencyCount: Int = 0,

    @ColumnInfo(defaultValue = "0")
    val fastFrequencyCount: Int = 0,

    @ColumnInfo(defaultValue = "0")
    val correctRecoilCount: Int = 0,

    @ColumnInfo(defaultValue = "0")
    val durationInMillis: Long = 0L
)