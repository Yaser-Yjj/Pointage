package com.lykos.pointage.data.model.response

import com.lykos.pointage.data.model.data.ReportData

data class ReportResponse(
    val success: Boolean,
    val message: String,
    val data: ReportData?
)