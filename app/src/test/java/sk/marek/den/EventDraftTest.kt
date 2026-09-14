package sk.marek.den

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant

class EventDraftTest {
    private fun draft() = EventDraft(4, CalendarSource.GOOGLE, "Stretnutie", LocalDateTime.parse("2026-09-15T09:00"), LocalDateTime.parse("2026-09-15T10:00"), false, "", "")
    @Test fun groupContainsOnlyWritableCalendarsOfThatSource() {
        val calendars = listOf(DayCalendar(1,"Sviatky","",CalendarSource.GOOGLE,false), DayCalendar(2,"Práca","",CalendarSource.OUTLOOK,true), DayCalendar(3,"Osobný","",CalendarSource.GOOGLE,true))
        assertEquals(listOf(3L), creationCalendars(calendars,CalendarSource.GOOGLE).map { it.id })
        assertTrue(creationCalendars(calendars,CalendarSource.LOCAL).isEmpty())
    }
    @Test fun localTimeUsesCalendarTimezone() {
        val times = eventTimes(draft(), ZoneId.of("Europe/Bratislava"))
        assertEquals(Instant.parse("2026-09-15T07:00:00Z").toEpochMilli(),times.start)
        assertEquals(3600000,times.end-times.start)
    }
    @Test fun allDayUsesUtcAndInclusiveLastDateAcrossDst() {
        val times=eventTimes(draft().copy(start=LocalDateTime.parse("2026-10-24T09:00"),end=LocalDateTime.parse("2026-10-25T10:00"),allDay=true),ZoneId.of("Europe/Bratislava"))
        assertEquals("UTC",times.timezone)
        assertEquals(Instant.parse("2026-10-24T00:00:00Z").toEpochMilli(),times.start)
        assertEquals(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(),times.end)
    }
    @Test fun invalidFormCannotProduceAnEvent() {
        for (invalid in listOf(draft().copy(title="  "),draft().copy(calendarId=-1),draft().copy(end=draft().start),draft().copy(end=draft().start.minusDays(1),allDay=true))) {
            assertThrows(IllegalArgumentException::class.java) { eventTimes(invalid) }
        }
    }
    @Test fun nonexistentTimeAtSpringDstIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { eventTimes(draft().copy(start=LocalDateTime.parse("2026-03-29T02:30"),end=LocalDateTime.parse("2026-03-29T04:00")),ZoneId.of("Europe/Bratislava")) }
    }
}
