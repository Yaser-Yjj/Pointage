package com.lykos.pointage.model.response

import com.lykos.pointage.model.data.PvReportData

class PvReportResponse(
    val success: Boolean,
    val message: String,
    val data: PvReportData? = null
)