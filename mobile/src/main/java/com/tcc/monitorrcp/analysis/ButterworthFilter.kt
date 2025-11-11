package com.tcc.monitorrcp.analysis

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Implementa um filtro digital Butterworth de 2ª Ordem (Biquad).
 */
class ButterworthFilter(
    cutoffFrequency: Double,
    sampleRate: Double,
    isHighPass: Boolean = true
) {
    // Coeficientes do filtro
    private val a = DoubleArray(3)
    private val b = DoubleArray(3)

    // "Memória" do filtro (últimas 2 entradas e saídas)
    private val x = DoubleArray(3) // x[n], x[n-1], x[n-2]
    private val y = DoubleArray(3) // y[n], y[n-1], y[n-2]

    init {
        // Pré-deformação da frequência
        val omega = 2.0 * sampleRate * tan(PI * cutoffFrequency / sampleRate)
        val norm: Double

        // Fórmulas dos coeficientes para um Butterworth de 2ª Ordem
        val c = 4.0 * sampleRate * sampleRate + 2.0 * sqrt(2.0) * sampleRate * omega + omega * omega

        if (isHighPass) {
            // Coeficientes High-pass
            norm = 4.0 * sampleRate * sampleRate / c
            a[0] = 1.0 * norm
            a[1] = -2.0 * norm
            a[2] = 1.0 * norm

            b[0] = 1.0 // b0 é sempre 1 por convenção, mas não é usado no cálculo
            b[1] = (2.0 * omega * omega - 8.0 * sampleRate * sampleRate) / c
            b[2] = (4.0 * sampleRate * sampleRate - 2.0 * sqrt(2.0) * sampleRate * omega + omega * omega) / c
        } else {
            // Coeficientes Low-pass (caso precise no futuro)
            norm = omega * omega / c
            a[0] = 1.0 * norm
            a[1] = 2.0 * norm
            a[2] = 1.0 * norm

            b[0] = 1.0
            b[1] = (2.0 * omega * omega - 8.0 * sampleRate * sampleRate) / c
            b[2] = (4.0 * sampleRate * sampleRate - 2.0 * sqrt(2.0) * sampleRate * omega + omega * omega) / c
        }
    }

    /**
     * Aplica o filtro a uma lista completa de dados (sinal).
     */
    fun apply(data: List<Float>): List<Float> {
        // Zera a memória do filtro
        x.fill(0.0)
        y.fill(0.0)

        val output = FloatArray(data.size)

        for (n in data.indices) {
            // Atualiza a "memória" de entrada
            x[0] = data[n].toDouble()

            // A Equação de Diferença (o coração do filtro)
            // y[n] = a0*x[n] + a1*x[n-1] + a2*x[n-2] - b1*y[n-1] - b2*y[n-2]
            y[0] = (a[0] * x[0]) + (a[1] * x[1]) + (a[2] * x[2]) - (b[1] * y[1]) - (b[2] * y[2])

            output[n] = y[0].toFloat()

            // Prepara a memória para a próxima iteração
            // x[n-2] = x[n-1]
            x[2] = x[1]
            // x[n-1] = x[n]
            x[1] = x[0]

            // y[n-2] = y[n-1]
            y[2] = y[1]
            // y[n-1] = y[n]
            y[1] = y[0]
        }
        return output.toList()
    }
}