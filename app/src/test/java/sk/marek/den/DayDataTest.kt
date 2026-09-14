package sk.marek.den

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayDataTest {
    @Test fun accountTypeIdentifiesOutlookEvenWithCustomEmailDomain() {
        assertEquals(CalendarSource.OUTLOOK, classifyCalendar("com.microsoft.office.outlook", "work@example.test"))
        assertEquals(CalendarSource.GOOGLE, classifyCalendar("com.google", "work@example.test"))
        assertEquals(CalendarSource.LOCAL, classifyCalendar("LOCAL", "phone"))
    }
    @Test fun allDayUtcStorageRemainsOnCorrectLocalDate() {
        val e = DayEvent(1, "TEST", Instant.parse("2026-09-14T00:00:00Z").toEpochMilli(), Instant.parse("2026-09-15T00:00:00Z").toEpochMilli(), true, CalendarSource.GOOGLE)
        val zone = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2026-09-15T02:00:00Z").toEpochMilli()
        assertTrue(eventIsUpcoming(e, now, zone))
        assertEquals("Dnes · celý deň", eventTime(e, LocalDate.of(2026, 9, 14), zone))
        assertFalse(eventIsUpcoming(e, Instant.parse("2026-09-15T12:00:00Z").toEpochMilli(), zone))
    }
    @Test fun endedTimedEventsDisappearWhileOngoingEventsRemain() {
        val e = DayEvent(1, "TEST", 1000, 2000, false, CalendarSource.LOCAL)
        assertTrue(eventIsUpcoming(e, 1500, ZoneId.of("UTC")))
        assertFalse(eventIsUpcoming(e, 2001, ZoneId.of("UTC")))
    }
    @Test fun weatherLocationIsRoundedBeforeNetworkTransmission() {
        assertEquals(48.15, roundedCoordinate(48.153789), 0.0000001)
        assertEquals(-73.99, roundedCoordinate(-73.98765), 0.0000001)
        assertEquals("Búrky", weatherText(95))
        assertEquals("Sneženie", weatherText(73))
    }
}
