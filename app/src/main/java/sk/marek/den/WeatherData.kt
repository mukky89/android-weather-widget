package sk.marek.den

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.math.round

data class Weather(val temperature: Double, val code: Int, val low: Double, val high: Double,
    val city: String, val modelTime: Long, val fetched: Long, val latitude: Double, val longitude: Double, val isDay: Boolean = true,
    val forecast: List<ForecastDay> = emptyList(), val timezone: String = java.time.ZoneId.systemDefault().id)
fun weatherText(code: Int): String = when (code) {
    0 -> "Jasno"; 1, 2 -> "Polooblačno"; 3 -> "Zamračené"; 45, 48 -> "Hmla"
    51, 53, 55, 56, 57 -> "Mrholenie"; 61, 63, 65, 66, 67 -> "Dážď"
    71, 73, 75, 77 -> "Sneženie"; 80, 81, 82 -> "Prehánky"; 85, 86 -> "Snehové prehánky"
    95, 96, 99 -> "Búrky"; else -> "Počasie"
}
fun Context.granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
fun Context.hasLocation() = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
fun roundedCoordinate(value: Double) = round(value * 100) / 100
fun weatherJson(w: Weather) = JSONObject().put("temperature", w.temperature).put("code", w.code).put("low", w.low).put("high", w.high)
    .put("city", w.city).put("modelTime", w.modelTime).put("fetched", w.fetched).put("latitude", w.latitude).put("longitude", w.longitude).put("isDay", w.isDay).put("forecast", forecastJson(w.forecast)).put("timezone", w.timezone).toString()
fun cachedWeather(context: Context): Weather? = runCatching {
    val j = JSONObject(context.getSharedPreferences("day", Context.MODE_PRIVATE).getString("weather", "")!!)
    Weather(j.getDouble("temperature"), j.getInt("code"), j.getDouble("low"), j.getDouble("high"), j.getString("city"),
        j.getLong("modelTime"), j.getLong("fetched"), j.getDouble("latitude"), j.getDouble("longitude"), j.optBoolean("isDay", true), cachedForecast(j.optJSONArray("forecast")), j.optString("timezone", java.time.ZoneId.systemDefault().id))
}.getOrNull()

@android.annotation.SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context, foreground: Boolean): Location? {
    if (!context.hasLocation() || (!foreground && !context.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))) return null
    val manager = context.getSystemService(LocationManager::class.java)
    if (!manager.isLocationEnabled) return null
    val providers = manager.getProviders(true)
    val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..300_000_000_000L }.maxByOrNull { it.elapsedRealtimeNanos }
    if (cached != null) return cached
    val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers } ?: return null
    return withTimeoutOrNull(10_000) { suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location -> if (continuation.isActive) continuation.resume(location) }
        } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
    } }
}

@Suppress("DEPRECATION")
suspend fun cityName(context: Context, lat: Double, lon: Double): String {
    if (!Geocoder.isPresent()) return "Aktuálna poloha"
    val geocoder = Geocoder(context, Locale.forLanguageTag("sk-SK"))
    val addresses = if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(5000) {
        suspendCancellableCoroutine<List<android.location.Address>> { continuation ->
            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) { if (continuation.isActive) continuation.resume(addresses) }
                override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(emptyList()) }
            })
        }
    } else runCatching { geocoder.getFromLocation(lat, lon, 1) }.getOrNull()
    return addresses?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: "Aktuálna poloha"
}
suspend fun loadWeather(context: Context, foreground: Boolean): Pair<Weather?, String?> {
    val prefs = context.getSharedPreferences("day", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("weather_enabled", false) || !context.hasLocation()) return null to "Povoľ polohu v appke Deň"
    val cached = cachedWeather(context)
    val location = currentLocation(context, foreground)
        ?: return cached to if (!foreground && !context.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) "Na presun mesta povoľ polohu vždy" else "Poloha nie je dostupná · obnov"
    val lat = roundedCoordinate(location.latitude); val lon = roundedCoordinate(location.longitude)
    return try {
        val connection = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto&timeformat=unixtime").openConnection() as HttpsURLConnection
        val data = try {
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
        val current = data.getJSONObject("current"); val daily = data.getJSONObject("daily")
        val timezone = data.getString("timezone")
        val forecast = parseDailyForecast(daily, timezone)
        val city = if (cached?.latitude == lat && cached.longitude == lon && cached.city != "Aktuálna poloha") cached.city else runCatching { cityName(context, lat, lon) }.getOrDefault("Aktuálna poloha")
        val weather = Weather(current.getDouble("temperature_2m"), current.getInt("weather_code"), daily.getJSONArray("temperature_2m_min").getDouble(0),
            daily.getJSONArray("temperature_2m_max").getDouble(0), city, current.getLong("time") * 1000, System.currentTimeMillis(), lat, lon, current.getInt("is_day") == 1, forecast, timezone)
        require(weather.temperature.isFinite() && weather.temperature in -100.0..70.0)
        prefs.edit().putString("weather", weatherJson(weather)).apply()
        weather to null
    } catch (_: Exception) { cached to if (cached == null) "Počasie nedostupné · skontroluj internet" else "Počasie sa neobnovilo · uložené údaje" }
}
