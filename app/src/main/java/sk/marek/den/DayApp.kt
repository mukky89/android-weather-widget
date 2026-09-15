package sk.marek.den

import android.app.Application
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayState(val agenda: Agenda = Agenda(error = "Načítavam kalendáre…"), val weather: Weather? = null,
    val weatherError: String? = null, val loading: Boolean = false)
class DayApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var startupRefresh: Job? = null
        private set
    val state = MutableStateFlow(DayState())
    private val mutex = Mutex()
    private var observed = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scope.launch { refreshAgenda() } }
    }
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) DayWidget.updateAll(this@DayApp, state.value)
                if (intent.action == Intent.ACTION_SCREEN_ON && LockscreenOverview.enabled(this@DayApp)) scope.launch { refreshAgenda() }
            }
        }, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED).apply { addAction(Intent.ACTION_SCREEN_ON) }, ContextCompat.RECEIVER_EXPORTED)
        try {
            contentResolver.registerContentObserver(android.provider.Settings.System.getUriFor(XIAOMI_ALARM_SETTING), false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { DayWidget.updateAll(this@DayApp, state.value) }
                })
        } catch (_: SecurityException) { /* Periodic widget updates still refresh the alarm. */ }
        startupRefresh = scope.launch { refreshAgenda() }
    }
    fun observeCalendars() {
        if (!observed && granted(android.Manifest.permission.READ_CALENDAR)) {
            contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer); observed = true
        }
    }
    suspend fun refreshAgenda() {
        val agenda = withContext(Dispatchers.IO) { readAgenda(this@DayApp) }
        CalendarReminders.refresh(this@DayApp)
        state.value = state.value.copy(agenda = agenda, weather = if (hasLocation()) state.value.weather ?: cachedWeather(this) else null)
        DayWidget.updateAll(this, state.value)
        observeCalendars()
    }
    suspend fun refresh(foreground: Boolean = false) = mutex.withLock {
        state.value = state.value.copy(loading = true)
        try {
            refreshAgenda()
            val (weather, error) = loadWeather(this, foreground)
            state.value = state.value.copy(weather = weather, weatherError = error, loading = false)
            DayWidget.updateAll(this, state.value)
        } finally { state.value = state.value.copy(loading = false) }
    }
}
fun nameday(context: Context, date: LocalDate = LocalDate.now()): String {
    val data = JSONObject(context.assets.open("namedays-sk.json").bufferedReader().use { it.readText() })
    val names = data.optJSONArray(date.format(DateTimeFormatter.ofPattern("MM-dd"))) ?: return "Dnes bez menín"
    return (0 until names.length()).joinToString(", ") { names.getString(it) }
}
fun scheduleDay(context: Context) {
    if (android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DayWidget::class.java)).isEmpty() && !LockscreenOverview.enabled(context)) return
    val scheduler = context.getSystemService(JobScheduler::class.java)
    // AppWidgetManager owns the 30-minute cadence; this job performs each requested fetch.
    scheduler.cancel(811)
    if (scheduler.getPendingJob(812) != null) return
    scheduler.schedule(JobInfo.Builder(812, ComponentName(context, DayJob::class.java)).setMinimumLatency(0).build())
}
class DayJob : JobService() {
    private val jobs = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = (application as DayApp).scope.launch {
            try { (application as DayApp).refresh() } finally { withContext(Dispatchers.Main) { jobs.remove(params.jobId); jobFinished(params, false) } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { jobs.remove(params.jobId)?.cancel(); return true }
}
