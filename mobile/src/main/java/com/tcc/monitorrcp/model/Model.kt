package com.tcc.monitorrcp.model
import kotlin.math.sqrt

enum class Screen {
    SplashScreen,
    LoginScreen,
    HomeScreen,
    DataScreen,
    HistoryScreen,
    InstructionsScreen
}

data class SensorDataPoint(val timestamp: Long, val type: String, val x: Float, val y: Float, val z: Float) {
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)
}

data class TestResult(
    val timestamp: Long,
    val medianFrequency: Double,
    val averageDepth: Double,
    val totalCompressions: Int,
    val correctFrequencyCount: Int,
    val correctDepthCount: Int
)