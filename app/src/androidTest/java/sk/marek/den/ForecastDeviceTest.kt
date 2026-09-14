package sk.marek.den

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForecastDeviceTest {
    private fun daily() = JSONObject("""{"time":[1789336800,1789423200],"weather_code":[80,1],"temperature_2m_max":[21.0,23.8],"temperature_2m_min":[16.4,15.6]}""")
    @Test fun dailyParsingAndCacheRoundTripPreserveDatesCodesAndMinMax() {
        val days=parseDailyForecast(daily(),"Europe/Bratislava")
        assertEquals("2026-09-14",days[0].date.toString())
        assertEquals(80,days[0].code)
        assertEquals(23.8,days[1].high,0.0)
        assertEquals(15.6,days[1].low,0.0)
        val w=Weather(20.0,0,16.4,21.0,"Test",0,0,0.0,0.0,forecast=days,timezone="Europe/Bratislava")
        val cache=JSONObject(weatherJson(w))
        assertEquals(days,cachedForecast(cache.getJSONArray("forecast")))
        assertEquals("Europe/Bratislava",cache.getString("timezone"))
        assertTrue(cachedForecast(JSONObject("{}").optJSONArray("forecast")).isEmpty())
    }
    @Test fun invalidDailyTemperaturesDoNotBecomeZeroForecasts() {
        val broken=daily(); broken.getJSONArray("temperature_2m_min").put(0,JSONObject.NULL)
        assertThrows(Exception::class.java) { parseDailyForecast(broken,"Europe/Bratislava") }
        val short=daily(); short.getJSONArray("weather_code").remove(0)
        assertThrows(IllegalArgumentException::class.java) { parseDailyForecast(short,"Europe/Bratislava") }
    }
}
