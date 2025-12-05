package com.tcc.monitorrcp.ui.viewmodel

import com.tcc.monitorrcp.model.TestQuality

/**
 * Guarda o estado dos filtros da tela de histórico.
 */
data class HistoryFilterState(
    val isFilterSheetVisible: Boolean = false,
    val isDatePickerVisible: Boolean = false,

    val appliedQuality: TestQuality = TestQuality.TODOS,
    val appliedDurationMinMs: Long = 0L,
    val appliedDurationMaxMs: Long = 0L,
    val appliedStartDateMs: Long? = null,
    val appliedEndDateMs: Long? = null,

    val pendingQuality: TestQuality = TestQuality.TODOS,
    val pendingDurationMinSec: String = "",
    val pendingDurationMaxSec: String = "",
    val pendingStartDateMs: Long? = null,
    val pendingEndDateMs: Long? = null
) {
    val areFiltersActive: Boolean
        get() = appliedQuality != TestQuality.TODOS ||
                appliedDurationMinMs > 0L ||
                appliedDurationMaxMs > 0L ||
                appliedStartDateMs != null
}