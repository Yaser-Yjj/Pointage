package com.lykos.pointage.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.lykos.pointage.GeofenceApplication
import com.lykos.pointage.MainActivity
import com.lykos.pointage.receiver.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import androidx.core.content.edit

class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val GEOFENCE_ID = "main_geofence"
        private const val AWAY_NOTIFICATION_ID = 2001
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LATITUDE = "geofence_latitude"
        private const val KEY_LONGITUDE = "geofence_longitude"
        private const val KEY_RADIUS = "geofence_radius"

        // Default values - will be replaced with current location
        private const val DEFAULT_GEOFENCE_LATITUDE = 37.7749
        private const val DEFAULT_GEOFENCE_LONGITUDE = -122.4194
        private const val DEFAULT_GEOFENCE_RADIUS_METERS = 2f

        // Test coordinates - larger radius for easier testing
        private const val TEST_GEOFENCE_RADIUS_METERS = 2f
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun setupGeofencesWithCurrentLocation() {
        Log.d(TAG, "🔍 Getting current location for geofence setup...")

        if (!hasLocationPermission()) {
            Log.e(TAG, "❌ Location permission not granted")
            return
        }

        if (!isLocationEnabled()) {
            Log.e(TAG, "❌ Location services are disabled")
            return
        }

        getCurrentLocationAndSetupGeofence()
    }

    fun setupGeofences() {
        Log.d(TAG, "Setting up geofences with saved/default location...")

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        if (!isLocationEnabled()) {
            Log.e(TAG, "Location services are disabled")
            return
        }

        // Use saved coordinates or defaults
        val latitude = sharedPrefs.getFloat(KEY_LATITUDE, DEFAULT_GEOFENCE_LATITUDE.toFloat()).toDouble()
        val longitude = sharedPrefs.getFloat(KEY_LONGITUDE, DEFAULT_GEOFENCE_LONGITUDE.toFloat()).toDouble()
        val radius = sharedPrefs.getFloat(KEY_RADIUS, DEFAULT_GEOFENCE_RADIUS_METERS)

        Log.d(TAG, "Using geofence coordinates: Lat=$latitude, Lng=$longitude, Radius=${radius}m")

        // Remove existing geofences first
        removeGeofences {
            // Add new geofences after removal
            addGeofences(latitude, longitude, radius)
        }
    }

    private fun getCurrentLocationAndSetupGeofence() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Fine location permission not granted")
            return
        }

        Log.d(TAG, "📍 Requesting current location...")

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d(TAG, "✅ Got current location: ${location.latitude}, ${location.longitude}")
                    Log.d(TAG, "📊 Location accuracy: ${location.accuracy} meters")

                    // Save current location as geofence center
                    sharedPrefs.edit {
                        putFloat(KEY_LATITUDE, location.latitude.toFloat())
                            .putFloat(KEY_LONGITUDE, location.longitude.toFloat())
                            .putFloat(KEY_RADIUS, TEST_GEOFENCE_RADIUS_METERS)
                    }

                    Log.d(TAG, "💾 Saved geofence center: ${location.latitude}, ${location.longitude}")

                    // Remove existing geofences first
                    removeGeofences {
                        // Add new geofences with current location
                        addGeofences(location.latitude, location.longitude, TEST_GEOFENCE_RADIUS_METERS)
                    }
                } else {
                    Log.e(TAG, "❌ Could not get current location - location is null")
                    // Try to get fresh location
                    requestFreshLocation()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to get current location", exception)
                requestFreshLocation()
            }
    }

    private fun requestFreshLocation() {
        Log.d(TAG, "🔄 Requesting fresh location...")

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            5000 // 5 seconds
        ).build()

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d(TAG, "✅ Got fresh location: ${location.latitude}, ${location.longitude}")

                    // Save and setup geofence
                    sharedPrefs.edit()
                        .putFloat(KEY_LATITUDE, location.latitude.toFloat())
                        .putFloat(KEY_LONGITUDE, location.longitude.toFloat())
                        .putFloat(KEY_RADIUS, TEST_GEOFENCE_RADIUS_METERS)
                        .apply()

                    removeGeofences {
                        addGeofences(location.latitude, location.longitude, TEST_GEOFENCE_RADIUS_METERS)
                    }

                    // Stop location updates
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun addGeofences(latitude: Double, longitude: Double, radius: Float) {
        Log.d(TAG, "🎯 Creating geofence at: Lat=$latitude, Lng=$longitude, Radius=${radius}m")

        val geofence = createGeofence(latitude, longitude, radius)
        val geofencingRequest = createGeofencingRequest(geofence)

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "❌ Fine location permission not granted")
                return
            }

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ GEOFENCE ADDED SUCCESSFULLY!")
                    Log.d(TAG, "📍 Center: $latitude, $longitude")
                    Log.d(TAG, "📏 Radius: ${radius} meters")
                    Log.d(TAG, "🚨 Now walk ${radius + 5} meters away to test exit!")

                    // Show immediate notification
                    showGeofenceSetupNotification(latitude, longitude, radius)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ FAILED TO ADD GEOFENCE!")
                    handleGeofenceError(exception, "add")
                }
        } catch (securityException: SecurityException) {
            Log.e(TAG, "❌ Security exception when adding geofence", securityException)
        }
    }

    private fun showGeofenceSetupNotification(lat: Double, lng: Double, radius: Float) {
        // Ensure notification channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GeofenceApplication.NOTIFICATION_CHANNEL_ID,
                GeofenceApplication.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, GeofenceApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🎯 Geofence Active!")
            .setContentText("Center: $lat, $lng | Radius: ${radius}m | Walk ${radius + 10}m away to test!")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(3001, notification)
    }

    fun removeGeofences(onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "🗑️ Removing existing geofences...")
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Geofences removed successfully")
                onComplete?.invoke()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to remove geofences", exception)
                onComplete?.invoke() // Still call onComplete even if removal fails
            }
    }

    private fun handleGeofenceError(exception: Exception, operation: String) {
        when (exception) {
            is ApiException -> {
                val errorMessage = when (exception.statusCode) {
                    GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                        "Geofence service is not available. Check if location services are enabled and Google Play Services is updated."
                    }
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> {
                        "Too many geofences registered. Maximum is 100 per app."
                    }
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> {
                        "Too many pending intents registered for geofences."
                    }
                    1000 -> {
                        "Location services unavailable. Please enable high accuracy location mode and ensure Google Play Services is updated."
                    }
                    else -> {
                        "Geofence error: ${exception.statusCode} - ${exception.message}"
                    }
                }
                Log.e(TAG, "❌ Failed to $operation geofence: $errorMessage")
            }
            else -> {
                Log.e(TAG, "❌ Failed to $operation geofence", exception)
            }
        }
    }

    private fun createGeofence(latitude: Double, longitude: Double, radius: Float): Geofence {
        Log.d(TAG, "🏗️ Building geofence with:")
        Log.d(TAG, "   📍 Latitude: $latitude")
        Log.d(TAG, "   📍 Longitude: $longitude")
        Log.d(TAG, "   📏 Radius: $radius meters")
        Log.d(TAG, "   🔄 Transitions: ENTER + EXIT")
        Log.d(TAG, "   ⏱️ Loitering delay: 1 second")

        return Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(1000) // 1 second delay
            .build()
    }

    private fun createGeofencingRequest(geofence: Geofence): GeofencingRequest {
        Log.d(TAG, "📋 Creating geofencing request with NO initial trigger")
        return GeofencingRequest.Builder()
            .setInitialTrigger(0) // No initial trigger - wait for actual movement
            .addGeofence(geofence)
            .build()
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "🔐 Location permissions - Fine: $fineLocation, Coarse: $coarseLocation")
        return fineLocation && coarseLocation
    }

    private fun isLocationEnabled(): Boolean {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        Log.d(TAG, "📡 Location providers - GPS: $isGpsEnabled, Network: $isNetworkEnabled")
        return isGpsEnabled || isNetworkEnabled
    }

    fun getCurrentGeofenceInfo(): String {
        val latitude = sharedPrefs.getFloat(KEY_LATITUDE, 0f)
        val longitude = sharedPrefs.getFloat(KEY_LONGITUDE, 0f)
        val radius = sharedPrefs.getFloat(KEY_RADIUS, TEST_GEOFENCE_RADIUS_METERS)

        return if (latitude != 0f && longitude != 0f) {
            "Geofence Center:\nLat: $latitude\nLng: $longitude\nRadius: ${radius}m\n\nWalk ${radius + 5}m away to trigger EXIT"
        } else {
            "No geofence set yet.\nStart tracking to set geofence at your current location."
        }
    }

    fun showAwayNotification() {
        Log.d(TAG, "🔴 Showing AWAY notification")

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GeofenceApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🔴 OUTSIDE THE FENCE!")
            .setContentText("Timer is ON - You left the designated area")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(AWAY_NOTIFICATION_ID, notification)
    }

    fun cancelAwayNotification() {
        Log.d(TAG, "🟢 Cancelling AWAY notification")
        notificationManager.cancel(AWAY_NOTIFICATION_ID)
    }
}
