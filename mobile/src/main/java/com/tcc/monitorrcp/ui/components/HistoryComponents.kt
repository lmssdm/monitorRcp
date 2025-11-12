package com.tcc.monitorrcp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
// CORREÇÃO: Adicionando as importações de coleção que faltavam
import kotlin.collections.maxOrNull
import kotlin.collections.minOrNull

@Composable
fun LineChartWithTargetRange(
    data: List<Double>,
    label: String,
    lineColor: Color,
    targetMin: Double,
    targetMax: Double,
    targetColor: Color,
    yAxisLabel: String,
    yAxisLabelsOverride: List<Double>? = null
) {
    val dataPoints = data.takeLast(10).reversed()

    val yAxisLabels = yAxisLabelsOverride ?: listOf(targetMin, targetMax, 80.0, 140.0)

    // Estas chamadas agora funcionarão por causa das novas importações
    val dataMin = dataPoints.minOrNull() ?: 0.0
    val dataMax = dataPoints.maxOrNull() ?: 1.0

    val overallMin = floor(min(yAxisLabels.minOrNull() ?: 0.0, dataMin) / 10.0) * 10.0
    val overallMaxDefault = (max(yAxisLabels.maxOrNull() ?: 0.0, dataMax) / 10.0).let { it + 1 }.toInt() * 10.0
    val overallMax = if (yAxisLabelsOverride != null) max(yAxisLabels.maxOrNull() ?: 10.0, dataMax).coerceAtMost(15.0) else overallMaxDefault

    val range = (overallMax - overallMin).takeIf { it > 0 } ?: 1.0

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val yAxisPaddingPx = with(density) { 50.dp.toPx() }
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
                return (canvasHeight * (1 - ((value - overallMin) / range))).toFloat()
            }

            fun getX(index: Int): Float {
                return yAxisPaddingPx + (index * (canvasWidth / (dataPoints.size - 1).coerceAtLeast(1)))
            }

            // --- RÓTULO DO EIXO Y (Vertical) ---
            val yLabelLayoutResult = textMeasurer.measure(
                text = yAxisLabel,
                style = axisLabelStyle
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.rotate(
                    -90f,
                    yAxisPaddingPx / 4,
                    canvasHeight / 2
                )
                drawText(
                    textLayoutResult = yLabelLayoutResult,
                    topLeft = Offset(
                        x = (canvasHeight / 2) - (yLabelLayoutResult.size.width / 2),
                        y = (yAxisPaddingPx / 4)
                    )
                )
                canvas.nativeCanvas.restore()
            }
            // --- FIM DO RÓTULO DO EIXO Y ---


            // 1. Desenha a Zona-Alvo (verde)
            val targetTopY = getY(targetMax)
            val targetBottomY = getY(targetMin)
            drawRect(
                color = targetColor,
                topLeft = Offset(yAxisPaddingPx, targetTopY),
                size = Size(canvasWidth, targetBottomY - targetTopY)
            )

            // 2. Desenha o Eixo Y (Rótulos e Linhas de Grelha)
            val relevantLabels = yAxisLabelsOverride?.map { it.toInt() }
                ?: (overallMin.toInt()..overallMax.toInt() step 20).toMutableList().apply {
                    addAll(listOf(targetMin.toInt(), targetMax.toInt()))
                }

            relevantLabels.distinct().sorted().forEach { value ->
                val y = getY(value.toDouble())
                val textLayoutResult = textMeasurer.measure(
                    text = "$value",
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

            // 4. Desenha o Eixo X
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

            // --- RÓTULO DO EIXO X (Horizontal) ---
            val xLabelLayoutResult = textMeasurer.measure(
                text = "Testes (Mais Recentes à Esquerda)",
                style = axisLabelStyle
            )
            drawText(
                textLayoutResult = xLabelLayoutResult,
                topLeft = Offset(
                    x = yAxisPaddingPx + (canvasWidth / 2) - (xLabelLayoutResult.size.width / 2),
                    y = size.height - (xLabelLayoutResult.size.height)
                )
            )
            // --- FIM DO RÓTULO DO EIXO X ---
        }

        // 5. Desenha a Legenda
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


@Composable
fun HistoryItem(
    result: TestResult,
    testNumber: Int,
    onClick: () -> Unit
) {
    val date = remember(result.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(result.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Teste $testNumber",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            HistoryMetricRow("Data:", date)
            HistoryMetricRow("Duração:", result.formattedDuration)
            HistoryMetricRow("Total de Compressões:", "${result.totalCompressions}")
        }
    }
}

@Composable
fun HistoryMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}