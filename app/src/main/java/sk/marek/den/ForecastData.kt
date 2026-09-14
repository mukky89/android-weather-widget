package sk.marek.den

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ForecastDay(val date: LocalDate, val code: Int, val low: Double, val high: Double)
fun forecastDate(epochSeconds: Long, timezone: String): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of(timezone)).toLocalDate()
fun forecastToday(weather: Weather?, now: Instant = Instant.now()): LocalDate = now.atZone(runCatching { ZoneId.of(weather?.timezone ?: "") }.getOrDefault(ZoneId.systemDefault())).toLocalDate()
fun forecastSlots(days: List<ForecastDay>, today: LocalDate): List<ForecastDay?> = (0L..5L).map { offset -> days.firstOrNull { it.date == today.plusDays(offset) } }
fun forecastLabel(date: LocalDate, today: LocalDate): String = if (date == today) "DNES" else listOf("PO", "UT", "ST", "ŠT", "PI", "SO", "NE")[date.dayOfWeek.value-1]
fun parseDailyForecast(daily: JSONObject, timezone: String): List<ForecastDay> {
    val dates = daily.getJSONArray("time"); val codes = daily.getJSONArray("weather_code")
    val lows = daily.getJSONArray("temperature_2m_min"); val highs = daily.getJSONArray("temperature_2m_max")
    require(dates.length() > 0 && listOf(codes, lows, highs).all { it.length() == dates.length() })
    return (0 until dates.length()).map { index ->
        val low = lows.getDouble(index); val high = highs.getDouble(index)
        require(low.isFinite() && high.isFinite() && low in -100.0..70.0 && high in low..70.0)
        ForecastDay(forecastDate(dates.getLong(index), timezone), codes.getInt(index), low, high)
    }.sortedBy { it.date }
}
fun forecastJson(days: List<ForecastDay>) = JSONArray().apply {
    days.forEach { put(JSONObject().put("date",it.date.toString()).put("code",it.code).put("low",it.low).put("high",it.high)) }
}
fun cachedForecast(data: JSONArray?): List<ForecastDay> = if (data == null) emptyList() else (0 until data.length()).mapNotNull { index ->
    runCatching { val day=data.getJSONObject(index)
        val low=day.getDouble("low"); val high=day.getDouble("high")
        require(low.isFinite() && high.isFinite() && low in -100.0..70.0 && high in low..70.0)
        ForecastDay(LocalDate.parse(day.getString("date")),day.getInt("code"),low,high)
    }.getOrNull()
}
