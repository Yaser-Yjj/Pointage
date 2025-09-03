package com.lykos.pointage.data.model.data

data class ReportData(
    val reportId: Int,
    val userId: String,
    val imagesUploaded: Int,
    val imagePaths: List<String>
)