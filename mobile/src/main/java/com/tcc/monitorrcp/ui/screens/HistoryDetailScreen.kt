package com.tcc.monitorrcp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.components.FrequencyQualityChart
import com.tcc.monitorrcp.ui.components.HistoryMetricRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    test: TestResult?,
    testNumber: Int?, // ALTERAÇÃO AQUI: Recebe o número
    onBack: () -> Unit
) {
    // ALTERAÇÃO AQUI: Usa o testNumber para o título
    val title = remember(testNumber) {
        if (testNumber != null) {
            "Detalhes do Teste $testNumber"
        } else {
            "Detalhes do Teste"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) }, // Usa o novo título
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        if (test == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Erro: Teste não encontrado.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Card 1: Resumo das Métricas (EXPANDIDO) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Resumo do Teste",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Pega a data formatada
                    val date = remember(test.timestamp) {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            .format(Date(test.timestamp))
                    }

                    // ALTERAÇÃO AQUI: Todas as métricas agora estão aqui
                    HistoryMetricRow(
                        label = "Data:",
                        value = date
                    )
                    HistoryMetricRow(
                        label = "Duração:",
                        value = test.formattedDuration
                    )
                    HistoryMetricRow(
                        label = "Total de Compressões:",
                        value = "${test.totalCompressions}"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    HistoryMetricRow(
                        label = "Frequência Mediana:",
                        value = "%.0f cpm".format(test.medianFrequency)
                    )
                    HistoryMetricRow(
                        label = "Profundidade Média:",
                        value = "%.1f cm".format(test.averageDepth)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    HistoryMetricRow(
                        label = "Compressões (Freq. Correta):",
                        value = "${test.correctFrequencyCount} (${"%.1f".format(test.correctFrequencyPercentage)}%)"
                    )
                    HistoryMetricRow(
                        label = "Compressões (Freq. Lenta):",
                        value = "${test.slowFrequencyCount}"
                    )
                    HistoryMetricRow(
                        label = "Compressões (Freq. Rápida):",
                        value = "${test.fastFrequencyCount}"
                    )
                    HistoryMetricRow(
                        label = "Compressões (Prof. Correta):",
                        value = "${test.correctDepthCount}"
                    )
                }
            }

            // --- Card 2: Gráfico de Frequência ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Este é o gráfico de barra empilhada
                    FrequencyQualityChart(testResult = test)
                }
            }

            // --- Card 3: Placeholder de Profundidade ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Análise da Profundidade",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        // Exibe a contagem de profundidade correta (embora o gráfico não esteja pronto)
                        text = "Compressões Corretas (5-6 cm): ${test.correctDepthCount} de ${test.totalCompressions}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}