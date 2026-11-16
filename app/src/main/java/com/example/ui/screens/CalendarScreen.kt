package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarViewMode
import com.example.data.model.ClientEntity
import com.example.data.model.ClientStatus
import com.example.data.model.PackageRuleEntity
import com.example.data.model.SessionEntity
import com.example.data.model.SessionStatus
import com.example.data.model.WIB_ZONE
import com.example.data.model.epochMillisToWibDate
import com.example.data.model.formatWibDayDate
import com.example.data.model.formatWibTime
import com.example.ui.components.IhcEmptyState
import com.example.ui.components.IhcHeaderBar
import com.example.ui.components.IhcSessionStatusBadge
import com.example.ui.theme.IhcEmerald
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun CalendarScreen(
    clients: List<ClientEntity>,
    sessions: List<SessionEntity>,
    packageRules: List<PackageRuleEntity>,
    onCreateSession: (
        client: ClientEntity,
        title: String,
        dateWib: LocalDate,
        timeWib: LocalTime,
        durationMinutes: Int,
        location: String,
        notes: String
    ) -> Unit,
    onCreateRecurring: (
        client: ClientEntity,
        title: String,
        firstDateWib: LocalDate,
        timeWib: LocalTime,
        durationMinutes: Int,
        weeksCount: Int,
        location: String,
        notes: String
    ) -> Unit,
    onUpdateSessionStatus: (SessionEntity, SessionStatus) -> Unit,
    onCoachReschedule: (
        session: SessionEntity,
        newDateWib: LocalDate,
        newTimeWib: LocalTime,
        durationMinutes: Int,
        reason: String
    ) -> Unit
) {
    var viewMode by remember { mutableStateOf(CalendarViewMode.HARIAN) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(WIB_ZONE)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var reschedulingSession by remember { mutableStateOf<SessionEntity?>(null) }

    val activeClients = remember(clients) {
        clients.filter { ClientStatus.fromString(it.status) == ClientStatus.AKTIF }
    }

    val filteredSessions = remember(sessions, viewMode, selectedDate) {
        sessions.filter { session ->
            val d = epochMillisToWibDate(session.startAtMillis)
            when (viewMode) {
                CalendarViewMode.HARIAN -> d == selectedDate
                CalendarViewMode.MINGGUAN -> {
                    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weekEnd = weekStart.plusDays(6)
                    !d.isBefore(weekStart) && !d.isAfter(weekEnd)
                }
                CalendarViewMode.BULANAN -> YearMonth.from(d) == YearMonth.from(selectedDate)
            }
        }.sortedBy { it.startAtMillis }
    }

    Scaffold(
        floatingActionButton = {
            if (activeClients.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_session_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah Jadwal Sesi")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            IhcHeaderBar(
                title = "Kalender Latihan",
                subtitle = "WIB (Asia/Jakarta) • Cek bentrok otomatis"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalendarViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("calendar_mode_${mode.name.lowercase()}")
                    )
                }
            }

            // Date Navigator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selectedDate = when (viewMode) {
                        CalendarViewMode.HARIAN -> selectedDate.minusDays(1)
                        CalendarViewMode.MINGGUAN -> selectedDate.minusWeeks(1)
                        CalendarViewMode.BULANAN -> selectedDate.minusMonths(1)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Sebelumnya")
                }

                val label = when (viewMode) {
                    CalendarViewMode.HARIAN -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale("id", "ID")))
                    CalendarViewMode.MINGGUAN -> {
                        val ws = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        val we = ws.plusDays(6)
                        "${ws.dayOfMonth}/${ws.monthValue} - ${we.dayOfMonth}/${we.monthValue}/${we.year}"
                    }
                    CalendarViewMode.BULANAN -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")))
                }
                Text(label, style = MaterialTheme.typography.titleMedium)

                IconButton(onClick = {
                    selectedDate = when (viewMode) {
                        CalendarViewMode.HARIAN -> selectedDate.plusDays(1)
                        CalendarViewMode.MINGGUAN -> selectedDate.plusWeeks(1)
                        CalendarViewMode.BULANAN -> selectedDate.plusMonths(1)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Berikutnya")
                }
            }

            if (viewMode == CalendarViewMode.BULANAN) {
                MonthMiniGrid(
                    month = YearMonth.from(selectedDate),
                    selectedDate = selectedDate,
                    sessions = sessions,
                    onSelectDate = { selectedDate = it }
                )
            }

            if (filteredSessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    IhcEmptyState(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "Tidak ada sesi di rentang ini",
                        description = if (activeClients.isEmpty()) "Tambahkan klien aktif terlebih dahulu." else "Tekan + untuk membuat sesi tunggal atau jadwal rutin."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        val client = clients.firstOrNull { it.id == session.clientId }
                        val rule = client?.let { c -> packageRules.firstOrNull { it.tier == c.packageTier } }
                        val status = SessionStatus.fromString(session.status)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${session.clientName} • ${session.title}", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${formatWibDayDate(session.startAtMillis)} • ${formatWibTime(session.startAtMillis)} - ${formatWibTime(session.endAtMillis)} WIB",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text("Lokasi: ${session.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IhcSessionStatusBadge(status)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(SessionStatus.HADIR, SessionStatus.TIDAK_HADIR).forEach { st ->
                                        FilterChip(
                                            selected = status == st,
                                            onClick = { onUpdateSessionStatus(session, st) },
                                            label = { Text(st.label) }
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (rule?.canReschedule == false) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Basic", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        OutlinedButton(onClick = { reschedulingSession = session }) {
                                            Text("Pindah")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(64.dp)) }
                }
            }
        }
    }

    if (showAddDialog && activeClients.isNotEmpty()) {
        AddSessionDialog(
            clients = activeClients,
            initialDate = selectedDate,
            onDismiss = { showAddDialog = false },
            onCreateSingle = { client, title, date, time, dur, loc, note ->
                onCreateSession(client, title, date, time, dur, loc, note)
                showAddDialog = false
            },
            onCreateRecurring = { client, title, date, time, dur, weeks, loc, note ->
                onCreateRecurring(client, title, date, time, dur, weeks, loc, note)
                showAddDialog = false
            }
        )
    }

    reschedulingSession?.let { session ->
        var dateText by remember { mutableStateOf(epochMillisToWibDate(session.startAtMillis).toString()) }
        var timeText by remember { mutableStateOf(formatWibTime(session.startAtMillis)) }
        var durText by remember { mutableStateOf(((session.endAtMillis - session.startAtMillis) / 60_000L).toString()) }
        var reason by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { reschedulingSession = null },
            title = { Text("Reschedule Sesi ${session.clientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dateText, onValueChange = { dateText = it; err = null }, label = { Text("Tanggal Baru (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = timeText, onValueChange = { timeText = it; err = null }, label = { Text("Jam Baru WIB (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = durText, onValueChange = { durText = it.filter { c -> c.isDigit() } }, label = { Text("Durasi (menit)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Alasan perubahan") }, modifier = Modifier.fillMaxWidth())
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val d = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    val t = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
                    if (d == null || t == null) {
                        err = "Format tanggal YYYY-MM-DD dan jam HH:MM wajib valid."
                        return@Button
                    }
                    onCoachReschedule(session, d, t, durText.toIntOrNull() ?: 60, reason)
                    reschedulingSession = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { reschedulingSession = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun MonthMiniGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    sessions: List<SessionEntity>,
    onSelectDate: (LocalDate) -> Unit
) {
    val sessionDates = remember(sessions) {
        sessions.map { epochMillisToWibDate(it.startAtMillis) }.toSet()
    }
    val firstDayOfMonth = month.atDay(1)
    val startOffset = (firstDayOfMonth.dayOfWeek.value - 1) // Monday = 0
    val daysInMonth = month.lengthOfMonth()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val cellIdx = r * 7 + c
                    val dayNum = cellIdx - startOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = month.atDay(dayNum)
                        val isSelected = date == selectedDate
                        val hasSession = date in sessionDates
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { onSelectDate(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasSession) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else IhcEmerald)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(36.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionDialog(
    clients: List<ClientEntity>,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onCreateSingle: (ClientEntity, String, LocalDate, LocalTime, Int, String, String) -> Unit,
    onCreateRecurring: (ClientEntity, String, LocalDate, LocalTime, Int, Int, String, String) -> Unit
) {
    var selectedClient by remember { mutableStateOf(clients.first()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Sesi Latihan") }
    var dateText by remember { mutableStateOf(initialDate.toString()) }
    var timeText by remember { mutableStateOf("07:00") }
    var durationText by remember { mutableStateOf("60") }
    var location by remember { mutableStateOf("IHC Studio") }
    var notes by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var weeksCountText by remember { mutableStateOf("4") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jadwalkan Sesi Baru") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedClient.fullName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Klien") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        clients.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.fullName} (${c.packageTier})") },
                                onClick = {
                                    selectedClient = c
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Judul Sesi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dateText, onValueChange = { dateText = it; err = null }, label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1.3f))
                    OutlinedTextField(value = timeText, onValueChange = { timeText = it; err = null }, label = { Text("Jam WIB") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = durationText, onValueChange = { durationText = it.filter { c -> c.isDigit() } }, label = { Text("Durasi (mnt)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Lokasi") }, singleLine = true, modifier = Modifier.weight(1.4f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text("Jadwal Rutin Mingguan", style = MaterialTheme.typography.bodyMedium)
                }
                if (isRecurring) {
                    OutlinedTextField(
                        value = weeksCountText,
                        onValueChange = { weeksCountText = it.filter { c -> c.isDigit() } },
                        label = { Text("Jumlah Minggu (1-16)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth())
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    val t = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
                    if (d == null || t == null) {
                        err = "Gunakan format YYYY-MM-DD dan HH:MM (WIB)."
                        return@Button
                    }
                    val dur = (durationText.toIntOrNull() ?: 60).coerceIn(15, 240)
                    if (isRecurring) {
                        val weeks = (weeksCountText.toIntOrNull() ?: 4).coerceIn(1, 16)
                        onCreateRecurring(selectedClient, title, d, t, dur, weeks, location, notes)
                    } else {
                        onCreateSingle(selectedClient, title, d, t, dur, location, notes)
                    }
                },
                modifier = Modifier.testTag("session_save_btn")
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
