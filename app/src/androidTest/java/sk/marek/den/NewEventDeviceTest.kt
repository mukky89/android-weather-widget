package sk.marek.den

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class NewEventDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun allSourceFormsSelectWritableCalendarAndValidateWithoutSaving() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val agenda=readAgenda(context)
        for (source in CalendarSource.entries) {
            ActivityScenario.launch<NewEventActivity>(Intent(context,NewEventActivity::class.java).putExtra("source",source.name)).use {
                compose.waitUntil(10000) { compose.onAllNodesWithText("Načítavam kalendáre…").fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText("Nová udalosť").assertExists()
                compose.onNodeWithText(source.label).assertExists()
                val calendars=creationCalendars(agenda.calendars,source)
                if (calendars.size > 1) {
                    compose.onNodeWithText("Vybrať kalendár").performClick()
                    compose.onNodeWithText(calendars.first().displayName()).performScrollTo().performClick()
                }
                if (calendars.isNotEmpty()) {
                    compose.onNodeWithText("Uložiť udalosť").performScrollTo().performClick()
                    compose.onNodeWithText("Doplň názov udalosti.").assertExists()
                }
                compose.onNodeWithText("Zrušiť").performScrollTo().performClick()
            }
        }
    }
}
