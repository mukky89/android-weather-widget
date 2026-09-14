package sk.marek.den

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CalendarReminders {
    const val CHANNEL = "calendar_events"
    const val FIRE = "sk.marek.den.REMIND"
    const val TEST = "sk.marek.den.TEST_REMINDER"
    const val WATCH = 813
    private val mutex = Mutex()
    fun enabled(context: Context) = context.getSharedPreferences("day",0).getBoolean("reminders_enabled",false)
    fun exact(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun notificationsAllowed(context: Context): Boolean {
        val manager=context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun channel(context: Context) {
        val channel=NotificationChannel(CHANNEL,"Udalosti z kalendára",NotificationManager.IMPORTANCE_HIGH).apply {
            description="Pripomenutia stretnutí a celodenných udalostí"
            enableVibration(true)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun alarmIntent(context: Context, action: String) = PendingIntent.getBroadcast(context, if(action==TEST) 819 else 820,
        Intent(context, ReminderReceiver::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun alarm(context: Context, at: Long, action: String, precise: Boolean) {
        val manager=context.getSystemService(AlarmManager::class.java)
        val pending=alarmIntent(context,action)
        try {
            if (precise && exact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending)
        } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending) }
    }
    fun scheduleTest(context: Context) { channel(context); alarm(context,System.currentTimeMillis()+10000,TEST,true) }
    fun watch(context: Context, replace: Boolean = false) {
        val jobs=context.getSystemService(JobScheduler::class.java)
        if (!enabled(context) || !context.granted(Manifest.permission.READ_CALENDAR)) { jobs.cancel(WATCH); return }
        if (!replace && jobs.getPendingJob(WATCH)!=null) return
        jobs.schedule(JobInfo.Builder(WATCH,ComponentName(context,ReminderWatchJob::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(1000).setTriggerContentMaxDelay(10000).build())
    }
    suspend fun refresh(context: Context, deliver: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            channel(context)
            val prefs=context.getSharedPreferences("day",0)
            val alarms=context.getSystemService(AlarmManager::class.java)
            if (!enabled(context) || !context.granted(Manifest.permission.READ_CALENDAR) || !notificationsAllowed(context)) {
                alarms.cancel(alarmIntent(context,FIRE)); alarms.cancel(alarmIntent(context,TEST))
                context.getSystemService(JobScheduler::class.java).cancel(WATCH)
                prefs.edit().remove("next_reminder").apply()
                if (!enabled(context)) {
                    val notifications = context.getSystemService(NotificationManager::class.java)
                    notifications.activeNotifications.filter { it.notification.channelId == CHANNEL }.forEach { notifications.cancel(it.tag, it.id) }
                }
                return@withContext
            }
            watch(context)
            val now=System.currentTimeMillis()
            val agenda=readAgenda(context)
            if (agenda.error!=null) { alarm(context,now+15*60000,FIRE,false); return@withContext }
            val ledger=runCatching { JSONObject(prefs.getString("delivered_reminders","{}")!!) }.getOrDefault(JSONObject())
            ledger.keys().asSequence().toList().filter { ledger.optLong(it)<now }.forEach { ledger.remove(it) }
            val delivered=ledger.keys().asSequence().toMutableSet()
            val reminders=eventReminders(agenda.events,prefs.getInt("reminder_minutes",15),prefs.getBoolean("remind_all_day",true),now)
            if (deliver) for (reminder in dueReminders(reminders,delivered,now)) {
                try {
                    post(context,reminder)
                    delivered.add(reminder.key); ledger.put(reminder.key,maxOf(reminder.event.end,reminder.expires)+2*86400000L)
                    prefs.edit().putString("delivered_reminders",ledger.toString()).commit()
                } catch (_: SecurityException) { break }
            }
            val next=nextReminderAt(reminders,delivered,now)
            prefs.edit().putString("delivered_reminders",ledger.toString()).putLong("next_reminder",next ?: 0).apply()
            // Refresh the 14-day horizon even with no widget or an empty calendar.
            val maintenance=now+6*3600000L
            alarm(context,next ?: maintenance,FIRE,next!=null)
        }
    }
    private fun eventIntent(context: Context,event: DayEvent): Intent = Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,event.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,event.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,event.end).also {
            val google=Intent(it).setPackage("com.google.android.calendar")
            if (google.resolveActivity(context.packageManager)!=null) it.setPackage("com.google.android.calendar")
        }
    private fun base(context: Context,title: String,text: String,open: PendingIntent) = Notification.Builder(context,CHANNEL)
        .setSmallIcon(R.drawable.ic_event_notification).setContentTitle(title).setContentText(text)
        .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
        .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
    fun post(context: Context,reminder: EventReminder) {
        val event=reminder.event
        val target=eventIntent(context,event).setIdentifier(reminder.key)
        val open=PendingIntent.getActivity(context,820,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val date=if(event.allDay) "Dnes · celý deň" else Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. M. · HH:mm"))
        context.getSystemService(NotificationManager::class.java).notify(reminder.key,820,
            base(context,event.title,"$date · ${event.source.label}",open).setWhen(event.begin).build())
    }
    fun postTest(context: Context) {
        if (!enabled(context) || !notificationsAllowed(context)) return
        channel(context)
        val open=PendingIntent.getActivity(context,819,Intent(context,DayActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify("test",819,
            base(context,"Skúšobné upozornenie","Upozornenia na udalosti z kalendára sú zapnuté.",open).setOnlyAlertOnce(false).build())
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending=goAsync()
        val app=context.applicationContext as DayApp
        app.scope.launch {
            try {
                if(intent.action==CalendarReminders.TEST) CalendarReminders.postTest(context)
                else CalendarReminders.refresh(context,deliver=intent.action==CalendarReminders.FIRE)
            } finally { pending.finish() }
        }
    }
}
class ReminderWatchJob : JobService() {
    private var work: Job?=null
    override fun onStartJob(params: JobParameters): Boolean {
        work=(application as DayApp).scope.launch {
            try { CalendarReminders.refresh(this@ReminderWatchJob) }
            finally { withContext(NonCancellable+Dispatchers.Main) {
                CalendarReminders.watch(this@ReminderWatchJob,replace=true)
                jobFinished(params,false)
            } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
}
