package com.lykos.pointage.model.response

import com.lykos.pointage.model.data.ReportData

data class ReportResponse(
    val success: Boolean,
    val message: String,
    val data: ReportData?
)