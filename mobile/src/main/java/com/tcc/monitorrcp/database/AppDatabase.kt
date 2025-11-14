package com.tcc.monitorrcp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class],
    // --- [MUDANÇA AQUI] Incrementar a versão para 7 ---
    version = 7, // [REFACTOR] Versão incrementada de 6 para 7
    // --- FIM DA MUDANÇA ---
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rcp_database"
                )
                    // Lembrete: Isso vai apagar os dados antigos na atualização para a v7
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}