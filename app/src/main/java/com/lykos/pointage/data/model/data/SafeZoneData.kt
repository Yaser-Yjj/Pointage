package com.lykos.pointage.data.model.data

data class SafeZoneData(
    val id: Int,
    val name: String = "Unnamed Zone",
    val latitude: Double,
    val longitude: Double,
    val radius: Float
)