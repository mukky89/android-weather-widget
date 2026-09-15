package sk.marek.den

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration

fun dayWidgetLayout(context: Context, widgetId: Int): Int {
    if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return R.layout.day_widget
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    val key = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
    val height = options.getInt(key)
    return when (height) {
        in 1 until 320 -> R.layout.day_widget_small
        in 320 until 346 -> R.layout.day_widget_compact
        else -> R.layout.day_widget
    }
}
