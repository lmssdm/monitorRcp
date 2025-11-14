package com.tcc.monitorrcp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.model.DateFilter
import com.tcc.monitorrcp.model.TestQuality
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.components.AdvancedFilterSheet
import com.tcc.monitorrcp.ui.components.HistoryItem
import com.tcc.monitorrcp.ui.components.HistoryMetricRow
import com.tcc.monitorrcp.ui.components.LineChartWithTargetRange
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import com.tcc.monitorrcp.ui.viewmodel.HistoryFilterState
import com.tcc.monitorrcp.ui.components.AppWatermark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<TestResult>,
    onBack: () -> Unit,
    onTestClick: (TestResult, Int) -> Unit,
    isSortDescending: Boolean,
    onToggleSortOrder: () -> Unit,
    filterState: HistoryFilterState,
    onShowFilterSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onPendingQualityChanged: (TestQuality) -> Unit,
    onPendingDurationMinChanged: (String) -> Unit,
    onPendingDurationMaxChanged: (String) -> Unit,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit,
    onClearFilters: () -> Unit,
    onApplyFilters: () -> Unit,
    onDeleteTest: (TestResult) -> Unit
) {
    var isListExpanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(targetValue = if (isListExpanded) 180f else 0f, label = "rotation")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val datePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filterState.pendingStartDateMs,
        initialSelectedEndDateMillis = filterState.pendingEndDateMs
    )

    var testToDelete by remember { mutableStateOf<TestResult?>(null) }
    val showDeleteDialog = testToDelete != null

    if (filterState.isDatePickerVisible) {
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    onDateRangeSelected(
                        datePickerState.selectedStartDateMillis,
                        datePickerState.selectedEndDateMillis
                    )
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) {
                    Text("Cancelar")
                }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                modifier = Modifier.height(500.dp),
                showModeToggle = false
            )
        }
    }

    if (filterState.isFilterSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismissFilterSheet,
            sheetState = sheetState
        ) {
            AdvancedFilterSheet(
                filterState = filterState,
                onPendingQualityChanged = onPendingQualityChanged,
                onPendingDurationMinChanged = onPendingDurationMinChanged,
                onPendingDurationMaxChanged = onPendingDurationMaxChanged,
                onShowDatePicker = onShowDatePicker,
                onDismissDatePicker = onDismissDatePicker,
                onDateRangeSelected = onDateRangeSelected,
                onClearFilters = onClearFilters,
                onApplyFilters = onApplyFilters
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { testToDelete = null },
            title = { Text("Excluir Teste") },
            text = { Text("Tem certeza que deseja excluir este teste? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        testToDelete?.let { onDeleteTest(it) }
                        testToDelete = null
                    }
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { testToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

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

        val areFiltersActive = filterState.areFiltersActive

        if (history.isEmpty() && !areFiltersActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AppWatermark()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nenhum teste realizado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    val totalTests = history.size
                    val avgFreqPercent = history.map { it.correctFrequencyPercentage }.average().takeIf { !it.isNaN() } ?: 0.0
                    val avgDepthPercent = history.map { it.correctDepthPercentage }.average().takeIf { !it.isNaN() } ?: 0.0
                    val avgRecoilPercent = history.map { it.correctRecoilPercentage }.average().takeIf { !it.isNaN() } ?: 0.0

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (areFiltersActive) "Resumo (Média de $totalTests testes filtrados)" else "Resumo Geral (Média de $totalTests testes)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        HistoryMetricRow(
                            label = "Qualidade da Frequência:",
                            value = String.format(Locale.getDefault(), "%.1f%%", avgFreqPercent)
                        )
                        HistoryMetricRow(
                            label = "Qualidade da Profundidade:",
                            value = String.format(Locale.getDefault(), "%.1f%%", avgDepthPercent)
                        )
                        HistoryMetricRow(
                            label = "Qualidade do Recoil:",
                            value = String.format(Locale.getDefault(), "%.1f%%", avgRecoilPercent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Evolução dos Testes", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // --- [MUDANÇA PONTO 5] Label dinâmico ---
                val xAxisLabel = if (isSortDescending) {
                    "Testes (Mais Antigo à Esquerda)"
                } else {
                    "Testes (Mais Novo à Esquerda)"
                }
                // --- FIM DA MUDANÇA ---

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
                        xAxisLabel = xAxisLabel, // Passa o label dinâmico
                        yAxisLabelsOverride = null,
                        yMinLimit = null, // Sem limite mínimo para frequência
                        yMaxLimit = 140.0
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LineChartWithTargetRange(
                        data = history.map { it.medianDepth },
                        label = "Evolução da Profundidade (cm)",
                        lineColor = Color(0xFF2E7D32),
                        targetMin = 5.0,
                        targetMax = 6.0,
                        targetColor = Color(0xFF66BB6A).copy(alpha = 0.2f),
                        yAxisLabel = "cm",
                        xAxisLabel = xAxisLabel, // Passa o label dinâmico

                        // --- [MUDANÇA PONTO 3] Zoom no gráfico ---
                        yAxisLabelsOverride = listOf(3.0, 4.0, 5.0, 6.0, 7.0, 8.0),
                        yMinLimit = 3.0, // Força o eixo a começar em 3.0
                        yMaxLimit = 8.0  // Força o eixo a terminar em 8.0
                        // --- FIM DA MUDANÇA ---
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Testes",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onShowFilterSheet) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtrar testes"
                        )
                    }
                    IconButton(onClick = onToggleSortOrder) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = if (isSortDescending) "Ordenar por Mais Antigos" else "Ordenar por Mais Recentes"
                        )
                    }
                    IconButton(onClick = { isListExpanded = !isListExpanded }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isListExpanded) "Recolher" else "Expandir",
                            modifier = Modifier.rotate(rotationAngle)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(visible = isListExpanded) {
                    Column {
                        if (history.isEmpty() && areFiltersActive) {
                            Text(
                                "Nenhum teste encontrado para este filtro.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        } else {
                            history.forEachIndexed { index, result ->
                                val testNumber = if (isSortDescending) history.size - index else index + 1

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                                            testToDelete = result
                                            return@rememberSwipeToDismissBoxState false
                                        }
                                        return@rememberSwipeToDismissBoxState false
                                    }
                                )

                                LaunchedEffect(testToDelete) {
                                    if (testToDelete == null && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                        dismissState.reset()
                                    }
                                }

                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    enableDismissFromEndToStart = true,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        val color by animateColorAsState(
                                            targetValue = when (dismissState.targetValue) {
                                                SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                                else -> Color.Transparent
                                            }, label = "color"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Excluir",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                ) {
                                    HistoryItem(
                                        result = result,
                                        testNumber = testNumber,
                                        onClick = { onTestClick(result, testNumber) }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}