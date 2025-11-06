package com.tcc.monitorrcp.model

import kotlin.math.sqrt

// Enum para controlar as telas do aplicativo
enum class Screen {
    SplashScreen,
    LoginScreen,
    InstructionsScreen, // <-- ELA ESTÁ DE VOLTA AQUI
    HomeScreen,
    DataScreen,
    HistoryScreen
}

// Classe que representa um ponto único de dado do sensor
data class SensorDataPoint(
    val timestamp: Long,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float
) {
    // Função vital para a fusão de sensores (Magnitude Total)
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)
}

// Classe para representar o resultado final de um teste de RCP
data class TestResult(
    val timestamp: Long,
    val medianFrequency: Double,
    val averageDepth: Double,
    val totalCompressions: Int,
    val correctFrequencyCount: Int,
    val correctDepthCount: Int
)