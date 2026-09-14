package sk.marek.den

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class EventDraft(val calendarId: Long, val source: CalendarSource, val title: String,
    val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean, val location: String, val description: String)
data class EventTimes(val start: Long, val end: Long, val timezone: String)
fun eventTimes(draft: EventDraft, zone: ZoneId = ZoneId.systemDefault()): EventTimes {
    require(draft.calendarId > 0) { "Vyber kalendár." }
    require(draft.title.isNotBlank()) { "Doplň názov udalosti." }
    if (draft.allDay) {
        require(!draft.end.toLocalDate().isBefore(draft.start.toLocalDate())) { "Koniec nemôže byť pred začiatkom." }
        return EventTimes(draft.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            draft.end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), "UTC")
    }
    require(zone.rules.getValidOffsets(draft.start).isNotEmpty() && zone.rules.getValidOffsets(draft.end).isNotEmpty()) { "Tento čas pri zmene letného času neexistuje. Vyber iný čas." }
    val start = draft.start.atZone(zone).toInstant().toEpochMilli()
    val end = draft.end.atZone(zone).toInstant().toEpochMilli()
    require(end > start) { "Koniec musí byť po začiatku." }
    return EventTimes(start, end, zone.id)
}
fun creationCalendars(calendars: List<DayCalendar>, source: CalendarSource) = calendars.filter { it.source == source && it.writable }
