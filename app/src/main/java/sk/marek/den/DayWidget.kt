package sk.marek.den

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.AlarmClock
import android.widget.RemoteViews
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DayWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, REFRESH)) {
            val pending = goAsync()
            val app = context.applicationContext as DayApp
            app.scope.launch { try { app.refreshAgenda(); scheduleDay(context) } finally { pending.finish() } }
        }
    }
    override fun onDisabled(context: Context) {
        val jobs = context.getSystemService(android.app.job.JobScheduler::class.java)
        jobs.cancel(811); jobs.cancel(812)
        LockscreenOverview.schedule(context)
    }
    companion object {
        const val REFRESH = "sk.marek.den.REFRESH"
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, DayWidget::class.java), null, null)
        }
        fun updateAll(context: Context, state: DayState) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DayWidget::class.java)).forEach { id -> manager.updateAppWidget(id, views(context, state)) }
            LockscreenOverview.update(context, state)
        }
        fun views(context: Context, state: DayState): RemoteViews = RemoteViews(context.packageName, R.layout.day_widget).apply {
            val open = PendingIntent.getActivity(context, 0, Intent(context, DayActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.day_root, open)
            setOnClickPendingIntent(R.id.day_settings, open)
            // Some Xiaomi calendar maintenance alarms also use setAlarmClock; these are not wake-up alarms.
            val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.takeIf {
                android.os.Build.VERSION.SDK_INT < 31 || it.showIntent?.isActivity == true
            }
            val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            val alarmTarget = if (alarmIntent.resolveActivity(context.packageManager) != null)
                PendingIntent.getActivity(context, 200, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                else alarm?.showIntent
            val xiaomiTime = xiaomiAlarmText(context)
            val alarmText = xiaomiTime?.takeIf { it.isNotBlank() } ?: alarm?.takeIf { xiaomiTime == null }?.let { Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.forLanguageTag("sk-SK"))) }
                ?: if (alarmTarget != null) "Nastaviť budík" else "Budíky nedostupné"
            setTextViewText(R.id.day_alarm, alarmText)
            setContentDescription(R.id.day_alarm, if (!xiaomiTime.isNullOrBlank()) "Najbližší budík: $xiaomiTime. Otvoriť budíky." else if (alarm != null && xiaomiTime == null) "Najbližší budík: " +
                Instant.ofEpochMilli(alarm.triggerTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE d. M. HH:mm", Locale.forLanguageTag("sk-SK"))) + ". Otvoriť budíky."
                else alarmText)
            setOnClickPendingIntent(R.id.day_alarm, alarmTarget ?: open)
            setOnClickPendingIntent(R.id.day_clock, alarmTarget ?: open)
            setOnClickPendingIntent(R.id.day_refresh, PendingIntent.getBroadcast(context, 0,
                Intent(context, DayWidget::class.java).setAction(REFRESH).addFlags(Intent.FLAG_RECEIVER_FOREGROUND), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setTextViewText(R.id.day_names, nameday(context))
            setContentDescription(R.id.day_names, "Meniny dnes: ${nameday(context)}")
            val weather = state.weather.takeIf { context.hasLocation() }
            val forecastTarget = PendingIntent.getActivity(context, 201, Intent(context, ForecastActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            for (id in listOf(R.id.day_weather_card, R.id.day_city, R.id.day_temperature, R.id.day_weather_icon, R.id.day_weather, R.id.day_forecast)) {
                setOnClickPendingIntent(id, forecastTarget)
            }
            setContentDescription(R.id.day_weather_card, "Otvoriť podrobnú predpoveď na Windy.com")
            val forecastIds = listOf(
                intArrayOf(R.id.forecast_0, R.id.forecast_day_0, R.id.forecast_icon_0, R.id.forecast_temp_0),
                intArrayOf(R.id.forecast_1, R.id.forecast_day_1, R.id.forecast_icon_1, R.id.forecast_temp_1),
                intArrayOf(R.id.forecast_2, R.id.forecast_day_2, R.id.forecast_icon_2, R.id.forecast_temp_2),
                intArrayOf(R.id.forecast_3, R.id.forecast_day_3, R.id.forecast_icon_3, R.id.forecast_temp_3),
                intArrayOf(R.id.forecast_4, R.id.forecast_day_4, R.id.forecast_icon_4, R.id.forecast_temp_4),
                intArrayOf(R.id.forecast_5, R.id.forecast_day_5, R.id.forecast_icon_5, R.id.forecast_temp_5))
            val today = forecastToday(weather)
            val forecast = forecastSlots(weather?.forecast.orEmpty(), today)
            forecastIds.forEachIndexed { index, ids ->
                val day = forecast[index]
                val date = today.plusDays(index.toLong())
                setOnClickPendingIntent(ids[0], forecastTarget)
                setTextViewText(ids[1], forecastLabel(date, today))
                setImageViewResource(ids[2], day?.let { weatherIcon(it.code, true) } ?: R.drawable.weather_unknown)
                setTextViewText(ids[3], day?.let { String.format(Locale.forLanguageTag("sk-SK"), "%.0f°/%.0f°", it.high, it.low) } ?: "— / —")
                setContentDescription(ids[0], "${date.format(DateTimeFormatter.ofPattern("d. M."))}: " + (day?.let {
                    "${weatherText(it.code)}, maximum ${it.high} °C, minimum ${it.low} °C"
                } ?: "Predpoveď nie je dostupná") + ". Otvoriť predpoveď na Windy.com.")
            }
            setImageViewResource(R.id.day_weather_icon, weather?.let { weatherIcon(it.code, it.isDay) } ?: R.drawable.weather_unknown)
            setContentDescription(R.id.day_weather_icon, weather?.let { weatherText(it.code) } ?: "Počasie zatiaľ nie je dostupné")
            if (weather == null) {
                setTextViewText(R.id.day_temperature, "— °C")
                setTextViewText(R.id.day_weather, state.weatherError ?: "Nastav počasie podľa polohy")
                setTextViewText(R.id.day_city, "Počasie podľa GPS")
                setTextViewText(R.id.day_weather_time, "Ťukni na nastavenia")
            } else {
                fun degree(value: Double) = String.format(Locale.forLanguageTag("sk-SK"), "%.0f°", value)
                setTextViewText(R.id.day_temperature, degree(weather.temperature))
                setTextViewText(R.id.day_weather, "${degree(weather.high)} / ${degree(weather.low)} · ${weatherText(weather.code)}")
                setTextViewText(R.id.day_city, weather.city)
                val age = System.currentTimeMillis() - weather.modelTime
                val time = Instant.ofEpochMilli(weather.modelTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (age > 86400000) "d.M. HH:mm" else "HH:mm"))
                setTextViewText(R.id.day_weather_time, state.weatherError ?: "Open-Meteo · ${if (age > 90 * 60000) "staršie " else ""}$time")
            }
            val hidden = context.getSharedPreferences("day", Context.MODE_PRIVATE).getStringSet("hidden_calendars", emptySet()).orEmpty()
            val rows = listOf(Triple(CalendarSource.GOOGLE, R.id.google_title, R.id.google_time), Triple(CalendarSource.OUTLOOK, R.id.outlook_title, R.id.outlook_time))
            for ((source, title, time) in rows) {
                val leftId = if (source == CalendarSource.GOOGLE) R.id.google_event_area else R.id.outlook_event_area
                val rightId = if (source == CalendarSource.GOOGLE) R.id.google_day_area else R.id.outlook_day_area
                val labelId = if (source == CalendarSource.GOOGLE) R.id.google_label else R.id.outlook_label
                val hintId = if (source == CalendarSource.GOOGLE) R.id.google_day_hint else R.id.outlook_day_hint
                val countId = if (source == CalendarSource.GOOGLE) R.id.google_count else R.id.outlook_count
                val addId = if (source == CalendarSource.GOOGLE) R.id.google_add else R.id.outlook_add
                setOnClickPendingIntent(addId, PendingIntent.getActivity(context, 100 + source.ordinal,
                    Intent(context, CalendarActionActivity::class.java).putExtra("source", source.name).putExtra("insert", true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                val summary = calendarSummary(state.agenda, source)
                val event = summary.event
                val available = state.agenda.calendars.any { it.source == source && it.id.toString() !in hidden }
                setTextViewText(title, state.agenda.error ?: event?.title ?: if (available) "Bez udalostí · 14 dní" else "Pripojiť kalendár")
                setTextViewText(time, event?.let { eventTime(it) } ?: "Dnes")
                setTextViewText(countId, if (state.agenda.error != null || !available) "— udalostí" else eventCountLabel(summary.count))
                val dayTarget = PendingIntent.getActivity(context, 300 + source.ordinal,
                    Intent(context, CalendarActionActivity::class.java).putExtra("source", source.name).putExtra("date", summary.date.toString()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val target = if (event == null) { if (available) dayTarget else open } else PendingIntent.getActivity(context, source.ordinal + 1,
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                        .setIdentifier("${event.id}:${event.begin}")
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
                        .also { intent ->
                            // Google Calendar opens Android provider IDs, including exported Outlook events.
                            val google = Intent(intent).setPackage("com.google.android.calendar")
                            if (google.resolveActivity(context.packageManager) != null) intent.setPackage("com.google.android.calendar")
                        },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                for (id in listOf(leftId, labelId, title)) setOnClickPendingIntent(id, target)
                for (id in listOf(rightId, time, countId, hintId)) setOnClickPendingIntent(id, dayTarget)
                setContentDescription(leftId, event?.let { "Otvoriť udalosť: ${it.title}" } ?: if (available) "Otvoriť dnešný deň" else "Pripojiť kalendár")
                setContentDescription(rightId, "${source.label}: otvoriť celý deň ${summary.date.format(DateTimeFormatter.ofPattern("d. M. yyyy"))}, ${eventCountLabel(summary.count)}")
            }
        }
    }
}

fun weatherIcon(code: Int, isDay: Boolean): Int = when (code) {
    0 -> if (isDay) R.drawable.weather_sun else R.drawable.weather_night
    1, 2 -> if (isDay) R.drawable.weather_partly else R.drawable.weather_night_cloud
    3 -> R.drawable.weather_cloud
    45, 48 -> R.drawable.weather_fog
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.weather_rain
    71, 73, 75, 77, 85, 86 -> R.drawable.weather_snow
    95, 96, 99 -> R.drawable.weather_storm
    else -> R.drawable.weather_unknown
}
