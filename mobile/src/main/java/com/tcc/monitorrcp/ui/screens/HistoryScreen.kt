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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<TestResult>,
    onBack: () -> Unit,
    onTestClick: (TestResult, Int) -> Unit,
    isSortDescending: Boolean,
    onToggleSortOrder: () -> Unit,

    isFilterSheetVisible: Boolean,
    onShowFilterSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,

    pendingQuality: TestQuality,
    pendingDurationMin: String,
    pendingDurationMax: String,
    pendingStartDate: Long?,
    pendingEndDate: Long?,

    onPendingQualityChanged: (TestQuality) -> Unit,
    onPendingDurationMinChanged: (String) -> Unit,
    onPendingDurationMaxChanged: (String) -> Unit,

    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,

    appliedQualityFilter: TestQuality,
    appliedDurationFilterMinMs: Long,
    appliedDurationFilterMaxMs: Long,

    isDatePickerVisible: Boolean,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit
) {
    var isListExpanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(targetValue = if (isListExpanded) 180f else 0f, label = "rotation")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val datePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = pendingStartDate,
        initialSelectedEndDateMillis = pendingEndDate
    )

    if (isFilterSheetVisible) {
        AdvancedFilterSheet(
            sheetState = sheetState,
            onDismiss = onDismissFilterSheet,

            pendingQuality = pendingQuality,
            pendingDurationMin = pendingDurationMin,
            pendingDurationMax = pendingDurationMax,
            pendingStartDate = pendingStartDate,
            pendingEndDate = pendingEndDate,

            onPendingQualityChanged = onPendingQualityChanged,
            onPendingDurationMinChanged = onPendingDurationMinChanged,
            onPendingDurationMaxChanged = onPendingDurationMaxChanged,

            onShowDatePicker = onShowDatePicker,

            onApplyFilters = onApplyFilters,
            onClearFilters = onClearFilters
        )
    }

    if (isDatePickerVisible) {
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
            val areFiltersActive = appliedQualityFilter != TestQuality.TODOS ||
                    appliedDurationFilterMinMs > 0L ||
                    appliedDurationFilterMaxMs > 0L ||
                    pendingStartDate != null

            if (history.isEmpty() && !areFiltersActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum teste realizado ainda.")
                }
            } else {

                // Card de Resumo Geral
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
                        yAxisLabelsOverride = null,
                        yMaxLimit = 140.0
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
                        data = history.map { it.medianDepth }, // [REFACTOR] Renomeado
                        label = "Evolução da Profundidade (cm)",
                        lineColor = Color(0xFF2E7D32),
                        targetMin = 5.0,
                        targetMax = 6.0,
                        targetColor = Color(0xFF66BB6A).copy(alpha = 0.2f),
                        yAxisLabel = "cm",
                        yAxisLabelsOverride = listOf(0.0, 2.0, 5.0, 6.0, 8.0, 10.0),
                        yMaxLimit = 10.0
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Linha de Título "Testes" com novos botões
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

                    // Botão de Filtro
                    IconButton(onClick = onShowFilterSheet) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtrar testes"
                        )
                    }

                    // Botão de Ordenação
                    IconButton(onClick = onToggleSortOrder) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = if (isSortDescending) "Ordenar por Mais Antigos" else "Ordenar por Mais Recentes"
                        )
                    }

                    // Botão de Expandir/Recolher
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
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        } else {
                            history.forEachIndexed { index, result ->
                                val testNumber = if (isSortDescending) history.size - index else index + 1
                                HistoryItem(
                                    result = result,
                                    testNumber = testNumber,
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
}