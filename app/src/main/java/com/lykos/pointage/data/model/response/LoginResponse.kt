package com.lykos.pointage.data.model.response

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("user_id")
    val userId: String? = null
)