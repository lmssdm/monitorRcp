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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.model.TestResult
import java.util.Locale

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
 * O Composable principal do gráfico de barras empilhadas.
 * Ele recebe o resultado do teste e exibe a análise da Frequência.
 */
@Composable
fun FrequencyQualityChart(testResult: TestResult) {

    // [CORREÇÃO] O 'total' para o gráfico deve ser a soma das frequências
    // (que é 'totalCompressions - 1', pois a primeira compressão não tem frequência)
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Análise da Frequência",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Desenha a Barra Empilhada
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            sections.forEach { section ->
                if (section.percentage > 0) {
                    Box(
                        modifier = Modifier
                            .weight(section.percentage)
                            // [CORREÇÃO] O .fillMaxWidth() foi REMOVIDO daqui,
                            // pois conflitava com o .weight() e causava o bug.
                            .height(30.dp)
                            .background(section.color)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Desenha a Legenda
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEach { section ->
                LegendItem(
                    color = section.color,
                    label = section.label,
                    value = String.format(
                        Locale.getDefault(),
                        "%d (%.1f%%)",
                        section.count,
                        section.percentage * 100
                    )
                )
            }
        }
    }
}

/**
 * Um item da legenda (ex: "Cor • Lento (<100)   15 (10.5%)")
 */
@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 14.sp
            )
        }
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}