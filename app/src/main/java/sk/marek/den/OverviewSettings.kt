package sk.marek.den

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun OverviewSettings(activity: ComponentActivity, state: DayState) {
    val app = activity.application as DayApp
    var enabled by remember { mutableStateOf(LockscreenOverview.enabled(activity)) }
    fun setEnabled(value: Boolean) {
        enabled = value
        activity.getSharedPreferences("day", 0).edit().putBoolean("lockscreen_overview", value).apply()
        LockscreenOverview.update(activity, state)
        if (value) app.scope.launch { app.refresh(true) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { setEnabled(it) }
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Prehľad na zamknutej obrazovke", fontWeight = FontWeight.Bold)
            Text("Tiché oznámenie s počasím, budíkom, meninami a Google/Outlook udalosťami. Názvy udalostí budú viditeľné aj bez odomknutia, ak to systém povoľuje.", fontSize = 13.sp)
            Button(onClick = {
                if (enabled) setEnabled(false)
                else if (Build.VERSION.SDK_INT >= 33 && !activity.granted(Manifest.permission.POST_NOTIFICATIONS)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else setEnabled(true)
            }) { Text(if (enabled) "Vypnúť prehľad" else "Zapnúť prehľad") }
            if (enabled) {
                Text(if (LockscreenOverview.allowed(activity)) "Prehľad je zapnutý. Rozbaľ oznámenie pre oba kalendáre." else "Systém blokuje oznámenia prehľadu.", fontSize = 12.sp)
                TextButton(onClick = { activity.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, LockscreenOverview.CHANNEL)) }) { Text("Zobrazenie na zamknutej obrazovke") }
            }
            Text("Zobrazí sa po rozsvietení displeja. Obnova približne každých 30 minút podľa Androidu alebo tlačidlom Obnoviť. Nenahrádza Always-on displej.", fontSize = 11.sp)
        }
    }
}
