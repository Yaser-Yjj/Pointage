package com.lykos.pointage.ui.controller

import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.data.database.GeofenceDatabase
import com.lykos.pointage.data.database.entity.DailyInsideTime
import com.lykos.pointage.data.database.entity.LocationEvent
import com.lykos.pointage.utils.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryDialogController(
    private val activity: AppCompatActivity,
    private val database: GeofenceDatabase
) {

    companion object {
        private const val TAG = "HistoryDialogController"
    }

    fun showHistoryDialog() {
        activity.lifecycleScope.launch {
            try {
                val historyData = fetchHistoryData()
                val formattedHistory = formatHistoryData(historyData)
                showDialog(formattedHistory)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
            }
        }
    }

    private suspend fun fetchHistoryData(): HistoryData {
        val dailyRecords = database.dailyInsideTimeDao().getAll().take(7)
        val events = database.locationEventDao().getRecentEvents().take(5)
        return HistoryData(dailyRecords, events)
    }

    private fun formatHistoryData(historyData: HistoryData): String {
        val history = StringBuilder()

        if (historyData.dailyRecords.isNotEmpty()) {
            history.append("⏱️ Daily Time Inside Safe Zone:\n\n")
            historyData.dailyRecords.forEach { record ->
                val formatted = TimeFormatter.formatTime(record.totalTimeInside)
                history.append("${record.date}: $formatted\n")
            }
            history.append("\n")
        }

        if (historyData.events.isNotEmpty()) {
            history.append("🟢 Recent Sessions:\n\n")
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            historyData.events.forEach { event ->
                val exit = event.exitTime?.let { sdf.format(it) } ?: "Unknown"
                val enter = event.enterTime?.let { sdf.format(it) } ?: "Still away"
                val duration = TimeFormatter.formatTime(event.totalTimeInside)
                history.append("Exit: $exit\nEnter: $enter\nDuration: $duration\n\n")
            }
        }

        return if (history.isEmpty()) "No data yet." else history.toString()
    }

    private suspend fun showDialog(historyText: String) {
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(activity)
                .setTitle("History")
                .setMessage(historyText)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    data class HistoryData(
        val dailyRecords: List<DailyInsideTime>,
        val events: List<LocationEvent>
    )
}