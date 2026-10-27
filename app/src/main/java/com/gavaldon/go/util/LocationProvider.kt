package com.gavaldon.go.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

/** RF-05. Ubicación puntual sin dependencias de Google Play Services. Nada se guarda ni se envía. */
class LocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Pide una sola lectura. Devuelve false si no hay proveedor disponible o falta permiso. */
    @SuppressLint("MissingPermission")
    fun requestOnce(onLocation: (Location) -> Unit): Boolean {
        stop()
        if (!hasPermission()) return false
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return false
        }
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                stop()
                onLocation(location)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Requerido en Android < 11")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        listener = l
        return try {
            manager.requestLocationUpdates(provider, 0L, 0f, l, Looper.getMainLooper())
            true
        } catch (e: SecurityException) {
            listener = null
            false
        }
    }

    fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }
}
