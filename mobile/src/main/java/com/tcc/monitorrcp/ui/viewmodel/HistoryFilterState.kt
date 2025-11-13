package com.tcc.monitorrcp.ui.viewmodel

import com.tcc.monitorrcp.model.TestQuality

/**
 * Uma classe de dados dedicada a conter TODOS os estados
 * relacionados à filtragem do histórico.
 */
data class HistoryFilterState(
    // Visibilidade dos pop-ups
    val isFilterSheetVisible: Boolean = false,
    val isDatePickerVisible: Boolean = false,

    // Filtros APLICADOS (usados na query/lógica)
    val appliedQuality: TestQuality = TestQuality.TODOS,
    val appliedDurationMinMs: Long = 0L,
    val appliedDurationMaxMs: Long = 0L,
    val appliedStartDateMs: Long? = null,
    val appliedEndDateMs: Long? = null,

    // Filtros PENDENTES (o que o usuário mexe no BottomSheet)
    val pendingQuality: TestQuality = TestQuality.TODOS,
    val pendingDurationMinSec: String = "",
    val pendingDurationMaxSec: String = "",
    val pendingStartDateMs: Long? = null,
    val pendingEndDateMs: Long? = null
) {
    /**
     * Propriedade computada para saber se algum filtro está ativo.
     */
    val areFiltersActive: Boolean
        get() = appliedQuality != TestQuality.TODOS ||
                appliedDurationMinMs > 0L ||
                appliedDurationMaxMs > 0L ||
                appliedStartDateMs != null
}