package com.mrstu82.tvhome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class Day(val label: String, val code: Int, val max: Int, val min: Int)
data class Weather(val temp: Int, val code: Int, val days: List<Day>)

object OpenMeteo {
    fun fetch(c: City): String {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
            "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=3")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    fun parse(json: String): Weather {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current"); val d = o.getJSONObject("daily")
        val days = (1..2).map { i ->
            val date = LocalDate.parse(d.getJSONArray("time").getString(i))
            Day(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).uppercase(Locale.UK),
                d.getJSONArray("weather_code").getInt(i),
                Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
                Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)).toInt())
        }
        return Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), days)
    }

    fun icon(code: Int) = when (code) {
        0 -> R.drawable.ic_sun
        1, 2 -> R.drawable.ic_partly
        3, 45, 48 -> R.drawable.ic_cloud
        in 71..77, 85, 86 -> R.drawable.ic_snow
        else -> R.drawable.ic_rain
    }

    fun describe(code: Int) = when (code) {
        0 -> "Clear"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"
        in 80..82 -> "Showers"; 85, 86 -> "Snow showers"; else -> "Thunderstorms"
    }
}
