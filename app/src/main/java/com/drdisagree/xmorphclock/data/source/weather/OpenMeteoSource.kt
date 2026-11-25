package com.drdisagree.xmorphclock.data.source.weather

import android.content.Context
import com.drdisagree.xmorphclock.domain.model.HourlyPoint
import com.drdisagree.xmorphclock.domain.model.WeatherCity
import com.drdisagree.xmorphclock.domain.model.WeatherSnapshot
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

internal object OpenMeteoSource {

    private const val BASE = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODE_BASE = "https://geocoding-api.open-meteo.com/v1/search"
    private const val HOURS = 24
    private const val CITY_LIMIT = 8
    private const val SECOND_MS = 1000L

    suspend fun searchCities(query: String): List<WeatherCity> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val url = "$GEOCODE_BASE?name=$encoded&count=$CITY_LIMIT&language=$lang&format=json"
        val root = HttpJson.get(url) ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val obj = results.optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("name").trim()
            val lat = obj.optDouble("latitude", Double.NaN)
            val lon = obj.optDouble("longitude", Double.NaN)
            if (name.isEmpty() || lat.isNaN() || lon.isNaN()) return@mapNotNull null
            WeatherCity(
                name = name,
                region = obj.optString("admin1").trim(),
                country = obj.optString("country").trim(),
                latitude = lat,
                longitude = lon
            )
        }
    }

    suspend fun fetch(context: Context, latitude: Double, longitude: Double, metric: Boolean, location: String): WeatherSnapshot? {
        val units = if (metric) "" else "&temperature_unit=fahrenheit&wind_speed_unit=mph"
        val url = "$BASE?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day,uv_index" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=2" +
                "&hourly=temperature_2m,weather_code,is_day&forecast_hours=$HOURS&timeformat=unixtime&timezone=auto$units"
        val root = HttpJson.get(url) ?: return null
        val current = root.optJSONObject("current") ?: return null
        val daily = root.optJSONObject("daily")
        val isDay = current.optInt("is_day", 1) == 1
        val code = WeatherCodes.fromWmo(current.optInt("weather_code", -1), isDay)
        return WeatherSnapshot(
            temperature = current.optDouble("temperature_2m").roundToInt(),
            high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.roundToInt() ?: 0,
            low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.roundToInt() ?: 0,
            conditionCode = code,
            condition = WeatherCodes.condition(context, code),
            isDay = isDay,
            humidity = current.optInt("relative_humidity_2m"),
            wind = current.optDouble("wind_speed_10m").roundToInt(),
            windUnit = if (metric) "km/h" else "mph",
            location = location,
            metric = metric,
            timestamp = System.currentTimeMillis(),
            sunrises = millis(daily?.optJSONArray("sunrise")),
            sunsets = millis(daily?.optJSONArray("sunset")),
            uvIndex = if (current.has("uv_index") && !current.isNull("uv_index")) current.optDouble("uv_index").toFloat() else null,
            hourly = hourly(root.optJSONObject("hourly"))
        )
    }

    private fun millis(array: JSONArray?): List<Long> =
        array?.let { (0 until it.length()).map { index -> it.optLong(index) * SECOND_MS }.filter { value -> value > 0L } }.orEmpty()

    private fun hourly(hourly: JSONObject?): List<HourlyPoint> {
        val times = hourly?.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code")
        val days = hourly.optJSONArray("is_day")
        return (0 until times.length()).map { index ->
            val day = (days?.optInt(index, 1) ?: 1) == 1
            HourlyPoint(
                time = times.optLong(index) * SECOND_MS,
                temperature = temps.optDouble(index).roundToInt(),
                conditionCode = WeatherCodes.fromWmo(codes?.optInt(index, -1) ?: -1, day),
                isDay = day
            )
        }
    }
}
