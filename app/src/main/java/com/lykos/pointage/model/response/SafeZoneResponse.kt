package com.lykos.pointage.model.response

import com.lykos.pointage.model.data.SafeZoneData


data class SafeZoneResponse(
    val success: Boolean,
    val data: SafeZoneData?,
    val message: String? = null
)