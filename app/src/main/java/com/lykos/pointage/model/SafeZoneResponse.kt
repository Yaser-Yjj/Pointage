package com.lykos.pointage.model


data class SafeZoneResponse(
    val success: Boolean,
    val data: SafeZoneData?,
    val message: String? = null
)