package com.tcc.monitorrcp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.model.DateFilter
import com.tcc.monitorrcp.model.TestQuality
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
// [CORREÇÃO BUG 1] Importar o TimeZone
import java.util.TimeZone


/**
 * Este é o BottomSheet (painel deslizante) que contém
 * todos os filtros avançados.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    // Estados dos filtros PENDENTES
    pendingQuality: TestQuality,
    pendingDurationMin: String,
    pendingDurationMax: String,
    pendingStartDate: Long?,
    pendingEndDate: Long?,

    // Funções para atualizar os filtros PENDENTES
    onPendingQualityChanged: (TestQuality) -> Unit,
    onPendingDurationMinChanged: (String) -> Unit,
    onPendingDurationMaxChanged: (String) -> Unit,

    onShowDatePicker: () -> Unit,

    // Funções para os botões de ação
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit
) {

    // [CORREÇÃO BUG 1 & 2] Usar TimeZone UTC e formato "dd/MM/yy"
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val startDateText = pendingStartDate?.let { dateFormatter.format(it) } ?: "Data Início"
    val endDateText = pendingEndDate?.let { dateFormatter.format(it) } ?: "Data Fim"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
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
                        selected = pendingQuality == TestQuality.TODOS,
                        onClick = { onPendingQualityChanged(TestQuality.TODOS) },
                        label = { Text("Todos") },
                        leadingIcon = { CheckmarkIcon(isSelected = pendingQuality == TestQuality.TODOS) }
                    )
                    FilterChip(
                        selected = pendingQuality == TestQuality.BOM,
                        onClick = { onPendingQualityChanged(TestQuality.BOM) },
                        label = { Text("Bons") },
                        leadingIcon = { CheckmarkIcon(isSelected = pendingQuality == TestQuality.BOM) }
                    )
                    FilterChip(
                        selected = pendingQuality == TestQuality.REGULAR,
                        onClick = { onPendingQualityChanged(TestQuality.REGULAR) },
                        label = { Text("Regulares") },
                        leadingIcon = { CheckmarkIcon(isSelected = pendingQuality == TestQuality.REGULAR) }
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
                        value = pendingDurationMin,
                        onValueChange = onPendingDurationMinChanged,
                        label = { Text("Mín. (seg)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    // Campo Máximo
                    OutlinedTextField(
                        value = pendingDurationMax,
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
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(startDateText, maxLines = 1) // [CORREÇÃO BUG 2] Adicionado maxLines
                    }
                    // Botão "Data Fim"
                    OutlinedButton(
                        onClick = onShowDatePicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(endDateText, maxLines = 1) // [CORREÇÃO BUG 2] Adicionado maxLines
                    }
                }
            }

            // 4. Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
    } else {
        null
    }
}