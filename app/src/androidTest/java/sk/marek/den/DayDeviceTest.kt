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
            for ((width, height) in listOf(250 to 360, 350 to 360)) {
                val parent = FrameLayout(context)
                context.addContentView(parent, android.view.ViewGroup.LayoutParams(-1, -1))
                val fixture = Weather(18.0, 2, 12.0, 24.0, "Ukážka", System.currentTimeMillis(), System.currentTimeMillis(), 0.0, 0.0,
                    forecast = (0L..6L).map { ForecastDay(LocalDate.now().plusDays(it), listOf(2,0,3,61,95,71,1)[it.toInt()], 12.0-it, 24.0-it) })
                val view = DayWidget.views(context, DayState(agenda = Agenda(), weather = fixture)).apply(context, parent)
                parent.addView(view)
                val d = context.resources.displayMetrics.density
                val w = (width*d).toInt(); val h = (height*d).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                for (id in listOf(R.id.day_alarm, R.id.day_panel, R.id.forecast_temp_0, R.id.forecast_temp_5, R.id.outlook_title, R.id.outlook_add, R.id.google_count, R.id.outlook_count, R.id.day_clock, R.id.day_date, R.id.day_temperature, R.id.google_day_area, R.id.outlook_day_area, R.id.day_toolbar, R.id.day_settings, R.id.day_refresh)) {
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
                File(context.filesDir,"day-widget-$width.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
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
