package sk.marek.den

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class DayActivity : ComponentActivity() {
    private val app get() = application as DayApp
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF315E9E), background = Color(0xFFF3F5FA))) { DaySettings() } }
    }
    override fun onResume() { super.onResume(); app.scope.launch { app.refresh(true) } }
    @Composable private fun DaySettings() {
        val state by app.state.collectAsStateWithLifecycle()
        val prefs = remember { getSharedPreferences("day", MODE_PRIVATE) }
        var hidden by remember { mutableStateOf(prefs.getStringSet("hidden_calendars", emptySet())!!.toSet()) }
        var message by remember { mutableStateOf<String?>(null) }
        val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.scope.launch { app.refreshAgenda() } }
        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.scope.launch { app.refresh(true) } }
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.safeDrawingPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("Deň", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF162C49)); Text("Všetko podstatné na tvojej ploche", color = Color.Gray) }
                item { Button(onClick = { DayWidget.requestPin(this@DayActivity); message = "Ak sa widget nepridal: podrž plochu → Widgety → Widgety systému Android → Deň · všetko podstatné" }, modifier = Modifier.fillMaxWidth()) { Text("Pridať widget na plochu") } }
                if (message != null) item { Text(message!!) }
                item { OverviewSettings(this@DayActivity, state) }
                item { ReminderSettings(this@DayActivity, state) }
                item { Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Počasie podľa polohy", fontWeight = FontWeight.Bold)
                    Text("Poloha sa zaokrúhli približne na 1 km a odošle Open-Meteo pre počasie a systémovej službe pre názov mesta. Kalendáre zostávajú v mobile. História polohy sa neukladá.", fontSize = 13.sp)
                    Text(state.weather?.let { "${it.city} · ${it.temperature} °C · ${weatherText(it.code)}" } ?: state.weatherError ?: "Poloha zatiaľ nie je nastavená")
                    Button(onClick = {
                        prefs.edit().putBoolean("weather_enabled", true).apply()
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }) { Text(if (hasLocation()) "Poloha povolená · obnoviť" else "Povoliť polohu pre počasie") }
                    Text("Pre zmenu mesta aj pri zatvorenej appke povoľ v systéme Poloha → Povoliť vždy. Obnova prebieha približne každých 30 minút podľa obmedzení Androidu.", fontSize = 12.sp)
                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }) {
                        Text(if (granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) "Poloha na pozadí povolená" else "Nastaviť polohu na pozadí")
                    }
                    TextButton(onClick = { prefs.edit().putBoolean("weather_enabled", false).remove("weather").apply(); app.scope.launch { app.refresh(true) } }) { Text("Vypnúť počasie a zmazať uloženú polohu") }
                } } }
                item { Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Stretnutia z kalendárov", fontWeight = FontWeight.Bold)
                    Text("Deň číta názov a čas udalostí z kalendárov synchronizovaných v Androide. Nečíta e-maily. Novú udalosť zapíše iba po stlačení Uložiť.", fontSize = 13.sp)
                    Button(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text(if (granted(Manifest.permission.READ_CALENDAR)) "Čítanie kalendárov povolené" else "Povoliť čítanie kalendárov") }
                    if (state.agenda.error != null) Text(state.agenda.error!!)
                    Text("Outlook: Nastavenia účtu → Kalendár → Synchronizovať kalendáre. Google: zapni synchronizáciu kalendára pri svojom účte.", fontSize = 12.sp)
                    TextButton(onClick = { packageManager.getLaunchIntentForPackage("com.microsoft.office.outlook")?.let(::startActivity) }) { Text("Otvoriť Outlook") }
                } } }
                items(state.agenda.calendars, key = { it.id }) { calendar ->
                    Card { Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Checkbox(calendar.id.toString() !in hidden, onCheckedChange = { checked ->
                            hidden = if (checked) hidden - calendar.id.toString() else hidden + calendar.id.toString()
                            prefs.edit().putStringSet("hidden_calendars", hidden).apply(); app.scope.launch { app.refreshAgenda() }
                        })
                        Column(Modifier.weight(1f)) { Text(calendar.name, fontWeight = FontWeight.SemiBold); Text("${calendar.source.label} · ${calendar.account}", fontSize = 11.sp) }
                    } }
                }
                item { OutlinedButton(onClick = { app.scope.launch { app.refresh(true) } }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "Načítavam…" else "Obnoviť widget a udalosti") } }
                item { Text("Meniny dnes: ${nameday(this@DayActivity)}", fontWeight = FontWeight.SemiBold)
                    Text("Počasie: Open-Meteo (CC BY 4.0). Meniny: name-day-calendar / Peter Knežek (MIT). Widget zobrazuje najbližšiu udalosť z každej skupiny v nasledujúcich 14 dňoch. Čas sa mení priamo na ploche.", fontSize = 11.sp)
                    Text("Deň 0.7 · vytvorené pre Mareka", fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }
}
