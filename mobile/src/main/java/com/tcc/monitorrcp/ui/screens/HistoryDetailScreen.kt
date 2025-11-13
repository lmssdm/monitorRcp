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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.IconToggleButton
import androidx.compose.ui.Alignment
import com.tcc.monitorrcp.ui.components.PercentageBar
import com.tcc.monitorrcp.ui.components.corCorreta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    test: TestResult?,
    testNumber: Int?,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
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
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
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

                    val date = remember(test.timestamp) {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            .format(Date(test.timestamp))
                    }

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
                        label = "Profundidade Mediana:", // [REFACTOR] Renomeado
                        value = "%.1f cm".format(test.medianDepth) // [REFACTOR] Renomeado
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

                    HistoryMetricRow(
                        label = "Compressões (Recoil Correto):",
                        value = "${test.correctRecoilCount} (${"%.1f".format(test.correctRecoilPercentage)}%)"
                    )

                    // --- [MUDANÇA AQUI] ADICIONADO AS NOVAS MÉTRICAS ---
                    HistoryMetricRow(
                        label = "Total de Pausas (>2s):",
                        value = "${test.interruptionCount}"
                    )
                    HistoryMetricRow(
                        label = "Tempo Total em Pausa:",
                        // Formata os milissegundos para segundos com uma casa decimal
                        value = "%.1f s".format(test.totalInterruptionTimeMs / 1000.0)
                    )
                    // --- FIM DA MUDANÇA ---
                }
            }

            // Card de Diagnóstico e Dicas
            FeedbackCard(test = test)

            // --- Card 2: Gráfico de Frequência ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FrequencyQualityChart(testResult = test)
                }
            }

            // --- Card 3: Gráficos de Qualidade (Profundidade e Recoil) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Análise de Qualidade (Profundidade e Recoil)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    PercentageBar(
                        label = "Qualidade da Profundidade (5-6cm)",
                        percentage = (test.correctDepthPercentage / 100.0).toFloat(),
                        color = corCorreta
                    )

                    PercentageBar(
                        label = "Qualidade do Retorno (Recoil)",
                        percentage = (test.correctRecoilPercentage / 100.0).toFloat(),
                        color = corCorreta
                    )
                }
            }
        }
    }
}

/**
 * Um card que analisa os resultados e dá dicas de melhoria.
 */
@Composable
private fun FeedbackCard(test: TestResult) {
    val tips = mutableListOf<String>()

    // Analisa a Frequência
    if (test.correctFrequencyPercentage < 80.0) {
        if (test.slowFrequencyCount > test.fastFrequencyCount) {
            tips.add("Ritmo Lento: Tente comprimir mais rápido, seguindo o ritmo de 100-120 cpm.")
        } else {
            tips.add("Ritmo Rápido: O ritmo está muito acelerado. Tente diminuir ligeiramente a velocidade.")
        }
    }

    // Analisa a Profundidade
    if (test.correctDepthPercentage < 80.0) {
        tips.add("Profundidade: Lembre-se de usar o peso do corpo para atingir 5-6 cm de profundidade.")
    }

    // Analisa o Recoil
    if (test.correctRecoilPercentage < 80.0) {
        tips.add("Retorno do Tórax (Recoil): É crucial aliviar totalmente o peso do peito após cada compressão.")
    }

    // --- [MUDANÇA AQUI] ADICIONADO FEEDBACK DE INTERRUPÇÃO ---
    // A regra de qualidade (em Model.kt) já marca como "Regular" se passar de 10s
    // Aqui, damos a dica se qualquer pausa longa for detectada.
    if (test.interruptionCount > 0) {
        val seconds = (test.totalInterruptionTimeMs / 1000.0)
        tips.add("Interrupções: Você fez ${test.interruptionCount} pausas longas (totalizando %.1f s). Tente minimizar o tempo sem comprimir.".format(seconds))
    }
    // --- FIM DA MUDANÇA ---


    // Se foi tudo bem
    if (tips.isEmpty() && test.totalCompressions > 0) {
        // [MUDANÇA AQUI] Texto atualizado para incluir "interrupções"
        tips.add("Excelente trabalho! As suas métricas de frequência, profundidade, recoil e interrupções estão ótimas.")
    }

    if (tips.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Dicas",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Diagnóstico e Dicas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            tips.forEach { tip ->
                Text(
                    text = "• $tip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}