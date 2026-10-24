package sk.marek.den

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.PersistableBundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeatherRefreshDeviceTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    // Isolate fixture data from the user's weather and calendar preferences.
    private fun context(allowLocation: Boolean = true) = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("weather_refresh_test_$name", mode)
        override fun checkSelfPermission(permission: String): Int = when (permission) {
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION ->
                if (allowLocation) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> PackageManager.PERMISSION_DENIED
            else -> super.checkSelfPermission(permission)
        }
    }
    private val cached = Weather(1.0, 3, 0.0, 5.0, "Test city", 1, 1, 48.16, 17.18)
    private fun response() = JSONObject("""{
        "timezone":"Europe/Bratislava",
        "current":{"temperature_2m":12.5,"weather_code":2,"is_day":1,"time":1792836000},
        "daily":{"time":[1792792800],"weather_code":[61],"temperature_2m_min":[6.0],"temperature_2m_max":[14.0]}
    }""")
    private fun seed(context: Context) = context.getSharedPreferences("day", 0).edit().clear()
        .putBoolean("weather_enabled", true).putString("weather", weatherJson(cached)).commit()

    @Test fun noBackgroundLocationStillFetchesFreshWeatherForCachedPlace() = runBlocking {
        val context = context()
        seed(context)
        try {
            var requests = 0
            val (weather, message) = loadWeather(context, false, locate = { _, foreground ->
                assertFalse(foreground)
                null
            }, forecast = { lat, lon ->
                requests++
                assertEquals(cached.latitude, lat, 0.0)
                assertEquals(cached.longitude, lon, 0.0)
                response()
            })
            assertEquals(1, requests)
            assertEquals(12.5, weather!!.temperature, 0.0)
            assertTrue(weather.fetched > cached.fetched)
            assertEquals("Test city", weather.city)
            assertEquals("Počasie pre poslednú známu polohu", message)
            assertEquals(weather, cachedWeather(context))
        } finally { context.getSharedPreferences("day", 0).edit().clear().commit() }
    }

    @Test fun missingFirstLocationDoesNotSendAnInventedLocation() = runBlocking {
        val context = context()
        context.getSharedPreferences("day", 0).edit().clear().putBoolean("weather_enabled", true).commit()
        try {
            val (weather, message) = loadWeather(context, false, locate = { _, _ -> null }, forecast = { _, _ ->
                fail("No request without a known location")
                response()
            })
            assertNull(weather)
            assertNotNull(message)
        } finally { context.getSharedPreferences("day", 0).edit().clear().commit() }
    }

    @Test fun revokedLocationOrDisabledWeatherDoesNotUseSavedCoordinates() = runBlocking {
        for (allowed in listOf(false, true)) {
            val context = context(allowed)
            seed(context)
            if (allowed) context.getSharedPreferences("day", 0).edit().putBoolean("weather_enabled", false).commit()
            try {
                val (weather, _) = loadWeather(context, false, locate = { _, _ ->
                    fail("Location must not be accessed")
                    null
                }, forecast = { _, _ -> fail("Weather must not be requested"); response() })
                assertNull(weather)
            } finally { context.getSharedPreferences("day", 0).edit().clear().commit() }
        }
    }

    @Test fun failedFetchPreservesCacheAndCancellationPropagates() = runBlocking {
        val context = context()
        seed(context)
        try {
            val (weather, message) = loadWeather(context, false, locate = { _, _ -> null },
                forecast = { _, _ -> throw java.io.IOException("offline") })
            assertEquals(cached, weather)
            assertNotNull(message)
            assertEquals(cached, cachedWeather(context))
            try {
                loadWeather(context, false, locate = { _, _ -> null },
                    forecast = { _, _ -> throw CancellationException("stopped") })
                fail("Cancellation must propagate to JobService")
            } catch (_: CancellationException) { }
        } finally { context.getSharedPreferences("day", 0).edit().clear().commit() }
    }

    @Test fun activeWidgetHasPersistentNetworkJobWithoutResetOnRedraw() {
        val context = context()
        context.getSharedPreferences("day", 0).edit().putBoolean("lockscreen_overview", true).commit()
        val jobs = context.getSystemService(JobScheduler::class.java)
        val original = jobs.getPendingJob(DAY_PERIODIC_JOB)
        val component = ComponentName(context, DayJob::class.java)
        try {
            jobs.cancel(DAY_PERIODIC_JOB)
            ensureDaySchedule(context)
            val scheduled = jobs.getPendingJob(DAY_PERIODIC_JOB)!!
            assertTrue(scheduled.isPeriodic)
            assertTrue(scheduled.isPersisted)
            assertEquals(DAY_REFRESH_INTERVAL, scheduled.intervalMillis)
            assertEquals(JobInfo.NETWORK_TYPE_ANY, scheduled.networkType)
            assertNull(jobs.getPendingJob(LockscreenOverview.JOB))
            val marker = PersistableBundle().apply { putString("test_marker", "keep") }
            assertEquals(JobScheduler.RESULT_SUCCESS, jobs.schedule(JobInfo.Builder(DAY_PERIODIC_JOB, component)
                .setPeriodic(DAY_REFRESH_INTERVAL).setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(marker).build()))
            repeat(3) { ensureDaySchedule(context) }
            assertEquals("keep", jobs.getPendingJob(DAY_PERIODIC_JOB)!!.extras.getString("test_marker"))
        } finally {
            jobs.cancel(DAY_PERIODIC_JOB)
            context.getSharedPreferences("day", 0).edit().clear().commit()
            if (original != null) jobs.schedule(original) else ensureDaySchedule(target)
        }
    }
}
