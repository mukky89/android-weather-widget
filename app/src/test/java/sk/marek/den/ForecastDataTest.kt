package sk.marek.den

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

class ForecastDataTest {
    @Test fun dailyTimestampUsesForecastLocationTimezone() {
        assertEquals(LocalDate.of(2026,9,14), forecastDate(1789336800,"Europe/Bratislava"))
        assertEquals(LocalDate.of(2026,9,13), forecastDate(1789336800,"Pacific/Honolulu"))
    }
    @Test fun midnightRolloverNeverRelabelsYesterdayAsToday() {
        val today=LocalDate.of(2026,9,15)
        val days=(0L..6L).map { ForecastDay(today.minusDays(1).plusDays(it),1,10.0,20.0) }
        val slots=forecastSlots(days,today)
        assertEquals(6,slots.size)
        assertEquals(today,slots.first()!!.date)
        assertEquals(today.plusDays(5),slots.last()!!.date)
        assertEquals("DNES",forecastLabel(today,today))
        assertEquals("ST",forecastLabel(today.plusDays(1),today))
    }
    @Test fun missingForecastDatesStayMissingRatherThanShiftingTemperatures() {
        val today=LocalDate.of(2026,9,15)
        val slots=forecastSlots(listOf(ForecastDay(today.plusDays(2),61,11.0,14.0)),today)
        assertNull(slots[0]); assertNull(slots[1]); assertEquals(14.0,slots[2]!!.high,0.0)
        assertTrue(forecastSlots(emptyList(),today).all { it == null })
    }
    @Test fun todayTracksWeatherCityEvenIfPhoneUsesAnotherTimezone() {
        val w=Weather(20.0,0,10.0,22.0,"Test",0,0,0.0,0.0,timezone="Pacific/Honolulu")
        assertEquals(LocalDate.of(2026,9,13),forecastToday(w,Instant.parse("2026-09-14T02:00:00Z")))
    }
}
