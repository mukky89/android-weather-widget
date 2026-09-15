package sk.marek.den

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class DayDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<DayActivity>()
    @Test fun manualPageChangesOnlyChosenCalendarAndIsIdempotent() {
        val context = compose.activity
        val now = System.currentTimeMillis()
        val events = CalendarSource.entries.take(2).flatMap { source ->
            (0..2).map { index -> DayEvent(800 + source.ordinal * 10L + index, "${source.name} $index", now, now + 60000, false, source) }
        }
        compose.runOnUiThread {
            val view = DayWidget.views(context, DayState(agenda = Agenda(events = events))).apply(context, FrameLayout(context))
            val google = view.findViewById<android.widget.ViewFlipper>(R.id.google_calendar_flipper)
            val outlook = view.findViewById<android.widget.ViewFlipper>(R.id.outlook_calendar_flipper)
            calendarPageViews(context, CalendarSource.OUTLOOK, 1).reapply(context, view)
            val update = calendarPageViews(context, CalendarSource.GOOGLE, 2)
            update.reapply(context, view)
            update.reapply(context, view)
            assertEquals(2, google.displayedChild)
            assertEquals(1, outlook.displayedChild)
            assertEquals("GOOGLE 2", google.currentView.findViewById<android.widget.TextView>(R.id.google_title).text.toString())
            assertEquals("OUTLOOK 1", outlook.currentView.findViewById<android.widget.TextView>(R.id.outlook_title).text.toString())
        }
    }
    @Test fun eventsRotateTogetherAndStopWhenOnlyOneRemains() {
        val context = compose.activity
        val today = LocalDate.now()
        val start = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val first = DayEvent(901, "Meeting one", start, start + 86400000, false, CalendarSource.GOOGLE)
        val second = first.copy(id = 902, title = "Meeting two", begin = start + 60000)
        val birthday = first.copy(id = 903, title = "Birthday", allDay = true,
            begin = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            end = today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        val agenda = Agenda(calendars = listOf(DayCalendar(901, "Example", "example.test", CalendarSource.GOOGLE)), events = listOf(first, second, birthday))
        lateinit var parent: FrameLayout
        lateinit var view: View
        lateinit var flipper: android.widget.ViewFlipper
        compose.runOnUiThread {
            parent = FrameLayout(context)
            context.addContentView(parent, android.view.ViewGroup.LayoutParams(-1, -1))
            view = DayWidget.views(context, DayState(agenda = agenda)).apply(context, parent)
            parent.addView(view)
            flipper = view.findViewById<FrameLayout>(R.id.google_row_container).findViewById(R.id.google_calendar_flipper)
            assertEquals(3, flipper.childCount)
            assertEquals(8000, flipper.flipInterval)
            assertEquals("Meeting one", flipper.currentView.findViewById<android.widget.TextView>(R.id.google_title).text.toString())
            assertNull(view.findViewById<FrameLayout>(R.id.outlook_row_container).findViewById<View>(R.id.outlook_calendar_flipper))
        }
        try {
            android.os.SystemClock.sleep(8500)
            compose.runOnUiThread {
                assertEquals(1, flipper.displayedChild)
                val current = flipper.currentView
                assertEquals("Meeting two", current.findViewById<android.widget.TextView>(R.id.google_title).text.toString())
                assertEquals(eventTime(second), current.findViewById<android.widget.TextView>(R.id.google_time).text.toString())
                assertTrue(current.findViewById<android.widget.TextView>(R.id.google_label).text.endsWith("2/3"))
                val single = Agenda(calendars = agenda.calendars, events = listOf(first))
                DayWidget.views(context, DayState(agenda = single)).reapply(context, view)
                assertNull(view.findViewById<FrameLayout>(R.id.google_row_container).findViewById<View>(R.id.google_calendar_flipper))
                assertEquals("Meeting one", view.findViewById<android.widget.TextView>(R.id.google_title).text.toString())
            }
        } finally { compose.runOnUiThread { (parent.parent as android.view.ViewGroup).removeView(parent) } }
    }
    @Test fun nativeCalendarRoutesKeepTheirSourceAndDate() {
        val date = LocalDate.of(2026, 9, 16)
        for (source in listOf(CalendarSource.GOOGLE, CalendarSource.OUTLOOK)) {
            val day = calendarDayIntent(source, date)
            assertEquals(calendarPackage(source), day.`package`)
            assertEquals("time/epoch", day.type)
            val at = android.content.ContentUris.parseId(day.data!!)
            assertEquals(date, java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            assertEquals("DAY", day.getStringExtra("VIEW"))
            val insert = calendarInsertIntent(source)
            assertEquals(calendarPackage(source), insert.`package`)
            assertEquals(android.content.Intent.ACTION_INSERT, insert.action)
            assertEquals("vnd.android.cursor.dir/event", insert.type)
        }
    }
    @Test fun localNamedaysAndWidgetRenderWithoutAccountAccess() {
        val context = compose.activity
        assertTrue(nameday(context, LocalDate.of(2026, 9, 14)).contains("Ľudomil"))
        assertTrue(nameday(context, LocalDate.of(2026, 4, 25)).contains("Marek"))
        compose.runOnUiThread {
            for ((width, height) in listOf(250 to 250, 350 to 250, 250 to 320, 350 to 320, 350 to 360)) {
                val parent = FrameLayout(context)
                context.addContentView(parent, android.view.ViewGroup.LayoutParams(-1, -1))
                val fixture = Weather(18.0, 2, 12.0, 24.0, "Ukážka", System.currentTimeMillis(), System.currentTimeMillis(), 0.0, 0.0,
                    forecast = (0L..6L).map { ForecastDay(LocalDate.now().plusDays(it), listOf(2,0,3,61,95,71,1)[it.toInt()], 12.0-it, 24.0-it) })
                val now = System.currentTimeMillis()
                val events = (0..2).map { DayEvent(700L + it, "Example $it", now, now + 60000, false, CalendarSource.GOOGLE) }
                val view = DayWidget.views(context, DayState(agenda = Agenda(events = events), weather = fixture), layoutId = if (height < 320) R.layout.day_widget_small else if (height < 346) R.layout.day_widget_compact else R.layout.day_widget).apply(context, parent)
                parent.addView(view)
                val d = context.resources.displayMetrics.density
                val w = (width*d).toInt(); val h = (height*d).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                for (id in listOf(R.id.day_alarm, R.id.day_panel, R.id.forecast_temp_0, R.id.forecast_temp_5, R.id.outlook_title, R.id.outlook_add, R.id.google_count, R.id.outlook_count, R.id.google_previous, R.id.google_next, R.id.day_clock, R.id.day_date, R.id.day_temperature, R.id.google_day_area, R.id.outlook_day_area, R.id.day_toolbar, R.id.day_settings, R.id.day_refresh)) {
                    val child = view.findViewById<View>(id)
                    val rect = android.graphics.Rect(); child.getDrawingRect(rect)
                    (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(child, rect)
                    assertTrue("Clipped view $id: $rect / $h", rect.bottom <= h && rect.right <= w && rect.top >= 0 && rect.left >= 0)
                }
                for ((leftId, rightId, addId) in listOf(Triple(R.id.google_event_area, R.id.google_day_area, R.id.google_add), Triple(R.id.outlook_event_area, R.id.outlook_day_area, R.id.outlook_add))) {
                    val left = view.findViewById<View>(leftId)
                    val right = view.findViewById<View>(rightId)
                    val add = view.findViewById<View>(addId)
                    assertTrue("Event/day/add targets must remain separate", left.width > 0 && left.right <= right.left && right.right <= add.left)
                    assertTrue(left.hasOnClickListeners() && right.hasOnClickListeners() && add.hasOnClickListeners())
                }
                assertEquals("HH:mm", view.findViewById<TextClock>(R.id.day_clock).format24Hour.toString())
                val b = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); view.draw(Canvas(b))
                File(context.filesDir,"day-widget-$width-$height.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
                (parent.parent as android.view.ViewGroup).removeView(parent)
            }
        }
    }
    @Test fun pinOnlyWhenExplicitlyRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pinDayWidget") == "yes")
        compose.onNodeWithText("Pridať widget na plochu").performClick()
    }
    @Test fun enableWeatherOnlyWhenExplicitlyAuthorized() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("enableAuthorizedWeather") == "yes")
        compose.onNodeWithText("Poloha povolená · obnoviť").performScrollTo().performClick()
        val app = compose.activity.application as DayApp
        runBlocking { app.refresh(true) }
        val state = app.state.value
        val report = JSONObject().put("weatherEnabled", app.getSharedPreferences("day", 0).getBoolean("weather_enabled", false))
            .put("hasWeather", state.weather != null).put("weatherError", state.weatherError ?: JSONObject.NULL)
            .put("calendarError", state.agenda.error ?: JSONObject.NULL)
        for (source in CalendarSource.entries) report.put(source.name, JSONObject()
            .put("calendars", state.agenda.calendars.count { it.source == source })
            .put("events", state.agenda.events.count { it.source == source }))
        File(app.filesDir, "device-status.json").writeText(report.toString())
        assertTrue(report.getBoolean("weatherEnabled"))
        assertNull(state.agenda.error)
    }
}
