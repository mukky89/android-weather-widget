package sk.marek.den

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarSummary(val event: DayEvent?, val date: LocalDate, val count: Int)

fun eventTouchesDay(event: DayEvent, date: LocalDate, zone: ZoneId): Boolean {
    val eventZone = if (event.allDay) ZoneOffset.UTC else zone
    val start = date.atStartOfDay(eventZone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(eventZone).toInstant().toEpochMilli()
    return event.begin < end && (event.end > start || event.begin == event.end && event.begin >= start)
}

fun calendarSummary(agenda: Agenda, source: CalendarSource, now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()): CalendarSummary {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val upcoming = agenda.events.filter { it.source == source && eventIsUpcoming(it, now, zone) }
        .sortedWith(compareBy<DayEvent> { calendarDate(it, today, zone).coerceAtLeast(today) }.thenBy { it.begin })
    val first = upcoming.firstOrNull()
    val date = first?.let { calendarDate(it, today, zone).coerceAtLeast(today) } ?: today
    // A birthday or holiday must not hide the next timed meeting on the same day.
    val event = upcoming.firstOrNull { !it.allDay && eventTouchesDay(it, date, zone) } ?: first
    val count = agenda.dayEvents.filter { it.source == source && eventTouchesDay(it, date, zone) }
        .distinctBy { it.id to it.begin }.size
    return CalendarSummary(event, date, count)
}

fun eventCountLabel(count: Int): String = when (count) {
    1 -> "1 udalosť"
    in 2..4 -> "$count udalosti"
    else -> "$count udalostí"
}

fun calendarRotationEvents(agenda: Agenda, source: CalendarSource, summary: CalendarSummary,
    zone: ZoneId = ZoneId.systemDefault()): List<DayEvent> {
    if (agenda.error != null) return emptyList()
    val events = agenda.dayEvents.filter { it.source == source && eventTouchesDay(it, summary.date, zone) }
        .distinctBy { it.id to it.begin }.sortedWith(compareBy<DayEvent> { it.allDay }.thenBy { it.begin })
    // Start with the next meeting, then show every event counted for this day, including all-day events.
    val first = events.indexOfFirst { it.id == summary.event?.id && it.begin == summary.event.begin }
    return if (first > 0) events.drop(first) + events.take(first) else events
}
