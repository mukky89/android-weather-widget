package sk.marek.den

import android.content.Context
import android.widget.RemoteViews

fun calendarPageIndex(current: Int, count: Int, direction: Int): Int =
    if (count <= 1) 0 else Math.floorMod(current + direction, count)

fun calendarFlipperId(source: CalendarSource): Int = if (source == CalendarSource.GOOGLE)
    R.id.google_calendar_flipper else R.id.outlook_calendar_flipper

fun calendarPageViews(context: Context, source: CalendarSource, index: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.day_widget).apply { setDisplayedChild(calendarFlipperId(source), index) }
