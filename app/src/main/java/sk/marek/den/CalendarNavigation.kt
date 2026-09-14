package sk.marek.den

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

fun calendarDate(event: DayEvent?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    event?.let { Instant.ofEpochMilli(it.begin).atZone(if (it.allDay) ZoneOffset.UTC else zone).toLocalDate() } ?: today

fun calendarDayIntent(context: Context, event: DayEvent?): Intent {
    val at = calendarDate(event).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val timeUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
    return Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(timeUri, at)).putExtra("VIEW", "DAY").also {
        // Use the same calendar viewer as event details, including Outlook's Android-synced events.
        val google = Intent(it).setPackage("com.google.android.calendar")
        if (google.resolveActivity(context.packageManager) != null) it.setPackage("com.google.android.calendar")
    }
}
