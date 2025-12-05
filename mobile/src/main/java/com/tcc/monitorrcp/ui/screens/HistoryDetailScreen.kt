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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.tcc.monitorrcp.ui.components.PercentageBar
import com.tcc.monitorrcp.ui.components.corCorreta

/**
 * Detalhes profundos de um teste específico, com gráficos detalhados e feedback textual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    test: TestResult?,
    testNumber: Int?,
    onBack: () -> Unit,
    onExport: () -> Unit,

    testToEditName: TestResult?,
    onShowEditNameDialog: () -> Unit,
    onDismissEditNameDialog: () -> Unit,
    onConfirmEditName: (String) -> Unit
) {
    val title = remember(test, testNumber) {
        if (test?.name?.isNotBlank() == true) {
            test.name
        } else if (testNumber != null) {
            "Detalhes do Teste $testNumber"
        } else {
            "Detalhes do Teste"
        }
    }

    if (testToEditName != null) {
        EditNameDialog(
            currentName = testToEditName.name,
            onDismiss = onDismissEditNameDialog,
            onConfirm = onConfirmEditName
        )
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
                    IconButton(onClick = onShowEditNameDialog) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Nome")
                    }
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
                        label = "Profundidade Mediana:",
                        value = "%.1f cm".format(test.medianDepth)
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
                    HistoryMetricRow(
                        label = "Total de Pausas (>2s):", // Era (>5s)
                        value = "${test.interruptionCount}"
                    )
                    HistoryMetricRow(
                        label = "Tempo Total em Pausa:",
                        value = "%.1f s".format(test.totalInterruptionTimeMs / 1000.0)
                    )
                }
            }

            FeedbackCard(test = test)

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FrequencyQualityChart(testResult = test)
                }
            }

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
@Composable
private fun FeedbackCard(test: TestResult) {
    val tips = mutableListOf<String>()

    var freqIsExcellent = false
    var depthIsExcellent = false
    var recoilIsExcellent = false

    when {
        test.correctFrequencyPercentage < 50.0 -> {
            val tip = if (test.slowFrequencyCount > test.fastFrequencyCount) {
                "Seu ritmo está muito lento. Tente comprimir mais rápido, seguindo o metrônomo."
            } else {
                "Seu ritmo está muito rápido. Tente diminuir a velocidade para o ritmo do metrônomo."
            }
            tips.add("❌ Frequência (Ruim): $tip")
        }
        test.correctFrequencyPercentage < 80.0 -> {
            val tip = if (test.slowFrequencyCount > test.fastFrequencyCount) {
                "Ritmo um pouco lento. Tente acelerar um pouco mais para se manter entre 100-120 cpm."
            } else {
                "Ritmo um pouco rápido. Tente relaxar e diminuir ligeiramente a velocidade."
            }
            tips.add("⚠️ Frequência (Regular): $tip")
        }
        else -> {
            tips.add("✅ Frequência (Excelente): Ótimo ritmo, continue assim!")
            freqIsExcellent = true
        }
    }

    when {
        test.correctDepthPercentage < 50.0 -> {
            tips.add("❌ Profundidade (Ruim): As compressões estão muito rasas. Lembre-se de usar o peso do seu corpo para atingir 5-6 cm.")
        }
        test.correctDepthPercentage < 80.0 -> {
            tips.add("⚠️ Profundidade (Regular): Quase lá! Concentre-se em aplicar um pouco mais de força para atingir os 5-6 cm recomendados.")
        }
        else -> {
            tips.add("✅ Profundidade (Excelente): Profundidade perfeita (5-6 cm).")
            depthIsExcellent = true
        }
    }

    when {
        test.correctRecoilPercentage < 50.0 -> {
            tips.add("❌ Recoil (Ruim): É crucial aliviar totalmente o peso do peito após cada compressão. Isso permite o sangue voltar ao coração.")
        }
        test.correctRecoilPercentage < 80.0 -> {
            tips.add("⚠️ Recoil (Regular): Lembre-se de deixar o tórax retornar completamente. Evite 'descansar' sobre a vítima entre as compressões.")
        }
        else -> {
            tips.add("✅ Recoil (Excelente): O retorno do tórax está ótimo.")
            recoilIsExcellent = true
        }
    }

    if (test.interruptionCount > 0) {
        val seconds = (test.totalInterruptionTimeMs / 1000.0)
        tips.add("⚠️ Interrupções: Você fez ${test.interruptionCount} pausas longas (totalizando %.1f s). Tente minimizar o tempo sem comprimir.".format(seconds))
    }
    if (freqIsExcellent && depthIsExcellent && recoilIsExcellent && test.interruptionCount == 0 && test.totalCompressions > 0) {
        tips.clear()
        tips.add("🏆 Excelente trabalho! Suas métricas de frequência, profundidade e recoil estão ótimas. Continue assim!")
    } else if (tips.isEmpty()) {
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

@Composable
private fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Nome do Teste") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nome do Teste") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}