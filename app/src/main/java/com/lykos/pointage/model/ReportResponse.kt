package com.lykos.pointage.model

data class ReportResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)