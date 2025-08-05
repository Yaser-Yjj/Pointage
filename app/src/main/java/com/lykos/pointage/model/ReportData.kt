package com.lykos.pointage.model

data class ReportData(
    val reportId: Int,
    val userId: String,
    val imagesUploaded: Int,
    val imagePaths: List<String>
)