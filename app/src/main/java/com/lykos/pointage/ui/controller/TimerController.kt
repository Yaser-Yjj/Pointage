package com.lykos.pointage.ui.controllers

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lykos.pointage.R
import com.lykos.pointage.utils.PreferencesManager
import com.lykos.pointage.utils.TimeFormatter

class TimerController(
    private val statusTextView: TextView,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "TimerController"
    }

    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false

    fun updateTimerDisplay() {
        val isOutsideZone = preferencesManager.getState() // true = outside, false = inside
        Log.d(TAG, "updateTimerDisplay called. isOutsideZone: $isOutsideZone")

        if (!isOutsideZone) {
            startTimer()
        } else {
            stopTimer()
        }
    }

    private fun startTimer() {
        if (isTimerRunning) return

        Log.d(TAG, "Starting timer")
        isTimerRunning = true

        setupActiveTimerUI()
        createTimerRunnable()
        startTimerLoop()
    }

    private fun stopTimer() {
        if (!isTimerRunning) return

        Log.d(TAG, "Stopping timer")
        isTimerRunning = false

        cleanupTimer()
        setupInactiveTimerUI()
    }

    private fun setupActiveTimerUI() {
        statusTextView.setBackgroundResource(R.drawable.bg_status_banner_active)
        statusTextView.setTextColor(ContextCompat.getColor(statusTextView.context, R.color.white))
    }

    private fun setupInactiveTimerUI() {
        statusTextView.setBackgroundResource(R.drawable.bg_status_banner)
        statusTextView.setTextColor(ContextCompat.getColor(statusTextView.context, R.color.white))
        statusTextView.text = "🔴 Outside Zone"
    }

    private fun createTimerRunnable() {
        timerRunnable = Runnable {
            if (isTimerRunning) {
                updateTimerText()
                scheduleNextUpdate()
            }
        }
    }

    private fun startTimerLoop() {
        timerHandler = Handler(Looper.getMainLooper())
        timerHandler?.post(timerRunnable!!)
    }

    @SuppressLint("SetTextI18n")
    private fun updateTimerText() {
        val accumulatedTime = preferencesManager.getAccumulatedTimeInside()
        val sessionStartTime = preferencesManager.getLastEnterTimestamp()
        val currentSessionDuration = System.currentTimeMillis() - sessionStartTime
        val totalDisplayTime = accumulatedTime + currentSessionDuration
        val formattedTime = TimeFormatter.formatTime(totalDisplayTime)
        statusTextView.text = "⏱️ Timer $formattedTime"
    }

    private fun scheduleNextUpdate() {
        timerHandler?.postDelayed(timerRunnable!!, 1000)
    }

    private fun cleanupTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
        timerHandler = null
        timerRunnable = null
    }

    fun cleanup() {
        stopTimer()
    }
}