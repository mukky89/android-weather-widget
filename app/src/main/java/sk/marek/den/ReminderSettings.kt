package sk.marek.den

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReminderSettings(activity: ComponentActivity, state: DayState) {
    val app=activity.application as DayApp
    val prefs=remember { activity.getSharedPreferences("day",0) }
    var enabled by remember { mutableStateOf(CalendarReminders.enabled(activity)) }
    var minutes by remember { mutableIntStateOf(prefs.getInt("reminder_minutes",15)) }
    var allDay by remember { mutableStateOf(prefs.getBoolean("remind_all_day",true)) }
    var chooseLead by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun setEnabled(value: Boolean) {
        enabled=value; prefs.edit().putBoolean("reminders_enabled",value).apply()
        app.scope.launch { app.refreshAgenda() }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setEnabled(granted)
        if(!granted) message="Oznámenia môžeš povoliť v systémových nastaveniach aplikácie."
    }
    // State changes after onResume make system permission/channel changes visible here.
    val allowed=state.let { CalendarReminders.notificationsAllowed(activity) }
    val exact=CalendarReminders.exact(activity)
    Card(shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("Upozornenia na udalosti",fontWeight=FontWeight.Bold)
            Text("Pripomenutia zo všetkých kalendárov vybraných nižšie, aj pri zatvorenej appke.",fontSize=13.sp)
            Button(onClick={
                if(enabled) setEnabled(false)
                else if(Build.VERSION.SDK_INT>=33 && !activity.granted(Manifest.permission.POST_NOTIFICATIONS)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else setEnabled(true)
            }) { Text(if(enabled) "Vypnúť upozornenia" else "Zapnúť upozornenia") }
            if(enabled) {
                Text(if(allowed) "Oznámenia sú povolené" else "Android blokuje oznámenia aplikácie",fontSize=12.sp)
                OutlinedButton(onClick={ chooseLead=true }) { Text(if(minutes==0) "Predstih: pri začiatku" else "Predstih: $minutes minút") }
                Row { Checkbox(allDay,{ allDay=it; prefs.edit().putBoolean("remind_all_day",it).apply(); app.scope.launch { app.refreshAgenda() } }); Text("Celodenné udalosti o 9:00",fontSize=13.sp,modifier=Modifier.padding(top=12.dp)) }
                if(exact) Text("Presné pripomenutia sú povolené",fontSize=12.sp)
                else {
                    Text("Pre upozornenie v nastavenom čase povoľ Budíky a pripomenutia. Bez toho môže Android čas posunúť.",fontSize=12.sp)
                    if(Build.VERSION.SDK_INT>=31) OutlinedButton(onClick={ activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${activity.packageName}"))) }) { Text("Povoliť presné pripomenutia") }
                }
                val next=prefs.getLong("next_reminder",0)
                if(next>0) Text("Najbližšie: "+Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. M. HH:mm")),fontSize=12.sp)
                OutlinedButton(onClick={ CalendarReminders.scheduleTest(activity); message="Skúšobné upozornenie je naplánované o 10 sekúnd." },enabled=allowed) { Text("Vyskúšať upozornenie") }
                TextButton(onClick={ activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)) }) { Text("Zvuk a nastavenia oznámení") }
                Text("Rešpektuje režim Nerušiť. Ak upozorňuje aj Google Kalendár alebo Outlook, môžeš dostať dve upozornenia.",fontSize=11.sp)
            }
            if(message!=null) Text(message!!,fontSize=12.sp)
        }
    }
    if(chooseLead) AlertDialog(onDismissRequest={chooseLead=false},title={Text("Kedy pripomenúť udalosť?")},text={Column {
        for(value in listOf(0,5,15,30,60)) TextButton(onClick={minutes=value;chooseLead=false;prefs.edit().putInt("reminder_minutes",value).apply();app.scope.launch {app.refreshAgenda()}},modifier=Modifier.fillMaxWidth()) { Text(if(value==0) "Pri začiatku" else "$value minút pred začiatkom") }
    }},confirmButton={TextButton(onClick={chooseLead=false}) {Text("Zavrieť")}})
}
