package com.tcc.monitorrcp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.components.HistoryItem
import com.tcc.monitorrcp.ui.components.LineChartWithTargetRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<TestResult>,
    onBack: () -> Unit,
    onTestClick: (TestResult, Int) -> Unit, // ALTERAÇÃO AQUI: Assinatura atualizada
    isSortDescending: Boolean,
    onToggleSortOrder: () -> Unit
) {
    var isListExpanded by remember { mutableStateOf(false) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isListExpanded) 180f else 0f,
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Testes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum teste realizado ainda.")
                }
            } else {
                Text("Resultados Anteriores", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // Gráfico 1 (Frequência)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LineChartWithTargetRange(
                        data = history.map { it.medianFrequency },
                        label = "Evolução da Frequência (cpm)",
                        lineColor = Color(0xFF1565C0),
                        targetMin = 100.0,
                        targetMax = 120.0,
                        targetColor = Color(0xFF66BB6A).copy(alpha = 0.2f),
                        yAxisLabel = "cpm",
                        yAxisLabelsOverride = null
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Gráfico 2 (Profundidade)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LineChartWithTargetRange(
                        data = history.map { it.averageDepth },
                        label = "Evolução da Profundidade (cm)",
                        lineColor = Color(0xFF2E7D32),
                        targetMin = 5.0,
                        targetMax = 6.0,
                        targetColor = Color(0xFF66BB6A).copy(alpha = 0.2f),
                        yAxisLabel = "cm",
                        yAxisLabelsOverride = listOf(0.0, 2.0, 5.0, 6.0, 8.0, 10.0)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isListExpanded = !isListExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lista Detalhada",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onToggleSortOrder) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = if (isSortDescending) "Ordenar por Mais Antigos" else "Ordenar por Mais Recentes"
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isListExpanded) "Recolher" else "Expandir",
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }

                AnimatedVisibility(visible = isListExpanded) {
                    Column {
                        history.forEachIndexed { index, result ->
                            // ALTERAÇÃO AQUI: Calcula o número do teste
                            val testNumber = if (isSortDescending) history.size - index else index + 1

                            HistoryItem(
                                result = result,
                                testNumber = testNumber, // Passa o número
                                // Passa o teste e o número para o clique
                                onClick = { onTestClick(result, testNumber) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}