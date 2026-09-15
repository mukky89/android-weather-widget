package sk.marek.den

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarPagingTest {
    @Test fun arrowsWrapAtBothEndsAndHandleSingleOrEmptyDay() {
        assertEquals(2, calendarPageIndex(0, 3, -1))
        assertEquals(0, calendarPageIndex(2, 3, 1))
        assertEquals(1, calendarPageIndex(0, 3, 1))
        assertEquals(0, calendarPageIndex(1, 3, -1))
        assertEquals(0, calendarPageIndex(0, 1, 1))
        assertEquals(0, calendarPageIndex(0, 0, -1))
    }
}
