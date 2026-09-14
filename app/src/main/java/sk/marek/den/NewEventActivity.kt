package sk.marek.den

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.os.Bundle
import android.provider.CalendarContract.Events
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EventSaveModel : ViewModel() {
    val saving = MutableStateFlow(false)
    val saved = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    fun save(app: DayApp, draft: EventDraft) {
        if (saving.value || saved.value) return
        saving.value = true; error.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val times = eventTimes(draft)
                    val agenda = readAgenda(app)
                    require(agenda.error == null) { agenda.error ?: "Kalendár sa nepodarilo načítať." }
                    require(creationCalendars(agenda.calendars, draft.source).any { it.id == draft.calendarId }) { "Do vybraného kalendára už nemožno zapisovať. Vyber iný." }
                    val values = ContentValues().apply {
                        put(Events.CALENDAR_ID, draft.calendarId); put(Events.TITLE, draft.title.trim())
                        put(Events.DTSTART, times.start); put(Events.DTEND, times.end); put(Events.EVENT_TIMEZONE, times.timezone)
                        put(Events.ALL_DAY, if (draft.allDay) 1 else 0)
                        put(Events.EVENT_LOCATION, draft.location.trim()); put(Events.DESCRIPTION, draft.description.trim())
                    }
                    checkNotNull(app.contentResolver.insert(Events.CONTENT_URI, values)) { "Udalosť sa nepodarilo uložiť." }
                }
                saved.value = true
                app.scope.launch { app.refreshAgenda() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value = if (e is SecurityException) "Povoľ zápis do kalendára a skús znova." else e.message ?: "Uloženie zlyhalo. Skús znova."
            } finally { saving.value = false }
        }
    }
}
class NewEventActivity : ComponentActivity() {
    private val app get() = application as DayApp
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = runCatching { CalendarSource.valueOf(intent.getStringExtra("source").orEmpty()) }.getOrNull()
        if (source == null) { finish(); return }
        val model = ViewModelProvider(this)[EventSaveModel::class.java]
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF315E9E))) { Editor(source, model) }
        }
    }
    @Composable private fun Editor(source: CalendarSource, model: EventSaveModel) {
        var calendars by remember { mutableStateOf<List<DayCalendar>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var calendarId by rememberSaveable { mutableLongStateOf(-1L) }
        var chooseCalendar by rememberSaveable { mutableStateOf(false) }
        var title by rememberSaveable { mutableStateOf("") }
        var location by rememberSaveable { mutableStateOf("") }
        var description by rememberSaveable { mutableStateOf("") }
        val initial = remember { LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) }
        var startText by rememberSaveable { mutableStateOf(initial.toString()) }
        var endText by rememberSaveable { mutableStateOf(initial.plusHours(1).toString()) }
        var allDay by rememberSaveable { mutableStateOf(false) }
        var validation by remember { mutableStateOf<String?>(null) }
        var pendingSave by rememberSaveable { mutableStateOf(false) }
        val saving by model.saving.collectAsStateWithLifecycle()
        val saved by model.saved.collectAsStateWithLifecycle()
        val saveError by model.error.collectAsStateWithLifecycle()
        fun draft() = EventDraft(calendarId, source, title, LocalDateTime.parse(startText), LocalDateTime.parse(endText), allDay, location, description)
        val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingSave && granted) model.save(app, draft())
            else if (pendingSave) validation = "Bez povolenia sa udalosť neuloží. Môžeš ho povoliť pri ďalšom pokuse."
            pendingSave = false
        }
        val readPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recreate() }
        LaunchedEffect(source) {
            val agenda = withContext(Dispatchers.IO) { readAgenda(app) }
            calendars = creationCalendars(agenda.calendars, source)
            loadError = agenda.error; loading = false
            if (calendarId == -1L) {
                val last = getSharedPreferences("day", MODE_PRIVATE).getLong("create_${source.name}", -1)
                calendarId = calendars.firstOrNull { it.id == last }?.id ?: calendars.singleOrNull()?.id ?: -1
            }
        }
        LaunchedEffect(saved) { if (saved) {
            getSharedPreferences("day", MODE_PRIVATE).edit().putLong("create_${source.name}", calendarId).apply()
            Toast.makeText(this@NewEventActivity, "Udalosť uložená do kalendára", Toast.LENGTH_LONG).show(); finish()
        } }
        val selected = calendars.firstOrNull { it.id == calendarId }
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.safeDrawingPadding().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Nová udalosť", fontSize = 28.sp); Text(source.label, color = Color.Gray) }
                item { if (loading) Text("Načítavam kalendáre…")
                    else if (loadError != null) { Text(loadError!!); Button(onClick = { readPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Povoliť čítanie kalendárov") } }
                    else if (calendars.isEmpty()) Text("V tejto skupine nie je pripojený kalendár s povoleným zápisom. Pripoj ho alebo zapni synchronizáciu v nastaveniach účtu.")
                    else OutlinedButton(onClick = { chooseCalendar = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Column { Text(selected?.displayName() ?: "Vybrať kalendár"); if (selected != null) Text(selected.accountName(), fontSize = 11.sp) }
                    }
                }
                item { OutlinedTextField(title, { title = it }, label = { Text("Názov udalosti") }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Row { Checkbox(allDay, { allDay = it }, enabled = !saving); Text("Celý deň", modifier = Modifier.padding(top = 12.dp)) } }
                item { DateTimeRow("Začiatok", LocalDateTime.parse(startText), allDay, !saving) { next ->
                    startText = next.toString()
                    if (!LocalDateTime.parse(endText).isAfter(next)) endText = next.plusHours(1).toString()
                } }
                item { DateTimeRow(if (allDay) "Posledný deň" else "Koniec", LocalDateTime.parse(endText), allDay, !saving) { endText = it.toString() } }
                item { OutlinedTextField(location, { location = it }, label = { Text("Miesto (voliteľné)") }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Poznámka (voliteľná)") }, enabled = !saving, minLines = 2, modifier = Modifier.fillMaxWidth()) }
                item {
                    if (validation != null || saveError != null) Text(validation ?: saveError!!, color = MaterialTheme.colorScheme.error)
                    Text("Uloží sa do vybraného kalendára. Google alebo Outlook ho synchronizuje podľa nastavení účtu.", fontSize = 12.sp)
                    Button(onClick = {
                        validation = runCatching { eventTimes(draft()) }.exceptionOrNull()?.message
                        if (validation == null) {
                            if (granted(Manifest.permission.WRITE_CALENDAR)) model.save(app, draft())
                            else { pendingSave = true; writePermission.launch(Manifest.permission.WRITE_CALENDAR) }
                        }
                    }, enabled = selected != null && !saving && !saved, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Ukladám…" else "Uložiť udalosť") }
                    TextButton(onClick = { finish() }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Zrušiť") }
                }
            }
        }
        if (chooseCalendar) AlertDialog(onDismissRequest = { chooseCalendar = false }, title = { Text("Vybrať kalendár · ${source.label}") },
            text = { LazyColumn { items(calendars, key = { it.id }) { calendar ->
                TextButton(onClick = { calendarId = calendar.id; chooseCalendar = false }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) { Text(calendar.displayName()); Text(calendar.accountName(), fontSize = 11.sp) }
                }
            } } }, confirmButton = { TextButton(onClick = { chooseCalendar = false }) { Text("Zavrieť") } })
    }
    @Composable private fun DateTimeRow(label: String, value: LocalDateTime, allDay: Boolean, enabled: Boolean, changed: (LocalDateTime) -> Unit) {
        Column {
            Text(label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { DatePickerDialog(this@NewEventActivity, { _, y, m, d -> changed(value.withDayOfMonth(1).withYear(y).withMonth(m+1).withDayOfMonth(d)) }, value.year, value.monthValue-1, value.dayOfMonth).show() }, enabled = enabled, modifier = Modifier.weight(1f)) { Text(value.format(DateTimeFormatter.ofPattern("d. M. yyyy"))) }
                if (!allDay) OutlinedButton(onClick = { TimePickerDialog(this@NewEventActivity, { _, h, m -> changed(value.withHour(h).withMinute(m)) }, value.hour, value.minute, true).show() }, enabled = enabled) { Text(value.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            }
        }
    }
}
fun DayCalendar.displayName() = if (name == "calendar_displayname_local") "Kalendár v telefóne" else name
fun DayCalendar.accountName() = if (account == "account_name_local") "Iba v tomto zariadení" else account
