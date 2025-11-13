package com.tcc.monitorrcp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
// --- [CORREÇÃO AQUI] Importação que faltava ---
import androidx.compose.foundation.layout.size
// --- FIM DA CORREÇÃO ---
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tcc.monitorrcp.model.TestQuality
import com.tcc.monitorrcp.ui.viewmodel.HistoryFilterState // Importa a nova classe
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


/**
 * Este é o conteúdo do BottomSheet (painel deslizante) que contém
 * todos os filtros avançados.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterSheet(
    // [REATORAÇÃO] Recebe o objeto de estado único
    filterState: HistoryFilterState,

    // Funções de evento
    onPendingQualityChanged: (TestQuality) -> Unit,
    onPendingDurationMinChanged: (String) -> Unit,
    onPendingDurationMaxChanged: (String) -> Unit,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit,
    onClearFilters: () -> Unit,
    onApplyFilters: () -> Unit
) {

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    // [REATORAÇÃO] Lê os valores pendentes do filterState
    val startDateText = filterState.pendingStartDateMs?.let { dateFormatter.format(it) } ?: "Data Início"
    val endDateText = filterState.pendingEndDateMs?.let { dateFormatter.format(it) } ?: "Data Fim"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Título
        Text(
            "Filtrar Testes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        HorizontalDivider()

        // 1. Filtro por Qualidade
        FilterSection(title = "Filtrar por Qualidade") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    // [REATORAÇÃO] Lê do filterState
                    selected = filterState.pendingQuality == TestQuality.TODOS,
                    onClick = { onPendingQualityChanged(TestQuality.TODOS) },
                    label = { Text("Todos") },
                    leadingIcon = { CheckmarkIcon(isSelected = filterState.pendingQuality == TestQuality.TODOS) }
                )
                FilterChip(
                    selected = filterState.pendingQuality == TestQuality.BOM,
                    onClick = { onPendingQualityChanged(TestQuality.BOM) },
                    label = { Text("Bons") },
                    leadingIcon = { CheckmarkIcon(isSelected = filterState.pendingQuality == TestQuality.BOM) }
                )
                FilterChip(
                    selected = filterState.pendingQuality == TestQuality.REGULAR,
                    onClick = { onPendingQualityChanged(TestQuality.REGULAR) },
                    label = { Text("Regulares") },
                    leadingIcon = { CheckmarkIcon(isSelected = filterState.pendingQuality == TestQuality.REGULAR) }
                )
            }
        }

        // 2. Filtro por Duração (Campos de Texto)
        FilterSection(title = "Filtrar por Duração (em segundos)") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Campo Mínimo
                OutlinedTextField(
                    // [REATORAÇÃO] Lê do filterState
                    value = filterState.pendingDurationMinSec,
                    onValueChange = onPendingDurationMinChanged,
                    label = { Text("Mín. (seg)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                // Campo Máximo
                OutlinedTextField(
                    // [REATORAÇÃO] Lê do filterState
                    value = filterState.pendingDurationMaxSec,
                    onValueChange = onPendingDurationMaxChanged,
                    label = { Text("Máx. (seg)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }

        // 3. Filtro por Data
        FilterSection(title = "Filtrar por Data") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão "Data Início"
                OutlinedButton(
                    onClick = onShowDatePicker,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(startDateText, maxLines = 1)
                }
                // Botão "Data Fim"
                OutlinedButton(
                    onClick = onShowDatePicker,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(endDateText, maxLines = 1)
                }
            }
        }

        // 4. Botões de Ação
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onClearFilters,
                modifier = Modifier.weight(1f)
            ) {
                Text("Limpar Filtros")
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onApplyFilters,
                modifier = Modifier.weight(1f)
            ) {
                Text("Aplicar")
            }
        }
    }
}

// Componente auxiliar para o conteúdo do BottomSheet
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

// Componente auxiliar para o ícone de "check"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckmarkIcon(isSelected: Boolean) {
    if (isSelected) {
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = "Selecionado",
            modifier = Modifier.size(FilterChipDefaults.IconSize)
        )
    }
}