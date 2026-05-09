package com.example.app1

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherSnapshot(
    val placeName: String,
    val temperatureC: Double?,
    val apparentTemperatureC: Double?,
    val weatherCode: Int?,
    val windSpeedKmh: Double?
) {
    fun temperatureText(): String =
        temperatureC?.let { "${it.toInt()}°C" } ?: "--°C"

    fun detailsText(): String {
        val feelsLike = apparentTemperatureC?.let { "ощущается ${it.toInt()}°C" }
        val wind = windSpeedKmh?.let { "ветер ${it.toInt()} км/ч" }
        return listOfNotNull(weatherDescription(weatherCode), feelsLike, wind).joinToString(", ")
    }
}

data class OpenMeteoForecastResponse(
    val current: OpenMeteoCurrentWeather?
)

data class OpenMeteoCurrentWeather(
    @SerializedName("temperature_2m")
    val temperatureC: Double?,
    @SerializedName("apparent_temperature")
    val apparentTemperatureC: Double?,
    @SerializedName("weather_code")
    val weatherCode: Int?,
    @SerializedName("wind_speed_10m")
    val windSpeedKmh: Double?
)

data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoPlace> = emptyList()
)

data class OpenMeteoPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
) {
    fun displayName(): String =
        listOf(name, admin1, country)
            .filterNot { it.isNullOrBlank() }
            .distinct()
            .joinToString(", ")
}

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code,wind_speed_10m",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastResponse
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "ru",
        @Query("format") format: String = "json"
    ): OpenMeteoGeocodingResponse
}

object WeatherApiClient {
    val forecastApi: OpenMeteoForecastApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoForecastApi::class.java)
    }

    val geocodingApi: OpenMeteoGeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoGeocodingApi::class.java)
    }
}

fun weatherDescription(code: Int?): String {
    return when (code) {
        0 -> "ясно"
        1, 2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55, 56, 57 -> "морось"
        61, 63, 65, 66, 67 -> "дождь"
        71, 73, 75, 77 -> "снег"
        80, 81, 82 -> "ливень"
        85, 86 -> "снегопад"
        95, 96, 99 -> "гроза"
        else -> "погода"
    }
}
