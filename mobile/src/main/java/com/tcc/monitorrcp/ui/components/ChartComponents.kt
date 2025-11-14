package com.tcc.monitorrcp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.model.TestResult
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.collections.maxOfOrNull
import kotlin.collections.minOrNull

// Definição das cores que usaremos para o gráfico
val corLenta = Color(0xFFEF5350) // Vermelho para "Lento"
val corCorreta = Color(0xFF66BB6A) // Verde para "Correto"
val corRapida = Color(0xFFFFEE58) // Amarelo para "Rápido"

/**
 * Representa uma única secção da barra de gráfico.
 */
private data class ChartSection(
    val label: String,
    val count: Int,
    val color: Color,
    val percentage: Float
)

/**
 * O Composable principal do gráfico.
 * Alterado para um gráfico de barras horizontais agrupadas.
 */
@Composable
fun FrequencyQualityChart(testResult: TestResult) {

    val totalFrequencies = (
            testResult.slowFrequencyCount +
                    testResult.correctFrequencyCount +
                    testResult.fastFrequencyCount
            ).toFloat().coerceAtLeast(1f)

    val sections = listOf(
        ChartSection(
            label = "Lento (<100)",
            count = testResult.slowFrequencyCount,
            color = corLenta,
            percentage = testResult.slowFrequencyCount / totalFrequencies
        ),
        ChartSection(
            label = "Correto (100-120)",
            count = testResult.correctFrequencyCount,
            color = corCorreta,
            percentage = testResult.correctFrequencyCount / totalFrequencies
        ),
        ChartSection(
            label = "Rápido (>120)",
            count = testResult.fastFrequencyCount,
            color = corRapida,
            percentage = testResult.fastFrequencyCount / totalFrequencies
        )
    )

    // Encontra a porcentagem máxima para normalizar as barras
    val maxPercentage = sections.maxOfOrNull { it.percentage }?.coerceAtLeast(0.01f) ?: 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Análise da Frequência",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Desenha as Barras Agrupadas
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            sections.forEach { section ->
                // Calcula a fração da barra em relação à barra mais longa
                val barFraction = if (maxPercentage > 0) section.percentage / maxPercentage else 0f

                BarItem(
                    label = section.label,
                    valueText = String.format(
                        Locale.getDefault(),
                        "%d (%.1f%%)",
                        section.count,
                        section.percentage * 100
                    ),
                    color = section.color,
                    fraction = barFraction
                )
            }
        }
    }
}

/**
 * Um Composable privado para desenhar uma única linha do gráfico de barras horizontal.
 * Inclui os rótulos e a própria barra.
 */
@Composable
private fun BarItem(
    label: String,
    valueText: String,
    color: Color,
    fraction: Float
) {
    Column {
        // Linha para os rótulos (Nome e Valor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Box para a barra (fundo + barra colorida)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // Cor da "trilha"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction) // Largura proporcional da barra
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color) // Cor da barra
            )
        }
    }
}

/**
 * Desenha uma barra de porcentagem simples (Gauge) para métricas de qualidade.
 * @param label Título da métrica (ex: "Qualidade da Profundidade")
 * @param percentage Valor percentual (0.0f a 1.0f)
 * @param color Cor da barra
 */
@Composable
fun PercentageBar(
    label: String,
    percentage: Float,
    color: Color
) {
    val percentageText = String.format(Locale.getDefault(), "%.1f%%", percentage * 100)

    Column {
        // Linha para os rótulos (Nome e Valor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = percentageText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Box para a barra (fundo + barra colorida)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // Cor da "trilha"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage) // Largura proporcional da barra
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color) // Cor da barra
            )
        }
    }
}

@Composable
fun LineChartWithTargetRange(
    data: List<Double>,
    label: String,
    lineColor: Color,
    targetMin: Double,
    targetMax: Double,
    targetColor: Color,
    yAxisLabel: String,
    xAxisLabel: String,
    yAxisLabelsOverride: List<Double>? = null,
    // --- [CORREÇÃO] Adicionado yMinLimit ---
    yMinLimit: Double? = null,
    yMaxLimit: Double? = null
) {
    val dataPoints = data.takeLast(10).reversed()

    // --- [CORREÇÃO] Lógica de Min/Max e Labels revertida para a original (que funcionava) ---
    val yAxisLabels = yAxisLabelsOverride ?: listOf(targetMin, targetMax, 80.0, 140.0)

    val dataMin = dataPoints.minOrNull() ?: 0.0
    val dataMax = dataPoints.maxOrNull() ?: 1.0

    val defaultMin = floor(min(yAxisLabels.minOrNull() ?: 0.0, dataMin) / 10.0) * 10.0
    val defaultMax = (max(yAxisLabels.maxOrNull() ?: 0.0, dataMax) / 10.0).let { it + 1 }.toInt() * 10.0

    // Aplica os overrides de min/max (para o zoom)
    val overallMin = yMinLimit ?: defaultMin
    val overallMax = yMaxLimit ?: defaultMax
    // --- FIM DA CORREÇÃO ---

    val range = (overallMax - overallMin).takeIf { it > 0 } ?: 1.0

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val yAxisPaddingPx = with(density) { 55.dp.toPx() }
    val xAxisPaddingPx = with(density) { 30.dp.toPx() }

    val textStyle = TextStyle(fontSize = 12.sp, color = Color.Black)
    val axisLabelStyle = TextStyle(fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {

            val canvasWidth = size.width - yAxisPaddingPx
            val canvasHeight = size.height - xAxisPaddingPx

            fun getY(value: Double): Float {
                val clampedValue = value.coerceIn(overallMin, overallMax)
                return (canvasHeight * (1 - ((clampedValue - overallMin) / range))).toFloat()
            }

            fun getX(index: Int): Float {
                return yAxisPaddingPx + (index * (canvasWidth / (dataPoints.size - 1).coerceAtLeast(1)))
            }

            // 1. Desenha a Zona-Alvo (verde)
            val targetTopY = getY(targetMax)
            val targetBottomY = getY(targetMin)
            drawRect(
                color = targetColor,
                topLeft = Offset(yAxisPaddingPx, targetTopY),
                size = Size(canvasWidth, targetBottomY - targetTopY)
            )

            // 2. Desenha o Eixo Y (Rótulos e Linhas de Grelha)
            // --- [CORREÇÃO] Lógica de Labels revertida para a original (que funcionava) ---
            val relevantLabels = yAxisLabelsOverride?.map { it.toInt() }
                ?: (overallMin.toInt()..overallMax.toInt() step 20).toMutableList().apply {
                    addAll(listOf(targetMin.toInt(), targetMax.toInt()))
                }
            // --- FIM DA CORREÇÃO ---

            relevantLabels.distinct().sorted().forEach { value ->
                val y = getY(value.toDouble())
                val labelText = "$value $yAxisLabel"
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = textStyle
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = yAxisPaddingPx - textLayoutResult.size.width - (with(density) { 4.dp.toPx() }),
                        y = y - (textLayoutResult.size.height / 2)
                    )
                )
                drawLine(
                    color = Color.Gray,
                    start = Offset(yAxisPaddingPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = pathEffect
                )
            }

            // 3. Desenha a Linha de Evolução
            if (dataPoints.size >= 2) {
                val linePath = Path()
                dataPoints.forEachIndexed { i, value ->
                    val x = getX(i)
                    val y = getY(value)
                    if (i == 0) {
                        linePath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 6f)
                )
            }

            // 4. Desenha os pontos (círculos)
            dataPoints.forEachIndexed { i, value ->
                val x = getX(i)
                val y = getY(value)
                drawCircle(
                    color = lineColor,
                    radius = 8f,
                    center = Offset(x, y)
                )
            }

            // 5. Desenha o Eixo X
            dataPoints.forEachIndexed { i, _ ->
                val x = getX(i)
                val labelText = (i + 1).toString()
                val textLayoutResult = textMeasurer.measure(text = labelText, style = textStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = x - (textLayoutResult.size.width / 2),
                        y = canvasHeight + (xAxisPaddingPx / 4)
                    )
                )
            }
        }

        // 6. Rótulo do Eixo X
        Text(
            text = xAxisLabel,
            style = axisLabelStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center
        )

        // 7. Legenda
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = lineColor, label = "Sua Média")
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            LegendItem(color = targetColor, label = "Zona Alvo ($targetMin-$targetMax $yAxisLabel)")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}