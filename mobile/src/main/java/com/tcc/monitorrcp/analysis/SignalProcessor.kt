package com.tcc.monitorrcp.analysis

import android.util.Log
import com.tcc.monitorrcp.model.SensorDataPoint
import com.tcc.monitorrcp.model.TestResult
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object SignalProcessor {

    private const val TAG = "SignalProcessor"
    private const val FINAL_ADAPTIVE_SENSITIVITY_FACTOR = 7.5

    /**
     * O "cérebro" matemático.	Algoritmos para filtrar ruído,
     *  remover gravidade, calcular picos, frequência e profundidade.
     */
    fun analyzeChunk(accData: List<SensorDataPoint>): Pair<Double, Double> {
        if (accData.isEmpty()) {
            Log.w(TAG, "[CHUNK] accData está vazio, impossível analisar.")
            return 0.0 to 0.0
        }

        val duration = (accData.last().timestamp - accData.first().timestamp) / 1000.0
        if (duration <= 0) return 0.0 to 0.0
        val fs = accData.size / duration

        val mags = accData.map { it.magnitude() }

        val butterworthHigh = ButterworthFilter(cutoffFrequency = 0.5, sampleRate = fs, isHighPass = true)
        val highPassed = butterworthHigh.apply(removeMeanDrift(mags))

        val butterworthLow = ButterworthFilter(cutoffFrequency = 8.0, sampleRate = fs, isHighPass = false)
        val filtered = butterworthLow.apply(highPassed)

        val filteredFloatList = filtered.map { it.toFloat() }

        val adaptiveThreshold = calculateAdaptiveThreshold(filteredFloatList) * 3.0

        val peaks = findPeaksWithProminence(filtered, adaptiveThreshold.toFloat(), (fs * 0.40).toInt())

        val freqs = calculateIndividualFrequencies(accData, peaks)

        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0
        val avgDepth = 0.0

        return medFreq to avgDepth
    }

    fun analyzeFinalData(allData: List<SensorDataPoint>, timestamp: Long): TestResult {

        val accData = allData.filter { it.type == "ACC" }
        val gyrData = allData.filter { it.type == "GYR" }

        if (accData.isEmpty() || gyrData.isEmpty()) {
            Log.e(TAG, "[FINAL] Dados de Acelerómetro (size=${accData.size}) ou Giroscópio (size=${gyrData.size}) estão vazios. Análise impossível.")
            return TestResult(timestamp, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0L, 0, 0L, name = "")
        }
        if (accData.size < 20 || gyrData.size < 20) {
            Log.w(TAG, "[FINAL] Dados insuficientes de ACC (${accData.size}) ou GYR (${gyrData.size}) para análise de profundidade.")
            return TestResult(timestamp, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0L, 0, 0L, name = "")
        }

        val (commonTimestamps, interpolatedAcc, interpolatedGyr) = interpolateSensorData(accData, gyrData)

        if (commonTimestamps.size < 2) {
            Log.w(TAG, "[FINAL] Falha na interpolação (listas não sobrepostas).")
            return TestResult(timestamp, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0L, 0, 0L, name = "")
        }

        val durationInMillis = commonTimestamps.last() - commonTimestamps.first()
        val fs = commonTimestamps.size / (durationInMillis / 1000.0)

        val linearAcceleration = complementaryFilter(interpolatedAcc, interpolatedGyr, fs)

        val depthSignal = doubleIntegrate(linearAcceleration, fs)

        val minPeakHeightCm = 1.5

        val minPeakDist = fs * 0.4

        val depthInCm = depthSignal.map { it * 100 }
        val depthPeaks = findPeaksWithProminence(depthInCm, minPeakHeightCm.toFloat(), minPeakDist.toInt())
        val total = depthPeaks.size
        if (total < 2) {
            Log.w(TAG, "[FINAL] Não foram encontrados picos de profundidade suficientes ($total).")
            return TestResult(timestamp, 0.0, 0.0, 0, 0, 0, 0, 0, 0, durationInMillis, 0, 0L, name = "")
        }

        val freqs = calculateIndividualFrequenciesInterp(commonTimestamps, depthPeaks)
        val medFreq = if (freqs.isNotEmpty()) freqs.median() else 0.0

        val depths = depthPeaks.map { depthInCm[it].toDouble() }
        val medDepth = if (depths.isNotEmpty()) depths.median() else 0.0

        val correctFreq = freqs.count { it in 100.0..120.0 }
        val slowFreq = freqs.count { it < 100.0 }
        val fastFreq = freqs.count { it > 120.0 }

        val correctDepth = depths.count { it in 5.0..6.0 }

        var correctRecoil = 0
        val RECOIL_MAX_DEVIATION_CM = 1.5

        for (i in 0 until depthPeaks.size) {
            val peakIndex = depthPeaks[i]
            val searchWindowStart = peakIndex
            val searchWindowEnd = if (i + 1 < depthPeaks.size) depthPeaks[i + 1] else depthInCm.size - 1

            if (searchWindowStart >= searchWindowEnd) continue

            val recoilWindow = depthInCm.subList(searchWindowStart, searchWindowEnd)
            val minRecoil = recoilWindow.minOrNull() ?: Float.MAX_VALUE

            if (abs(minRecoil) <= RECOIL_MAX_DEVIATION_CM) {
                correctRecoil++
            }
        }

        var interruptionCount = 0
        var totalInterruptionTimeMs = 0L
        val INTERRUPTION_THRESHOLD_MS = 2000L

        for (i in 0 until depthPeaks.size - 1) {
            val timeA = commonTimestamps[depthPeaks[i]]
            val timeB = commonTimestamps[depthPeaks[i + 1]]
            val deltaT = timeB - timeA

            if (deltaT > INTERRUPTION_THRESHOLD_MS) {
                interruptionCount++
                totalInterruptionTimeMs += (deltaT - 500) // Subtrai o tempo "normal" de uma compressão
            }
        }
        return TestResult(
            timestamp = timestamp,
            medianFrequency = medFreq,
            medianDepth = medDepth.coerceIn(0.0, 15.0),
            totalCompressions = total,
            correctFrequencyCount = correctFreq,
            slowFrequencyCount = slowFreq,
            fastFrequencyCount = fastFreq,
            correctDepthCount = correctDepth,
            correctRecoilCount = correctRecoil,
            durationInMillis = durationInMillis,
            interruptionCount = interruptionCount,
            totalInterruptionTimeMs = totalInterruptionTimeMs,
            name = ""
        )
    }
    private fun interpolateSensorData(
        accData: List<SensorDataPoint>,
        gyrData: List<SensorDataPoint>
    ): Triple<List<Long>, List<FloatArray>, List<FloatArray>> {

        val startTime = max(accData.first().timestamp, gyrData.first().timestamp)
        val endTime = min(accData.last().timestamp, gyrData.last().timestamp)

        if (startTime >= endTime) {
            Log.w(TAG, "[INTERPOLATE] StartTime ($startTime) é maior ou igual a EndTime ($endTime).")
            return Triple(emptyList(), emptyList(), emptyList())
        }

        val cleanAccData = accData.sortedBy { it.timestamp }.distinctBy { it.timestamp }
        val cleanGyrData = gyrData.sortedBy { it.timestamp }.distinctBy { it.timestamp }

        if (cleanAccData.size < 5 || cleanGyrData.size < 5) {
            Log.w(TAG, "[INTERPOLATE] Dados insuficientes para Spline (Acc: ${cleanAccData.size}, Gyr: ${cleanGyrData.size}).")
            return Triple(emptyList(), emptyList(), emptyList())
        }

        val accTimestamps = cleanAccData.map { it.timestamp.toDouble() }.toDoubleArray()
        val gyrTimestamps = cleanGyrData.map { it.timestamp.toDouble() }.toDoubleArray()

        val accX = SplineInterpolator().interpolate(accTimestamps, cleanAccData.map { it.x.toDouble() }.toDoubleArray())
        val accY = SplineInterpolator().interpolate(accTimestamps, cleanAccData.map { it.y.toDouble() }.toDoubleArray())
        val accZ = SplineInterpolator().interpolate(accTimestamps, cleanAccData.map { it.z.toDouble() }.toDoubleArray())

        val gyrX = SplineInterpolator().interpolate(gyrTimestamps, cleanGyrData.map { it.x.toDouble() }.toDoubleArray())
        val gyrY = SplineInterpolator().interpolate(gyrTimestamps, cleanGyrData.map { it.y.toDouble() }.toDoubleArray())
        val gyrZ = SplineInterpolator().interpolate(gyrTimestamps, cleanGyrData.map { it.z.toDouble() }.toDoubleArray())

        val commonTimestamps = (startTime..endTime step 10L).toList()

        val interpolatedAcc = commonTimestamps.map { t ->
            floatArrayOf(accX.value(t.toDouble()).toFloat(), accY.value(t.toDouble()).toFloat(), accZ.value(t.toDouble()).toFloat())
        }
        val interpolatedGyr = commonTimestamps.map { t ->
            floatArrayOf(gyrX.value(t.toDouble()).toFloat(), gyrY.value(t.toDouble()).toFloat(), gyrZ.value(t.toDouble()).toFloat())
        }

        return Triple(commonTimestamps, interpolatedAcc, interpolatedGyr)
    }
    private fun complementaryFilter(
        accData: List<FloatArray>,
        gyrData: List<FloatArray>,
        fs: Double
    ): List<Float> {
        val alpha = 0.98
        val dt = 1.0 / fs

        var anglePitch: Double = 0.0
        var angleRoll: Double = 0.0

        val linearAcceleration = mutableListOf<Float>()

        for (i in accData.indices) {
            val acc = accData[i]
            val gyr = gyrData[i]

            val anglePitchAcc = atan2(acc[1].toDouble(), acc[2].toDouble())
            val angleRollAcc = atan2(-acc[0].toDouble(), sqrt(acc[1] * acc[1] + acc[2] * acc[2].toDouble()))

            anglePitch += gyr[0] * dt
            angleRoll += gyr[1] * dt

            anglePitch = alpha * anglePitch + (1.0 - alpha) * anglePitchAcc
            angleRoll = alpha * angleRoll + (1.0 - alpha) * angleRollAcc

            val g = 9.81

            val linAccZ = acc[0] * sin(angleRoll) -
                    acc[1] * sin(anglePitch) * cos(angleRoll) +
                    acc[2] * cos(anglePitch) * cos(angleRoll) - g

            linearAcceleration.add(linAccZ.toFloat())
        }
        val butterworth = ButterworthFilter(cutoffFrequency = 0.5, sampleRate = fs, isHighPass = true)
        return butterworth.apply(linearAcceleration)
    }
    private fun doubleIntegrate(acceleration: List<Float>, fs: Double): List<Float> {
        val dt = 1.0 / fs

        var velocity = 0.0
        val velocitySignal = FloatArray(acceleration.size)
        val alphaV = 0.99

        for (i in 1 until acceleration.size) {
            velocity = alphaV * (velocity + acceleration[i] * dt)
            velocitySignal[i] = velocity.toFloat()
        }

        var position = 0.0
        val positionSignal = FloatArray(acceleration.size)
        val alphaP = 0.99

        for (i in 1 until velocitySignal.size) {
            position = alphaP * (position + velocitySignal[i] * dt)
            positionSignal[i] = -position.toFloat()
        }
        return positionSignal.toList()
    }

    fun parseData(csvData: String): List<SensorDataPoint> =
        csvData.split("\n").drop(1).mapNotNull {
            try {
                val p = it.split(",")
                if (p.size >= 5) SensorDataPoint(p[0].toLong(), p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat()) else null
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao analisar a linha de dados: $it", e)
                null
            }
        }

    private fun interpolateCubicSplineSafe(data: List<Float>, timestamps: List<Long>, upsampleFactor: Int = 2): Pair<List<Long>, List<Float>> {
        return try {
            if (data.size < 5) return timestamps to data
            Log.d("RCP_SPLINE", "Iniciando Spline com ${data.size} pontos.")
            val sorted = timestamps.zip(data).sortedBy { it.first }.distinctBy { it.first }

            val x = sorted.map { it: Pair<Long, Float> -> it.first / 1000.0 }.toDoubleArray()
            val y = sorted.map { it: Pair<Long, Float> -> it.second.toDouble() }.toDoubleArray()

            val spline = SplineInterpolator().interpolate(x, y)
            val totalDur = x.last() - x.first()
            val newCount = (sorted.size - 1) * upsampleFactor
            val newTimes = DoubleArray(newCount) { i -> x.first() + i * (totalDur / newCount) }

            val newValues = newTimes.map { it: Double -> spline.value(it).toFloat() }
            val newTimestamps = newTimes.map { it: Double -> (it * 1000).roundToLong() }

            Log.d("RCP_SPLINE", "Spline finalizada. Gerados ${newValues.size} pontos.")
            newTimestamps to newValues
        } catch (e: Exception) {
            Log.e("RCP_SPLINE", "Falha na Spline, usando dados originais: ${e.message}")
            timestamps to data
        }
    }
    private fun findPeaksWithProminence(data: List<Float>, minProm: Float, minDist: Int): List<Int> {
        val peaks = (1 until data.lastIndex).filter { data[it] > data[it - 1] && data[it] > data[it + 1] }
        val valid = mutableListOf<Int>()
        var last = -minDist
        for (i in peaks) {
            if (i - last < minDist) continue
            val windowStart = (i - minDist * 2).coerceAtLeast(0)
            val windowEnd = (i + minDist * 2).coerceAtMost(data.size)
            val localMin = data.subList(windowStart, windowEnd).minOrNull() ?: 0f
            if ((data[i] - localMin) > minProm) {
                valid.add(i)
                last = i
            }
        }
        return valid
    }
    private fun removeMeanDrift(data: List<Float>): List<Float> {
        if (data.isEmpty()) return data
        val mean = data.average().toFloat()
        return data.map { it - mean }
    }
    private fun calculateIndividualFrequencies(data: List<SensorDataPoint>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { a, b ->
            val dt = (data[b].timestamp - data[a].timestamp) / 1000.0
            if (dt > 0.2) 60.0 / dt else null
        }.filterNotNull()
    }
    private fun calculateIndividualFrequenciesInterp(times: List<Long>, peaks: List<Int>): List<Double> {
        return peaks.zipWithNext { a, b ->
            val dt = (times[b] - times[a]) / 1000.0
            if (dt > 0.2) 60.0 / dt else null
        }.filterNotNull()
    }
    private fun calculateMedian(data: List<Float>): Float {
        if (data.isEmpty()) return 0f
        val sortedData = data.sorted()
        val mid = sortedData.size / 2
        return if (sortedData.size % 2 == 0) {
            (sortedData[mid - 1] + sortedData[mid]) / 2f
        } else {
            sortedData[mid]
        }
    }
    private fun calculateAdaptiveThreshold(data: List<Float>): Double {
        if (data.isEmpty()) return 1.0
        val median = calculateMedian(data)
        val deviations = data.map { abs(it - median) }
        val mad = calculateMedian(deviations)
        val robustStdDev = mad * 1.4826
        return robustStdDev.coerceAtLeast(1.5)
    }

    private fun List<Double>.median() = sorted().let {
        if (it.isEmpty()) 0.0 else if (it.size % 2 == 0) (it[it.size / 2 - 1] + it[it.size / 2]) / 2.0 else it[it.size / 2]
    }
}