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

// Enum para os filtros de qualidade
enum class TestQuality {
    TODOS, // Todos os testes
    BOM,   // Bom (>= 80% em tudo)
    REGULAR // Regular (Pelo menos um < 80%)
}

// Enum para os filtros de data
enum class DateFilter {
    TODAS,
    ULTIMOS_7_DIAS,
    ESTE_MES
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
    val medianDepth: Double, // [REFACTOR] Renomeado de averageDepth
    val totalCompressions: Int,

    val correctFrequencyCount: Int,
    val slowFrequencyCount: Int,
    val fastFrequencyCount: Int,

    val correctDepthCount: Int,

    val correctRecoilCount: Int,

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

    // Porcentagem de profundidade correta
    val correctDepthPercentage: Double
        get() = if (totalCompressions > 0) {
            (correctDepthCount.toDouble() / totalCompressions.toDouble()) * 100.0
        } else {
            0.0
        }

    // Propriedade computada para a porcentagem de recoil correto
    val correctRecoilPercentage: Double
        get() = if (totalCompressions > 0) {
            (correctRecoilCount.toDouble() / totalCompressions.toDouble()) * 100.0
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

    // Propriedade que classifica o teste
    val quality: TestQuality
        get() {
            if (totalCompressions == 0) return TestQuality.REGULAR
            // Define "Bom" como >= 80% de acerto em todas as métricas principais
            val isFreqGood = correctFrequencyPercentage >= 80.0
            val isDepthGood = correctDepthPercentage >= 80.0
            val isRecoilGood = correctRecoilPercentage >= 80.0

            return if (isFreqGood && isDepthGood && isRecoilGood) {
                TestQuality.BOM
            } else {
                TestQuality.REGULAR
            }
        }
}