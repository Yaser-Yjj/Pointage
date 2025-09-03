package com.lykos.pointage.utils

import android.annotation.SuppressLint

object TimeFormatter {

    @SuppressLint("DefaultLocale")
    fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}