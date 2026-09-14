package sk.marek.den

import android.content.Context
import android.os.Build
import android.provider.Settings

const val XIAOMI_ALARM_SETTING = "next_alarm_clock_formatted"

// Verified against Xiaomi 13 Lite's Clock app. Null means unsupported; empty means no active wake-up alarm.
fun xiaomiAlarmText(context: Context): String? {
    if (Build.MANUFACTURER.lowercase() !in setOf("xiaomi", "redmi", "poco")) return null
    return try { Settings.System.getString(context.contentResolver, XIAOMI_ALARM_SETTING)?.trim() }
    catch (_: SecurityException) { null }
}
