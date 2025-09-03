package com.lykos.pointage.ui.controller

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.lykos.pointage.R
import com.lykos.pointage.data.model.data.SafeZoneData
import com.lykos.pointage.utils.GeofenceManager

class GeofenceMapController(
    private val activity: AppCompatActivity,
    private val geofenceManager: GeofenceManager,
    private val fusedLocationClient: FusedLocationProviderClient
) : OnMapReadyCallback {

    companion object {
        private const val DEFAULT_ZOOM = 15f
    }

    private lateinit var googleMap: GoogleMap
    private var geofenceCircle: Circle? = null

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        configureMapSettings()
        enableLocationIfPermitted()
    }

    private fun configureMapSettings() {
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = false
            isScrollGesturesEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
        }

        // Disable map interactions
        googleMap.setOnMapClickListener(null)
        googleMap.setOnMapLongClickListener(null)
    }

    fun updateMapWithZone(zone: SafeZoneData, isInside: Boolean) {
        if (::googleMap.isInitialized) {
            val location = LatLng(zone.latitude, zone.longitude)
            showGeofenceOnMap(location, zone.radius, isInside)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))
        }
    }

    fun showGeofenceOnMap(location: LatLng, radius: Float, isInside: Boolean) {
        googleMap.clear()

        val strokeColor = if (isInside) {
            ContextCompat.getColor(activity, R.color.green_stroke)
        } else {
            ContextCompat.getColor(activity, R.color.red_stroke)
        }

        val fillColor = if (isInside) {
            ContextCompat.getColor(activity, R.color.green_fill)
        } else {
            ContextCompat.getColor(activity, R.color.red_fill)
        }

        geofenceCircle = googleMap.addCircle(
            CircleOptions()
                .center(location)
                .radius(radius.toDouble())
                .strokeColor(strokeColor)
                .fillColor(fillColor)
                .strokeWidth(3f)
        )
    }

    fun updateGeofenceCircleColor(isInside: Boolean) {
        geofenceCircle?.let { circle ->
            val strokeColor = if (isInside) {
                ContextCompat.getColor(activity, R.color.green_stroke)
            } else {
                ContextCompat.getColor(activity, R.color.red_stroke)
            }
            val fillColor = if (isInside) {
                ContextCompat.getColor(activity, R.color.green_fill)
            } else {
                ContextCompat.getColor(activity, R.color.red_fill)
            }
            circle.strokeColor = strokeColor
            circle.fillColor = fillColor
        }
    }

    fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(activity, "Permission denied.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.latitude, it.longitude),
                        DEFAULT_ZOOM
                    )
                )
            } ?: Toast.makeText(activity, "Location unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun enableLocationIfPermitted() {
        if (::googleMap.isInitialized &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }
}