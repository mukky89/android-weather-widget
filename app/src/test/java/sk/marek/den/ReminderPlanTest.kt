package sk.marek.den

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderPlanTest {
    private val zone=ZoneId.of("Europe/Bratislava")
    private fun millis(s:String)=Instant.parse(s).toEpochMilli()
    private val begin=millis("2026-09-15T07:00:00Z")
    private fun meeting(id:Long=1)=DayEvent(id,"Stretnutie",begin,begin+3600000,false,CalendarSource.OUTLOOK)
    @Test fun meetingRemindsFifteenMinutesBeforeItStarts() {
        val reminders=eventReminders(listOf(meeting()),15,true,begin-3600000,zone)
        assertEquals(begin-900000,reminders.single().at)
        assertTrue(dueReminders(reminders,emptySet(),begin-900001).isEmpty())
        assertEquals(1,dueReminders(reminders,emptySet(),begin-900000).size)
    }
    @Test fun dismissedReminderDoesNotRepeatOnRefreshOrLeadChange() {
        val reminders=eventReminders(listOf(meeting()),30,true,begin-3000000,zone)
        val sent=setOf(reminders.single().key)
        assertTrue(dueReminders(reminders,sent,begin-1000).isEmpty())
        assertNull(nextReminderAt(reminders,sent,begin-1000))
    }
    @Test fun changedStartTimeCreatesFreshReminderButTitleChangeDoesNot() {
        val original=eventReminders(listOf(meeting()),15,true,begin-3600000,zone).single()
        val renamed=eventReminders(listOf(meeting().copy(title="Iný názov")),15,true,begin-3600000,zone).single()
        val moved=eventReminders(listOf(meeting().copy(begin=begin+3600000,end=begin+7200000)),15,true,begin-3600000,zone).single()
        assertEquals(original.key,renamed.key);assertNotEquals(original.key,moved.key)
    }
    @Test fun allDayRemindsAtNineLocalAcrossWinterTimeChange() {
        val event=DayEvent(2,"Celý deň",millis("2026-10-25T00:00:00Z"),millis("2026-10-26T00:00:00Z"),true,CalendarSource.GOOGLE)
        val reminders=eventReminders(listOf(event),15,true,event.begin,zone)
        assertEquals(millis("2026-10-25T08:00:00Z"),reminders.single().at)
        assertTrue(eventReminders(listOf(event),15,false,event.begin,zone).isEmpty())
        assertTrue(eventReminders(listOf(event),15,true,millis("2026-10-25T09:00:00Z"),zone).isEmpty())
    }
    @Test fun missedOldRemindersAreNotFloodedOnFirstEnable() {
        assertTrue(eventReminders(listOf(meeting()),15,true,begin+600000,zone).isEmpty())
        val imminent=eventReminders(listOf(meeting()),15,true,begin-60000,zone)
        assertEquals(1,dueReminders(imminent,emptySet(),begin-60000).size)
    }
    @Test fun simultaneousEventsAreAllDueAndSameInstanceIsDeduplicated() {
        val reminders=eventReminders(listOf(meeting(),meeting(),meeting(2)),15,true,begin-3600000,zone)
        assertEquals(2,reminders.size)
        assertEquals(2,dueReminders(reminders,emptySet(),begin-900000).size)
    }
    @Test fun emptyOrRemovedEventCannotLeaveADueReminder() {
        assertTrue(dueReminders(eventReminders(emptyList(),15,true,begin,zone),emptySet(),begin).isEmpty())
        assertNull(nextReminderAt(emptyList(),emptySet(),begin))
    }
}
