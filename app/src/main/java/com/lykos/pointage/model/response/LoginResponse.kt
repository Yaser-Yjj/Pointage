package com.lykos.pointage.model.response

data class LoginResponse(
    val message: String,
    val userId: String? = null
)