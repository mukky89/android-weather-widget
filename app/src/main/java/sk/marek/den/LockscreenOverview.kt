package sk.marek.den

import android.app.*
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.*
import android.provider.AlarmClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LockscreenOverview {
    const val CHANNEL = "day_overview"
    const val ID = 830
    const val JOB = 814
    fun enabled(context: Context) = context.getSharedPreferences("day", 0).getBoolean("lockscreen_overview", false)
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Prehľad dňa na zamknutej obrazovke", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tichý prehľad počasia, budíka, menín a Google/Outlook udalostí"
                setSound(null, null); enableVibration(false); setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun build(context: Context, state: DayState): Notification {
        val sk = Locale.forLanguageTag("sk-SK")
        val weather = state.weather.takeIf { context.hasLocation() }
        val weatherTitle = weather?.let { "${it.city} · ${String.format(sk, "%.0f °C", it.temperature)} · ${weatherText(it.code)}" }
            ?: "Deň · počasie zatiaľ nedostupné"
        val xiaomi = xiaomiAlarmText(context)
        val alarm = if (xiaomi != null) xiaomi.ifBlank { "nenastavený" } else
            context.getSystemService(AlarmManager::class.java).nextAlarmClock?.takeIf {
                android.os.Build.VERSION.SDK_INT < 31 || it.showIntent?.isActivity == true
            }?.let { Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE HH:mm", sk)) } ?: "nenastavený"
        val events = listOf(CalendarSource.GOOGLE, CalendarSource.OUTLOOK).map { source ->
            val event = state.agenda.events.firstOrNull { it.source == source }
            val label = if (source == CalendarSource.GOOGLE) "Google" else "Outlook"
            "$label: " + (state.agenda.error ?: event?.let { "${eventTime(it)} · ${it.title.take(100)}" } ?: "bez udalostí · 14 dní")
        }
        val compactEvents = listOf(CalendarSource.GOOGLE, CalendarSource.OUTLOOK).map { source ->
            val label = if (source == CalendarSource.GOOGLE) "Google" else "Outlook"
            "$label: " + (state.agenda.error ?: state.agenda.events.firstOrNull { it.source == source }?.title?.take(28) ?: "bez udalostí")
        }
        val compactTitle = weather?.let { "${it.city} · ${String.format(sk, "%.0f °C", it.temperature)} · ⏰ $alarm" } ?: "Deň · budík $alarm"
        val lines = mutableListOf(
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d. M.", sk)) + " · Meniny: ${nameday(context)}",
            "Budík: $alarm")
        lines.addAll(events)
        if (weather != null) {
            val time = Instant.ofEpochMilli(weather.modelTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. M. HH:mm"))
            lines.add("Open-Meteo · $time" + if (state.weatherError != null || System.currentTimeMillis() - weather.modelTime > 90 * 60000) " · staršie údaje" else "")
        }
        val open = PendingIntent.getActivity(context, ID, Intent(context, DayActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val refresh = PendingIntent.getBroadcast(context, 0, Intent(context, DayWidget::class.java).setAction(DayWidget.REFRESH)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_event_notification)
            .setContentTitle(compactTitle).setContentText(compactEvents.joinToString("\n"))
            .setStyle(Notification.BigTextStyle().setBigContentTitle(weatherTitle).bigText(lines.joinToString("\n")))
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS).setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
            .setShowWhen(false).setColor(0xFF315E9E.toInt())
            .addAction(Notification.Action.Builder(null, "Obnoviť", refresh).build())
            .apply {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                if (intent.resolveActivity(context.packageManager) != null) addAction(Notification.Action.Builder(null, "Budíky",
                    PendingIntent.getActivity(context, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            }.build()
    }
    fun update(context: Context, state: DayState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        schedule(context)
        if (!enabled(context)) { manager.cancel(ID); return }
        channel(context)
        if (!allowed(context)) return
        try { manager.notify(ID, build(context, state)) } catch (_: SecurityException) { /* Permission can be revoked between checks. */ }
    }
    fun schedule(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java)
        val widgets = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DayWidget::class.java))
        if (!enabled(context) || widgets.isNotEmpty()) { jobs.cancel(JOB); return }
        // The widget already refreshes every 30 minutes. Only add a job when the overview runs by itself.
        if (jobs.getPendingJob(JOB) == null) jobs.schedule(JobInfo.Builder(JOB, ComponentName(context, DayJob::class.java))
            .setPeriodic(30 * 60000L).setPersisted(true).build())
    }
}
