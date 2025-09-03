package com.lykos.pointage.model.response

import com.lykos.pointage.model.data.SafeZoneData


data class SafeZoneResponse(
    val success: Boolean,
    val message: String?,
    val count: Int,
    val data: List<SafeZoneData>
)