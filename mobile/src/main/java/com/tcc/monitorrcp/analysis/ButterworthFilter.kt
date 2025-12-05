package com.tcc.monitorrcp.analysis

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Implementação matemática do filtro digital (Passa-Alta e Passa-Baixa)
 * usado para limpar o sinal do acelerômetro antes da análise.
 */
class ButterworthFilter(
    cutoffFrequency: Double,
    sampleRate: Double,
    isHighPass: Boolean = true
) {
    private val a = DoubleArray(3)
    private val b = DoubleArray(3)

    private val x = DoubleArray(3)
    private val y = DoubleArray(3)

    init {
        val omega = 2.0 * sampleRate * tan(PI * cutoffFrequency / sampleRate)
        val norm: Double

        val c = 4.0 * sampleRate * sampleRate + 2.0 * sqrt(2.0) * sampleRate * omega + omega * omega

        if (isHighPass) {
            norm = 4.0 * sampleRate * sampleRate / c
            a[0] = 1.0 * norm
            a[1] = -2.0 * norm
            a[2] = 1.0 * norm

            b[0] = 1.0
            b[1] = (2.0 * omega * omega - 8.0 * sampleRate * sampleRate) / c
            b[2] = (4.0 * sampleRate * sampleRate - 2.0 * sqrt(2.0) * sampleRate * omega + omega * omega) / c
        } else {
            norm = omega * omega / c
            a[0] = 1.0 * norm
            a[1] = 2.0 * norm
            a[2] = 1.0 * norm

            b[0] = 1.0
            b[1] = (2.0 * omega * omega - 8.0 * sampleRate * sampleRate) / c
            b[2] = (4.0 * sampleRate * sampleRate - 2.0 * sqrt(2.0) * sampleRate * omega + omega * omega) / c
        }
    }
    fun apply(data: List<Float>): List<Float> {
        x.fill(0.0)
        y.fill(0.0)

        val output = FloatArray(data.size)

        for (n in data.indices) {
            x[0] = data[n].toDouble()
            y[0] = (a[0] * x[0]) + (a[1] * x[1]) + (a[2] * x[2]) - (b[1] * y[1]) - (b[2] * y[2])

            output[n] = y[0].toFloat()

            x[2] = x[1]
            x[1] = x[0]
            y[2] = y[1]
            y[1] = y[0]
        }
        return output.toList()
    }
}