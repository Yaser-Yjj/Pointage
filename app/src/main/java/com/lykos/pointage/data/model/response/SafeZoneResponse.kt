package com.lykos.pointage.data.model.response

import com.lykos.pointage.data.model.data.SafeZoneData


data class SafeZoneResponse(
    val success: Boolean,
    val message: String?,
    val count: Int,
    val data: List<SafeZoneData>
)