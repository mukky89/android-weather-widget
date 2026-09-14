package sk.marek.den

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarNavigationTest {
    @Test fun allDayKeepsUtcDateInNegativeTimezone() {
        val start = Instant.parse("2026-09-15T00:00:00Z").toEpochMilli()
        val event = DayEvent(1, "Example", start, start + 86400000, true, CalendarSource.GOOGLE)
        assertEquals(LocalDate.of(2026, 9, 15), calendarDate(event, zone = ZoneId.of("America/Los_Angeles")))
    }
    @Test fun timedEventOpensItsLocalDayAndMissingEventUsesToday() {
        val start = Instant.parse("2026-09-14T23:30:00Z").toEpochMilli()
        val event = DayEvent(1, "Example", start, start + 3600000, false, CalendarSource.OUTLOOK)
        assertEquals(LocalDate.of(2026, 9, 15), calendarDate(event, zone = ZoneId.of("Europe/Bratislava")))
        assertEquals(LocalDate.of(2026, 10, 25), calendarDate(null, today = LocalDate.of(2026, 10, 25)))
    }
}
