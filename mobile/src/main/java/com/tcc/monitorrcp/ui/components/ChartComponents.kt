package com.tcc.monitorrcp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tcc.monitorrcp.model.TestResult
import java.util.Locale
// CORREÇÃO: Importação corrigida de 'kotlin.math' para 'kotlin.collections'
import kotlin.collections.maxOfOrNull

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
    // Isso garante que a barra mais longa tenha 100% de largura
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