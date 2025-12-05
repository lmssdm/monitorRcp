package com.tcc.monitorrcp.model

import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Contém as Data Classes que representam os objetos do domínio
 */
enum class Screen {
    SplashScreen,
    LoginScreen,
    InstructionsScreen,
    HomeScreen,
    DataScreen,
    HistoryScreen,
    HistoryDetailScreen
}

enum class TestQuality {
    TODOS,
    BOM,   // Bom (Frequência >= 80% e sem pausas longas)
    REGULAR
}

enum class DateFilter {
    TODAS,
    ULTIMOS_7_DIAS,
    ESTE_MES
}
data class SensorDataPoint(
    val timestamp: Long,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float
) {
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)
}

data class TestResult(
    val timestamp: Long,
    val medianFrequency: Double,
    val medianDepth: Double,
    val totalCompressions: Int,

    val correctFrequencyCount: Int,
    val slowFrequencyCount: Int,
    val fastFrequencyCount: Int,

    val correctDepthCount: Int,

    val correctRecoilCount: Int,

    val durationInMillis: Long,

    val interruptionCount: Int,
    val totalInterruptionTimeMs: Long,

    val name: String
) {
    val wrongFrequencyCount: Int
        get() = slowFrequencyCount + fastFrequencyCount

    val correctFrequencyPercentage: Double
        get() = if (totalCompressions > 0) {
            (correctFrequencyCount.toDouble() / totalCompressions.toDouble()) * 100.0
        } else {
            0.0
        }

    val correctDepthPercentage: Double
        get() = if (totalCompressions > 0) {
            (correctDepthCount.toDouble() / totalCompressions.toDouble()) * 100.0
        } else {
            0.0
        }
    val correctRecoilPercentage: Double
        get() = if (totalCompressions > 0) {
            (correctRecoilCount.toDouble() / totalCompressions.toDouble()) * 100.0
        } else {
            0.0
        }
    val formattedDuration: String
        get() {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationInMillis) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val quality: TestQuality
        get() {
            if (totalCompressions == 0) return TestQuality.REGULAR

            // 1. Frequência: Deve ser >= 80% correta para ser BOM
            val isFreqGood = correctFrequencyPercentage >= 80.0
            val hasLongInterruptions = totalInterruptionTimeMs > 10000
            return if (isFreqGood && !hasLongInterruptions) {
                TestQuality.BOM
            } else {
                TestQuality.REGULAR
            }
        }
}