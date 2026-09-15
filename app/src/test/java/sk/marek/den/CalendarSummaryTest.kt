package sk.marek.den

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarSummaryTest {
    private val zone = ZoneId.of("Europe/Bratislava")
    private fun event(id: Long, start: String, end: String, allDay: Boolean = false,
        source: CalendarSource = CalendarSource.GOOGLE) = DayEvent(id, "Example", Instant.parse(start).toEpochMilli(), Instant.parse(end).toEpochMilli(), allDay, source)
    @Test fun allDayDoesNotHideMeetingAndCountIncludesFinishedEvents() {
        val birthday = event(1, "2026-09-15T00:00:00Z", "2026-09-16T00:00:00Z", true)
        val finished = event(2, "2026-09-15T06:00:00Z", "2026-09-15T07:00:00Z")
        val meeting = event(3, "2026-09-15T10:00:00Z", "2026-09-15T11:00:00Z")
        val otherSource = meeting.copy(id = 4, source = CalendarSource.OUTLOOK)
        val agenda = Agenda(events = listOf(birthday, meeting, otherSource), dayEvents = listOf(birthday, finished, meeting, meeting, otherSource))
        val result = calendarSummary(agenda, CalendarSource.GOOGLE, Instant.parse("2026-09-15T09:00:00Z").toEpochMilli(), zone)
        assertEquals(meeting, result.event)
        assertEquals(3, result.count)
        assertEquals(LocalDate.of(2026, 9, 15), result.date)
    }
    @Test fun countUsesDisplayedFutureDay() {
        val next = event(1, "2026-09-16T07:00:00Z", "2026-09-16T08:00:00Z")
        val result = calendarSummary(Agenda(events = listOf(next)), CalendarSource.GOOGLE, Instant.parse("2026-09-15T09:00:00Z").toEpochMilli(), zone)
        assertEquals(LocalDate.of(2026, 9, 16), result.date)
        assertEquals(1, result.count)
        val birthday = event(2, "2026-09-16T00:00:00Z", "2026-09-17T00:00:00Z", true)
        val evening = event(3, "2026-09-16T01:00:00Z", "2026-09-16T02:00:00Z")
        val west = calendarSummary(Agenda(events = listOf(birthday, evening)), CalendarSource.GOOGLE,
            Instant.parse("2026-09-15T23:00:00Z").toEpochMilli(), ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 9, 15), west.date)
        assertEquals(evening, west.event)
    }
    @Test fun countHandlesMultiDayAndExclusiveMidnightEnd() {
        val overnight = event(1, "2026-09-14T20:00:00Z", "2026-09-15T01:00:00Z")
        val midnightEnd = overnight.copy(end = Instant.parse("2026-09-14T22:00:00Z").toEpochMilli())
        assertTrue(eventTouchesDay(overnight, LocalDate.of(2026, 9, 15), zone))
        assertFalse(eventTouchesDay(midnightEnd, LocalDate.of(2026, 9, 15), zone))
        val allDay = event(2, "2026-09-14T00:00:00Z", "2026-09-17T00:00:00Z", true)
        val result = calendarSummary(Agenda(events = listOf(allDay)), CalendarSource.GOOGLE, Instant.parse("2026-09-15T12:00:00Z").toEpochMilli(), ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 9, 15), result.date)
        assertEquals(1, result.count)
    }
    @Test fun emptyAndFinishedOnlyDayStillHasAccurateCount() {
        val now = Instant.parse("2026-09-15T20:00:00Z").toEpochMilli()
        val finished = event(1, "2026-09-15T06:00:00Z", "2026-09-15T07:00:00Z")
        val result = calendarSummary(Agenda(dayEvents = listOf(finished)), CalendarSource.GOOGLE, now, zone)
        assertNull(result.event)
        assertEquals(1, result.count)
        assertEquals(0, calendarSummary(Agenda(), CalendarSource.GOOGLE, now, zone).count)
    }
    @Test fun dstDayUsesLocalBoundaries() {
        val late = event(1, "2026-10-25T22:30:00Z", "2026-10-25T23:00:00Z")
        assertTrue(eventTouchesDay(late, LocalDate.of(2026, 10, 25), zone))
        assertFalse(eventTouchesDay(late, LocalDate.of(2026, 10, 26), zone))
    }
    @Test fun rotationStartsWithNextMeetingAndIncludesExactlyCountedDay() {
        val birthday = event(1, "2026-09-15T00:00:00Z", "2026-09-16T00:00:00Z", true)
        val finished = event(2, "2026-09-15T06:00:00Z", "2026-09-15T07:00:00Z")
        val next = event(3, "2026-09-15T10:00:00Z", "2026-09-15T11:00:00Z")
        val tomorrow = event(4, "2026-09-16T10:00:00Z", "2026-09-16T11:00:00Z")
        val outlook = next.copy(id = 5, source = CalendarSource.OUTLOOK)
        val agenda = Agenda(events = listOf(birthday, next, tomorrow, outlook), dayEvents = listOf(birthday, finished, next, next, tomorrow, outlook))
        val summary = calendarSummary(agenda, CalendarSource.GOOGLE, Instant.parse("2026-09-15T09:00:00Z").toEpochMilli(), zone)
        val rotation = calendarRotationEvents(agenda, CalendarSource.GOOGLE, summary, zone)
        assertEquals(listOf(next, birthday, finished), rotation)
        assertEquals(summary.count, rotation.size)
        assertTrue(calendarRotationEvents(agenda.copy(error = "Unavailable"), CalendarSource.GOOGLE, summary, zone).isEmpty())
    }
}
