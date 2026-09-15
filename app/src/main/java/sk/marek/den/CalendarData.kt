package sk.marek.den

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as C
import android.provider.CalendarContract.Instances as I
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class CalendarSource(val label: String) { GOOGLE("Google · Gmail"), OUTLOOK("Outlook"), LOCAL("Ďalšie kalendáre") }
data class DayCalendar(val id: Long, val name: String, val account: String, val source: CalendarSource, val writable: Boolean = false)
data class DayEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val source: CalendarSource)
data class Agenda(val calendars: List<DayCalendar> = emptyList(), val events: List<DayEvent> = emptyList(), val error: String? = null,
    val dayEvents: List<DayEvent> = events)
fun classifyCalendar(type: String, account: String): CalendarSource = when {
    type.contains("outlook", true) || type.contains("exchange", true) || type.contains("microsoft", true) -> CalendarSource.OUTLOOK
    type.contains("google", true) -> CalendarSource.GOOGLE
    else -> CalendarSource.LOCAL
}
fun eventIsUpcoming(event: DayEvent, now: Long, zone: ZoneId): Boolean = if (event.allDay)
    Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate() > Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    else event.end > now || event.begin >= now
fun eventTime(event: DayEvent, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(event.begin).atZone(if (event.allDay) ZoneOffset.UTC else zone)
    val date = when (at.toLocalDate()) { today -> "Dnes"; today.plusDays(1) -> "Zajtra"; else -> at.format(DateTimeFormatter.ofPattern("d. M.")) }
    return if (event.allDay) "$date · celý deň" else "$date · ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}
fun readAgenda(context: Context): Agenda {
    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return Agenda(error = "Povoľ čítanie kalendárov v appke Deň")
    return try {

        val calendars = mutableListOf<DayCalendar>()
        context.contentResolver.query(C.CONTENT_URI, arrayOf(C._ID, C.CALENDAR_DISPLAY_NAME, C.ACCOUNT_NAME, C.ACCOUNT_TYPE, C.CALENDAR_ACCESS_LEVEL),
            "${C.VISIBLE}=1 AND ${C.SYNC_EVENTS}=1", null, null)?.use { cursor ->
            while (cursor.moveToNext()) calendars += DayCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(),
                classifyCalendar(cursor.getString(3).orEmpty(), cursor.getString(2).orEmpty()), cursor.getInt(4) >= C.CAL_ACCESS_CONTRIBUTOR)
        }
        val hidden = context.getSharedPreferences("day", Context.MODE_PRIVATE).getStringSet("hidden_calendars", emptySet()).orEmpty()
        val enabled = calendars.filter { it.id.toString() !in hidden }.associateBy { it.id }
        if (enabled.isEmpty()) return Agenda(calendars)
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val start = minOf(today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, now + 14L * 86400000) }.build()

        val events = mutableListOf<DayEvent>()
        context.contentResolver.query(uri, arrayOf(I.EVENT_ID, I.TITLE, I.BEGIN, I.END, I.ALL_DAY, I.CALENDAR_ID),
            "(${I.STATUS} IS NULL OR ${I.STATUS}!=?) AND (${I.SELF_ATTENDEE_STATUS} IS NULL OR ${I.SELF_ATTENDEE_STATUS}!=?)",
            arrayOf(CalendarContract.Events.STATUS_CANCELED.toString(), CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString()), "${I.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val calendar = enabled[cursor.getLong(5)] ?: continue
                val event = DayEvent(cursor.getLong(0), cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "Udalosť bez názvu", cursor.getLong(2), cursor.getLong(3), cursor.getInt(4) == 1, calendar.source)
                events += event
            }
        }
        val unique = events.distinctBy { Triple(it.id, it.begin, it.source) }
        Agenda(calendars, unique.filter { eventIsUpcoming(it, now, ZoneId.systemDefault()) }, dayEvents = unique)
    } catch (_: Exception) { Agenda(error = "Kalendáre sa nepodarilo načítať. Obnov údaje.") }
}
