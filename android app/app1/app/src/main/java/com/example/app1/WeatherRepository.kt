package com.example.app1

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WeatherRepository {

    suspend fun loadCurrentWeather(context: Context): WeatherSnapshot? {
        if (!WeatherPreferences.isEnabled(context)) return null

        val coordinates = when (WeatherPreferences.getMode(context)) {
            WeatherPreferences.MODE_CITY -> resolveCity(context)
            else -> WeatherLocationProvider(context.applicationContext).currentCoordinates()
        }

        val weather = withContext(Dispatchers.IO) {
            WeatherApiClient.forecastApi.getCurrentWeather(
                latitude = coordinates.latitude,
                longitude = coordinates.longitude
            )
        }.current

        return WeatherSnapshot(
            placeName = coordinates.placeName,
            temperatureC = weather?.temperatureC,
            apparentTemperatureC = weather?.apparentTemperatureC,
            weatherCode = weather?.weatherCode,
            windSpeedKmh = weather?.windSpeedKmh
        )
    }

    private suspend fun resolveCity(context: Context): WeatherCoordinates {
        val city = WeatherPreferences.getCity(context).trim()
        if (city.isBlank()) {
            throw IllegalStateException("Укажите город в настройках")
        }

        val place = withContext(Dispatchers.IO) {
            WeatherApiClient.geocodingApi.searchCity(city).results.firstOrNull()
        } ?: throw IllegalStateException("Город не найден")

        return WeatherCoordinates(
            latitude = place.latitude,
            longitude = place.longitude,
            placeName = place.displayName()
        )
    }
}
