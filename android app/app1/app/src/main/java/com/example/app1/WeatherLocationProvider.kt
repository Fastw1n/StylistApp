package com.example.app1

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WeatherCoordinates(
    val latitude: Double,
    val longitude: Double,
    val placeName: String
)

class WeatherLocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun currentCoordinates(): WeatherCoordinates {
        if (!hasLocationPermission(context)) {
            throw SecurityException("Нет разрешения на геопозицию")
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            throw IllegalStateException("Геопозиция выключена")
        }

        val lastLocation = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (lastLocation != null && System.currentTimeMillis() - lastLocation.time < LOCATION_MAX_AGE_MS) {
            return lastLocation.toWeatherCoordinates()
        }

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location.toWeatherCoordinates())
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }

            handler.post {
                runCatching {
                    locationManager.requestSingleUpdate(providers.first(), listener, Looper.getMainLooper())
                }.onFailure { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            }

            handler.postDelayed({
                locationManager.removeUpdates(listener)
                if (continuation.isActive) {
                    lastLocation?.let {
                        continuation.resume(it.toWeatherCoordinates())
                    } ?: continuation.resumeWithException(
                        IllegalStateException("Не удалось определить геопозицию")
                    )
                }
            }, LOCATION_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }

    private fun Location.toWeatherCoordinates(): WeatherCoordinates =
        WeatherCoordinates(
            latitude = latitude,
            longitude = longitude,
            placeName = "Рядом с вами"
        )

    companion object {
        private const val LOCATION_TIMEOUT_MS = 10_000L
        private const val LOCATION_MAX_AGE_MS = 30 * 60 * 1000L

        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }
    }
}
