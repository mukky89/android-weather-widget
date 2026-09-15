package sk.marek.den

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

fun calendarDate(event: DayEvent?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    event?.let { Instant.ofEpochMilli(it.begin).atZone(if (it.allDay) ZoneOffset.UTC else zone).toLocalDate() } ?: today

fun calendarPackage(source: CalendarSource): String = if (source == CalendarSource.OUTLOOK)
    "com.microsoft.office.outlook" else "com.google.android.calendar"

fun calendarDayIntent(source: CalendarSource, date: LocalDate): Intent {
    val at = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val timeUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
    return Intent(Intent.ACTION_VIEW).setDataAndType(ContentUris.withAppendedId(timeUri, at), "time/epoch")
        .putExtra("VIEW", "DAY").setPackage(calendarPackage(source))
}

fun calendarInsertIntent(source: CalendarSource): Intent = Intent(Intent.ACTION_INSERT)
    .setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event")
    .setPackage(calendarPackage(source))

/** Resolve at tap time: an uninstalled calendar must not silently open a different account's app. */
class CalendarActionActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val source = CalendarSource.entries.firstOrNull { it.name == intent.getStringExtra("source") } ?: CalendarSource.GOOGLE
        val target = if (intent.getBooleanExtra("insert", false)) calendarInsertIntent(source)
            else calendarDayIntent(source, runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrDefault(LocalDate.now()))
        try { startActivity(target) }
        catch (_: android.content.ActivityNotFoundException) { unavailable(source) }
        catch (_: SecurityException) { unavailable(source) }
        finish()
    }
    private fun unavailable(source: CalendarSource) {
        android.widget.Toast.makeText(this, "${source.label}: kalendárová aplikácia nie je dostupná. Nainštaluj alebo povoľ ju.", android.widget.Toast.LENGTH_LONG).show()
    }
}
