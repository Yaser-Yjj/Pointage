package com.lykos.pointage.data.model.response

import com.lykos.pointage.data.model.data.PvReportData

class PvReportResponse(
    val success: Boolean,
    val message: String,
    val data: PvReportData? = null
)