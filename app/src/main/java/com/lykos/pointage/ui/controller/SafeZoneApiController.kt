package com.lykos.pointage.ui.controller

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.data.model.data.SafeZoneData
import com.lykos.pointage.data.model.response.SafeZoneResponse
import com.lykos.pointage.service.RetrofitClient
import com.lykos.pointage.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class SafeZoneApiController(
    private val activity: AppCompatActivity,
    private val viewModel: MainViewModel
) {

    companion object {
        private const val TAG = "SafeZoneApiController"
    }

    fun fetchSafeZones(userId: String) {
        activity.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getSafeZones(userId)
                handleApiResponse(response)
            } catch (e: Exception) {
                handleNetworkError(e)
            }
        }
    }

    private suspend fun handleApiResponse(response: Response<SafeZoneResponse>) {
        Log.d(TAG, "API Response: $response")

        if (response.isSuccessful && response.body()?.success == true) {
            val zones = response.body()?.data.orEmpty()
            logZoneData(zones)

            if (zones.isEmpty()) {
                showNoZonesMessage()
            } else {
                viewModel.setSafeZones(zones)
            }
        } else {
            handleApiFailure(response.message())
        }
    }

    private fun logZoneData(zones: List<SafeZoneData>) {
        Log.d(TAG, "Fetched zones count: ${zones.size}")
        zones.forEach { zone ->
            Log.d(TAG, "Zone: lat=${zone.latitude}, lng=${zone.longitude}, radius=${zone.radius}")
        }
    }

    private suspend fun showNoZonesMessage() {
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, "No safe zones found.", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun handleApiFailure(message: String) {
        Log.e(TAG, "API failed: $message")
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, "Failed to load safe zones.", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun handleNetworkError(e: Exception) {
        Log.e(TAG, "Network error", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, "Network error.", Toast.LENGTH_LONG).show()
        }
    }
}