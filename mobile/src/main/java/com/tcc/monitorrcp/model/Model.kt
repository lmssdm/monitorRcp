package com.tcc.monitorrcp.model

import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

// Enum para controlar as telas do aplicativo
enum class Screen {
    SplashScreen,
    LoginScreen,
    InstructionsScreen,
    HomeScreen,
    DataScreen,
    HistoryScreen,
    HistoryDetailScreen
}

// Classe que representa um ponto único de dado do sensor
data class SensorDataPoint(
    val timestamp: Long,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float
) {
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)
}

// Classe para representar o resultado final de um teste de RCP
data class TestResult(
    val timestamp: Long,
    val medianFrequency: Double,
    val averageDepth: Double,
    val totalCompressions: Int,

    val correctFrequencyCount: Int,
    val slowFrequencyCount: Int,
    val fastFrequencyCount: Int,

    val correctDepthCount: Int,

    // [NECESSÁRIO] Nova métrica de Duração
    val durationInMillis: Long
) {
    // Propriedade computada para a contagem de frequência errada
    val wrongFrequencyCount: Int
        get() = slowFrequencyCount + fastFrequencyCount

    // Propriedade computada para a porcentagem de frequência correta
    val correctFrequencyPercentage: Double
        get() = if (totalCompressions > 0) {
            (correctFrequencyCount.toDouble() / totalCompressions.toDouble()) * 100.0
        } else {
            0.0
        }

    // Propriedade computada para formatar a duração (ex: "01:30")
    val formattedDuration: String
        get() {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationInMillis) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}