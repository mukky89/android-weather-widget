package sk.marek.den

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class EventReminder(val event: DayEvent, val at: Long, val expires: Long) {
    val key get() = "${event.id}:${event.begin}"
}
fun eventReminders(events: List<DayEvent>, minutes: Int, allDayEnabled: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<EventReminder> =
    events.asSequence().filter { !it.allDay || allDayEnabled }.map { event ->
        val at = if (event.allDay) Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate().atTime(9,0).atZone(zone).toInstant().toEpochMilli()
            else event.begin - minutes.coerceIn(0,1440) * 60000L
        val expires = if (event.allDay) at + 30 * 60000L else minOf(event.end.takeIf { it > event.begin } ?: (event.begin + 300000), event.begin + 300000)
        EventReminder(event, at, expires)
    }.filter { now < it.expires }.distinctBy { it.key }.sortedBy { it.at }.toList()
fun dueReminders(reminders: List<EventReminder>, delivered: Set<String>, now: Long) = reminders.filter { it.key !in delivered && it.at <= now && now < it.expires }
fun nextReminderAt(reminders: List<EventReminder>, delivered: Set<String>, now: Long): Long? = reminders.firstOrNull { it.key !in delivered && now < it.expires }?.at?.coerceAtLeast(now + 1000)
