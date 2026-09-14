package sk.marek.den

import android.app.Notification
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class OverviewDeviceTest {
    @Test fun overviewIsSilentPublicAndOnlyContainsGoogleAndOutlook() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val state = DayState(agenda = Agenda(events = listOf(
            DayEvent(1,"Google ukážka", now+60000,now+3600000,false,CalendarSource.GOOGLE),
            DayEvent(2,"Outlook ukážka", now+60000,now+3600000,false,CalendarSource.OUTLOOK),
            DayEvent(3,"Miestny skrytý", now+60000,now+3600000,false,CalendarSource.LOCAL))))
        LockscreenOverview.channel(context)
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(LockscreenOverview.CHANNEL)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        val notification = LockscreenOverview.build(context, state)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val body = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(body.contains("Google ukážka"))
        assertTrue(body.contains("Outlook ukážka"))
        assertFalse(body.contains("Miestny skrytý"))
        val compact = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(compact.contains("Google ukážka") && compact.contains("Outlook ukážka"))
        assertTrue(notification.actions.any { it.title == "Obnoviť" })
    }
}
