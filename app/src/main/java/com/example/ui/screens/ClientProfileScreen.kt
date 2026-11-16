package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.BodyCompositionEntity
import com.example.data.model.ClientEntity
import com.example.data.model.ClientStatus
import com.example.data.model.ClientTaskScheduleEntity
import com.example.data.model.MealPlanNoteEntity
import com.example.data.model.MeasurementEntity
import com.example.data.model.PackageRuleEntity
import com.example.data.model.PackageTier
import com.example.data.model.PhotoAngle
import com.example.data.model.ProgressPhotoEntity
import com.example.data.model.RequestStatus
import com.example.data.model.SessionEntity
import com.example.data.model.SessionStatus
import com.example.data.model.TaskType
import com.example.data.model.WIB_ZONE
import com.example.data.model.WorkoutExerciseEntity
import com.example.data.model.WorkoutRequestEntity
import com.example.data.model.formatWibDate
import com.example.data.model.formatWibDateTime
import com.example.ui.components.IhcEmptyState
import com.example.ui.components.IhcMetricChart
import com.example.ui.components.IhcPackageBadge
import com.example.ui.components.IhcRequestStatusBadge
import com.example.ui.components.IhcRestTimerCard
import com.example.ui.components.IhcSessionStatusBadge
import com.example.ui.theme.IhcDangerRed
import com.example.ui.theme.IhcEmerald
import com.example.ui.viewmodel.IhcViewModel
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientProfileScreen(
    clientId: String,
    initialTabIndex: Int,
    viewModel: IhcViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val rules by viewModel.packageRules.collectAsStateWithLifecycle()
    val allSessions by viewModel.sessions.collectAsStateWithLifecycle()
    val allWorkoutRequests by viewModel.workoutRequests.collectAsStateWithLifecycle()

    val client = clients.firstOrNull { it.id == clientId }
    if (client == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profil Klien") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                )
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Data klien tidak ditemukan atau telah diarsipkan.")
            }
        }
        return
    }

    val tier = PackageTier.fromString(client.packageTier)
    val rule = rules.firstOrNull { it.tier == client.packageTier }
        ?: PackageRuleEntity.defaults().first { it.tier == client.packageTier }

    val measurements by viewModel.observeMeasurements(clientId).collectAsStateWithLifecycle(emptyList())
    val bodyComps by viewModel.observeBodyCompositions(clientId).collectAsStateWithLifecycle(emptyList())
    val photos by viewModel.observePhotos(clientId).collectAsStateWithLifecycle(emptyList())
    val workouts by viewModel.observeWorkouts(clientId).collectAsStateWithLifecycle(emptyList())
    val mealNotes by viewModel.observeMealPlanNotes(clientId).collectAsStateWithLifecycle(emptyList())
    val taskSchedules by viewModel.observeTaskSchedulesForClient(clientId).collectAsStateWithLifecycle(emptyList())

    val clientSessions = remember(allSessions, clientId) {
        allSessions.filter { it.clientId == clientId }.sortedByDescending { it.startAtMillis }
    }
    val clientWorkoutRequests = remember(allWorkoutRequests, clientId) {
        allWorkoutRequests.filter { it.clientId == clientId }
    }

    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex.coerceIn(0, 5)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Info & Paket",
        "Pengukuran",
        "Foto Progres",
        "Workout & Meal",
        "Jadwal",
        "Jadwal Input"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(client.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = client.emailNormalized,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IhcPackageBadge(tier = tier, modifier = Modifier.padding(end = 8.dp))
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("profile_edit_btn")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Klien")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title) },
                        modifier = Modifier.testTag("profile_tab_$idx")
                    )
                }
            }

            when (selectedTab) {
                0 -> ClientInfoTab(
                    client = client,
                    tier = tier,
                    rule = rule,
                    sessions = clientSessions,
                    onEditClick = { showEditDialog = true },
                    onArchiveClick = { showArchiveConfirm = true }
                )
                1 -> ClientMeasurementTab(
                    rule = rule,
                    measurements = measurements,
                    bodyComps = bodyComps,
                    onAddMeasurement = { waist, neck, arm, weight, notes ->
                        viewModel.addMeasurement(clientId, waist, neck, arm, weight, notes)
                    },
                    onAddBodyComp = { bf, mm, age, notes ->
                        viewModel.addBodyComposition(clientId, bf, mm, age, notes)
                    }
                )
                2 -> ClientPhotosTab(
                    rule = rule,
                    photos = photos,
                    onUploadPhoto = { angle, uri, notes ->
                        viewModel.addProgressPhoto(clientId, angle, uri, notes)
                    }
                )
                3 -> ClientWorkoutMealTab(
                    rule = rule,
                    workouts = workouts,
                    mealNotes = mealNotes,
                    workoutRequests = clientWorkoutRequests,
                    onAddExercise = { day, name, sets, reps, load, rest, note ->
                        viewModel.addWorkoutExercise(clientId, day, name, sets, reps, load, rest, note)
                    },
                    onDeleteExercise = { viewModel.deleteWorkoutExercise(it) },
                    onAddMealNote = { title, note -> viewModel.addMealPlanNote(clientId, title, note) },
                    onReplyWorkoutRequest = { req, status, reply ->
                        viewModel.replyWorkoutRequest(req, status, reply)
                    }
                )
                4 -> ClientSessionsTab(
                    rule = rule,
                    sessions = clientSessions,
                    onUpdateStatus = { session, status -> viewModel.updateSessionStatus(session, status) }
                )
                5 -> ClientInputScheduleTab(
                    schedules = taskSchedules,
                    onJumpToTab = { tabIdx -> selectedTab = tabIdx },
                    onUpdateSchedule = { sched, dueMillis, note ->
                        viewModel.updateTaskSchedule(sched, dueMillis, note)
                    }
                )
            }
        }
    }

    if (showEditDialog) {
        ClientFormDialog(
            initialClient = client,
            packageRules = rules,
            onDismiss = { showEditDialog = false },
            onConfirm = { fullName, email, phone, newTier, status, goal, startMillis, endMillis, quota, notes ->
                viewModel.saveClient(
                    existingId = client.id,
                    fullName = fullName,
                    email = email,
                    phone = phone,
                    packageTier = newTier,
                    status = status,
                    goal = goal,
                    startDateMillis = startMillis,
                    endDateMillis = endMillis,
                    totalSessionsQuota = quota,
                    notes = notes
                )
                showEditDialog = false
            }
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Arsipkan Klien?") },
            text = {
                Text("${client.fullName} akan diarsipkan (soft delete). Riwayat pengukuran dan sesi tetap aman tersimpan di cadangan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveConfirm = false
                        viewModel.softDeleteClient(client, onDone = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IhcDangerRed)
                ) {
                    Text("Arsipkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ClientInfoTab(
    client: ClientEntity,
    tier: PackageTier,
    rule: PackageRuleEntity,
    sessions: List<SessionEntity>,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit
) {
    val attended = sessions.count { SessionStatus.fromString(it.status) == SessionStatus.HADIR }
    val quota = client.totalSessionsQuota.coerceAtLeast(1)
    val progress = (attended.toFloat() / quota.toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pelacakan Paket (Package Tracking)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$attended dari $quota sesi terpakai • Status: ${ClientStatus.fromString(client.status).label}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = IhcEmerald
                    )
                    Text(
                        text = "Periode: ${formatWibDate(client.startDateMillis)} — ${formatWibDate(client.endDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Identitas & Portal Klien", style = MaterialTheme.typography.titleMedium)
                    Text("Email Portal Web: ${client.emailNormalized}", style = MaterialTheme.typography.bodyMedium)
                    if (client.phone.isNotBlank()) Text("No. HP: ${client.phone}", style = MaterialTheme.typography.bodyMedium)
                    if (client.goal.isNotBlank()) Text("Tujuan: ${client.goal}", style = MaterialTheme.typography.bodyMedium)
                    if (client.notes.isNotBlank()) {
                        Text(
                            "Catatan: ${client.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fasilitas Aktif (${tier.label})", style = MaterialTheme.typography.titleMedium)
                    Text("• Kapasitas pertemuan: maks ${rule.maxSessionsPerWeek}x / minggu", style = MaterialTheme.typography.bodySmall)
                    Text("• Reschedule: ${if (rule.canReschedule) "Diizinkan" else "Terkunci (Basic)"}", style = MaterialTheme.typography.bodySmall)
                    Text("• Foto Progres: ${rule.allowedAngles().joinToString { it.label }} (tiap ${rule.photoFrequencyDays} hari)", style = MaterialTheme.typography.bodySmall)
                    Text("• Lingkar: ${rule.measurementFieldsCsv} (tiap ${rule.measurementFrequencyDays} hari)", style = MaterialTheme.typography.bodySmall)
                    Text("• Komposisi Tubuh: ${if (rule.hasBodyComposition) "Aktif (tiap ${rule.bodyCompositionFrequencyDays} hari)" else "Tidak termasuk"}", style = MaterialTheme.typography.bodySmall)
                    Text("• Meal Plan: ${if (rule.hasMealPlan) "Update tiap ${rule.mealPlanFrequencyDays} hari" else "Tidak termasuk"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onEditClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ubah Data / Paket")
                }
                OutlinedButton(
                    onClick = onArchiveClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IhcDangerRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Arsipkan")
                }
            }
        }
    }
}

@Composable
private fun ClientMeasurementTab(
    rule: PackageRuleEntity,
    measurements: List<MeasurementEntity>,
    bodyComps: List<BodyCompositionEntity>,
    onAddMeasurement: (waist: Double, neck: Double?, arm: Double?, weight: Double?, notes: String) -> Unit,
    onAddBodyComp: (bf: Double, muscle: Double, age: Int, notes: String) -> Unit
) {
    val allowedFields = rule.allowedMeasurementFields()
    val showNeck = "LEHER" in allowedFields
    val showArm = "LENGAN" in allowedFields

    var showMeasureDialog by remember { mutableStateOf(false) }
    var showBodyCompDialog by remember { mutableStateOf(false) }

    val sortedMeasurementsAsc = remember(measurements) { measurements.sortedBy { it.recordedAt } }
    val sortedBodyAsc = remember(bodyComps) { bodyComps.sortedBy { it.recordedAt } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showMeasureDialog = true },
                    modifier = Modifier.weight(1f).testTag("add_measurement_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Catat Lingkar")
                }
                if (rule.hasBodyComposition) {
                    Button(
                        onClick = { showBodyCompDialog = true },
                        modifier = Modifier.weight(1f).testTag("add_bodycomp_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Komposisi Tubuh")
                    }
                }
            }
        }

        // Package-filtered trend charts
        item {
            IhcMetricChart(
                title = "Tren Lingkar Perut",
                unit = "cm",
                values = sortedMeasurementsAsc.map { it.waistCm }
            )
        }
        if (showNeck) {
            item {
                IhcMetricChart(
                    title = "Tren Lingkar Leher",
                    unit = "cm",
                    values = sortedMeasurementsAsc.mapNotNull { it.neckCm }
                )
            }
        }
        if (showArm) {
            item {
                IhcMetricChart(
                    title = "Tren Lingkar Lengan",
                    unit = "cm",
                    values = sortedMeasurementsAsc.mapNotNull { it.armCm }
                )
            }
        }
        if (rule.hasBodyComposition) {
            item {
                IhcMetricChart(
                    title = "Tren Body Fat %",
                    unit = "%",
                    values = sortedBodyAsc.map { it.bodyFatPercent }
                )
            }
            item {
                IhcMetricChart(
                    title = "Tren Massa Otot",
                    unit = "kg",
                    values = sortedBodyAsc.map { it.muscleMassKg }
                )
            }
        }

        if (measurements.isEmpty() && (!rule.hasBodyComposition || bodyComps.isEmpty())) {
            item {
                IhcEmptyState(
                    icon = Icons.Outlined.Straighten,
                    title = "Belum ada riwayat pengukuran",
                    description = "Kolom input menyesuaikan paket secara otomatis."
                )
            }
        } else {
            item {
                Text("Riwayat Pengukuran Lingkar", style = MaterialTheme.typography.titleMedium)
            }
            items(measurements, key = { it.id }) { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(formatWibDateTime(m.recordedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        val parts = buildList {
                            add("Perut: ${m.waistCm} cm")
                            if (showNeck && m.neckCm != null) add("Leher: ${m.neckCm} cm")
                            if (showArm && m.armCm != null) add("Lengan: ${m.armCm} cm")
                            if (m.weightKg != null) add("BB: ${m.weightKg} kg")
                        }
                        Text(parts.joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                        if (m.notes.isNotBlank()) Text(m.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (rule.hasBodyComposition && bodyComps.isNotEmpty()) {
                item {
                    Text("Riwayat Komposisi Tubuh (Premium)", style = MaterialTheme.typography.titleMedium)
                }
                items(bodyComps, key = { it.id }) { b ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(formatWibDateTime(b.recordedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Body Fat: ${b.bodyFatPercent}% • Massa Otot: ${b.muscleMassKg} kg • Umur Tubuh: ${b.bodyAgeYears} thn",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (b.notes.isNotBlank()) Text(b.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showMeasureDialog) {
        var waistText by remember { mutableStateOf("") }
        var neckText by remember { mutableStateOf("") }
        var armText by remember { mutableStateOf("") }
        var weightText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showMeasureDialog = false },
            title = { Text("Input Lingkar Tubuh") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = waistText,
                        onValueChange = { waistText = it; err = null },
                        label = { Text("Lingkar Perut (cm)*") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("measure_waist_input")
                    )
                    if (showNeck) {
                        OutlinedTextField(
                            value = neckText,
                            onValueChange = { neckText = it },
                            label = { Text("Lingkar Leher (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("measure_neck_input")
                        )
                    }
                    if (showArm) {
                        OutlinedTextField(
                            value = armText,
                            onValueChange = { armText = it },
                            label = { Text("Lingkar Lengan (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("measure_arm_input")
                        )
                    }
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Berat Badan (kg, opsional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val waist = waistText.replace(',', '.').toDoubleOrNull()
                        if (waist == null || waist <= 0) {
                            err = "Masukkan angka lingkar perut yang valid."
                            return@Button
                        }
                        onAddMeasurement(
                            waist,
                            if (showNeck) neckText.replace(',', '.').toDoubleOrNull() else null,
                            if (showArm) armText.replace(',', '.').toDoubleOrNull() else null,
                            weightText.replace(',', '.').toDoubleOrNull(),
                            notes
                        )
                        showMeasureDialog = false
                    },
                    modifier = Modifier.testTag("measure_save_btn")
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showMeasureDialog = false }) { Text("Batal") } }
        )
    }

    if (showBodyCompDialog) {
        var bfText by remember { mutableStateOf("") }
        var mmText by remember { mutableStateOf("") }
        var ageText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showBodyCompDialog = false },
            title = { Text("Input Komposisi Tubuh") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bfText,
                        onValueChange = { bfText = it; err = null },
                        label = { Text("Body Fat (%)*") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mmText,
                        onValueChange = { mmText = it; err = null },
                        label = { Text("Massa Otot (kg)*") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter { ch -> ch.isDigit() }; err = null },
                        label = { Text("Umur Tubuh (tahun)*") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val bf = bfText.replace(',', '.').toDoubleOrNull()
                    val mm = mmText.replace(',', '.').toDoubleOrNull()
                    val age = ageText.toIntOrNull()
                    if (bf == null || mm == null || age == null) {
                        err = "Lengkapi Body Fat %, Massa Otot, dan Umur Tubuh."
                        return@Button
                    }
                    onAddBodyComp(bf, mm, age, notes)
                    showBodyCompDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showBodyCompDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ClientPhotosTab(
    rule: PackageRuleEntity,
    photos: List<ProgressPhotoEntity>,
    onUploadPhoto: (PhotoAngle, Uri, String) -> Unit
) {
    val allowedAngles = rule.allowedAngles()
    var selectedUploadAngle by remember(allowedAngles) { mutableStateOf(allowedAngles.first()) }
    var filterAngle by remember { mutableStateOf<PhotoAngle?>(null) }
    var pendingNote by remember { mutableStateOf("") }
    var zoomedPhoto by remember { mutableStateOf<ProgressPhotoEntity?>(null) }

    // Zero-permission Android Photo Picker per Play policy
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadPhoto(selectedUploadAngle, uri, pendingNote)
            pendingNote = ""
        }
    }

    val displayedPhotos = remember(photos, filterAngle, allowedAngles) {
        photos.filter { p ->
            val angle = PhotoAngle.fromString(p.angle)
            angle in allowedAngles && (filterAngle == null || angle == filterAngle)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Unggah Foto Progres (File Asli + Pratinjau)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allowedAngles.forEach { angle ->
                            FilterChip(
                                selected = selectedUploadAngle == angle,
                                onClick = { selectedUploadAngle = angle },
                                label = { Text(angle.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = pendingNote,
                        onValueChange = { pendingNote = it },
                        label = { Text("Catatan foto (opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("upload_progress_photo_btn")
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pilih Foto (${selectedUploadAngle.label})")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterAngle == null,
                    onClick = { filterAngle = null },
                    label = { Text("Semua Sudut") }
                )
                allowedAngles.forEach { angle ->
                    FilterChip(
                        selected = filterAngle == angle,
                        onClick = { filterAngle = angle },
                        label = { Text(angle.label) }
                    )
                }
            }
        }

        if (displayedPhotos.isEmpty()) {
            item {
                IhcEmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Belum ada foto progres",
                    description = "Foto asli tersimpan aman & pratinjau terkompresi siap untuk portal web."
                )
            }
        } else {
            items(displayedPhotos, key = { it.id }) { photo ->
                val angle = PhotoAngle.fromString(photo.angle)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { zoomedPhoto = photo },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = File(photo.localOriginalPath),
                            contentDescription = "Foto progres ${angle.label}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(angle.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                formatWibDateTime(photo.takenAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (photo.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(photo.notes, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Ketuk untuk perbesar",
                                style = MaterialTheme.typography.labelSmall,
                                color = IhcEmerald
                            )
                        }
                    }
                }
            }
        }
    }

    zoomedPhoto?.let { photo ->
        Dialog(onDismissRequest = { zoomedPhoto = null }) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(PhotoAngle.fromString(photo.angle).label, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { zoomedPhoto = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup")
                        }
                    }
                    AsyncImage(
                        model = File(photo.localOriginalPath),
                        contentDescription = "Foto Progres Zoom",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(formatWibDateTime(photo.takenAt), style = MaterialTheme.typography.bodySmall)
                    if (photo.notes.isNotBlank()) Text(photo.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ClientWorkoutMealTab(
    rule: PackageRuleEntity,
    workouts: List<WorkoutExerciseEntity>,
    mealNotes: List<MealPlanNoteEntity>,
    workoutRequests: List<WorkoutRequestEntity>,
    onAddExercise: (day: String, name: String, sets: Int, reps: String, load: String, rest: Int, note: String) -> Unit,
    onDeleteExercise: (WorkoutExerciseEntity) -> Unit,
    onAddMealNote: (title: String, note: String) -> Unit,
    onReplyWorkoutRequest: (WorkoutRequestEntity, RequestStatus, String) -> Unit
) {
    var activeTimerSeconds by remember { mutableIntStateOf(60) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showAddMealDialog by remember { mutableStateOf(false) }
    var replyingRequest by remember { mutableStateOf<WorkoutRequestEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            IhcRestTimerCard(defaultSeconds = activeTimerSeconds)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAddExerciseDialog = true },
                    modifier = Modifier.weight(1f).testTag("add_workout_exercise_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah Gerakan")
                }
                if (rule.hasMealPlan) {
                    OutlinedButton(
                        onClick = { showAddMealDialog = true },
                        modifier = Modifier.weight(1f).testTag("add_meal_note_btn")
                    ) {
                        Icon(Icons.Outlined.RestaurantMenu, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Update Meal Plan")
                    }
                }
            }
        }

        if (workouts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "Belum ada gerakan di program workout klien ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(workouts, key = { it.id }) { ex ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${ex.programDay} • ${ex.exerciseName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${ex.sets} set × ${ex.reps} • Istirahat ${ex.restSeconds}d" +
                                    if (ex.loadNote.isNotBlank()) " • ${ex.loadNote}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ex.coachNotes.isNotBlank()) {
                                Text(ex.coachNotes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { activeTimerSeconds = ex.restSeconds }) {
                            Text("${ex.restSeconds}d")
                        }
                        IconButton(onClick = { onDeleteExercise(ex) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Gerakan")
                        }
                    }
                }
            }
        }

        if (rule.hasMealPlan) {
            item {
                Text("Catatan Update Meal Plan (tiap ${rule.mealPlanFrequencyDays} hari)", style = MaterialTheme.typography.titleMedium)
            }
            if (mealNotes.isEmpty()) {
                item {
                    Text(
                        "Belum ada catatan update Meal Plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(mealNotes, key = { it.id }) { note ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall)
                            Text(formatWibDateTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (note.note.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(note.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Paket Basic tidak termasuk Meal Plan.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (workoutRequests.isNotEmpty()) {
            item {
                Text("Request Gerakan Workout dari Klien", style = MaterialTheme.typography.titleMedium)
            }
            items(workoutRequests, key = { it.id }) { req ->
                val st = RequestStatus.fromString(req.status)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(req.requestedExercise, style = MaterialTheme.typography.titleSmall)
                            IhcRequestStatusBadge(st)
                        }
                        if (req.reason.isNotBlank()) Text("Alasan: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                        if (req.coachReply.isNotBlank()) Text("Balasan Coach: ${req.coachReply}", style = MaterialTheme.typography.bodySmall, color = IhcEmerald)
                        TextButton(onClick = { replyingRequest = req }) {
                            Text("Balas / Ubah Status")
                        }
                    }
                }
            }
        }
    }

    if (showAddExerciseDialog) {
        var day by remember { mutableStateOf("Hari 1") }
        var name by remember { mutableStateOf("") }
        var setsText by remember { mutableStateOf("3") }
        var reps by remember { mutableStateOf("10-12") }
        var load by remember { mutableStateOf("") }
        var restText by remember { mutableStateOf("60") }
        var note by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            title = { Text("Tambah Gerakan Workout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Sesi / Hari") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it; err = null }, label = { Text("Nama Gerakan*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = setsText, onValueChange = { setsText = it.filter { c -> c.isDigit() } }, label = { Text("Set") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = restText, onValueChange = { restText = it.filter { c -> c.isDigit() } }, label = { Text("Rest (d)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = load, onValueChange = { load = it }, label = { Text("Target Beban (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Catatan teknik") }, modifier = Modifier.fillMaxWidth())
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isBlank()) {
                        err = "Nama gerakan wajib diisi."
                        return@Button
                    }
                    onAddExercise(
                        day,
                        name,
                        setsText.toIntOrNull() ?: 3,
                        reps,
                        load,
                        restText.toIntOrNull() ?: 60,
                        note
                    )
                    showAddExerciseDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddExerciseDialog = false }) { Text("Batal") } }
        )
    }

    if (showAddMealDialog) {
        var title by remember { mutableStateOf("Update Meal Plan") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMealDialog = false },
            title = { Text("Catat Update Meal Plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Catatan perubahan / arahan") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddMealNote(title, note)
                    showAddMealDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddMealDialog = false }) { Text("Batal") } }
        )
    }

    replyingRequest?.let { req ->
        var replyText by remember { mutableStateOf(req.coachReply) }
        var selectedStatus by remember { mutableStateOf(RequestStatus.DISETUJUI) }
        AlertDialog(
            onDismissRequest = { replyingRequest = null },
            title = { Text("Balas Request Gerakan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(req.requestedExercise, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(RequestStatus.DISETUJUI, RequestStatus.DIBALAS, RequestStatus.DITOLAK).forEach { st ->
                            FilterChip(selected = selectedStatus == st, onClick = { selectedStatus = st }, label = { Text(st.label) })
                        }
                    }
                    OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Balasan Coach") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onReplyWorkoutRequest(req, selectedStatus, replyText)
                    replyingRequest = null
                }) { Text("Kirim") }
            },
            dismissButton = { TextButton(onClick = { replyingRequest = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ClientSessionsTab(
    rule: PackageRuleEntity,
    sessions: List<SessionEntity>,
    onUpdateStatus: (SessionEntity, SessionStatus) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = if (rule.canReschedule) {
                        "Maks ${rule.maxSessionsPerWeek}x/minggu • Klien dapat mengajukan reschedule lewat portal web."
                    } else {
                        "Maks ${rule.maxSessionsPerWeek}x/minggu • Reschedule terkunci (Paket Basic)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
        if (sessions.isEmpty()) {
            item {
                IhcEmptyState(
                    icon = Icons.Outlined.Schedule,
                    title = "Belum ada sesi terjadwal",
                    description = "Buat jadwal sesi dari menu Kalender."
                )
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                val status = SessionStatus.fromString(session.status)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.title, style = MaterialTheme.typography.titleSmall)
                            IhcSessionStatusBadge(status)
                        }
                        Text("${formatWibDateTime(session.startAtMillis)} • ${session.location}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(SessionStatus.HADIR, SessionStatus.TIDAK_HADIR, SessionStatus.DIBATALKAN).forEach { st ->
                                FilterChip(
                                    selected = status == st,
                                    onClick = { onUpdateStatus(session, st) },
                                    label = { Text(st.label) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientInputScheduleTab(
    schedules: List<ClientTaskScheduleEntity>,
    onJumpToTab: (Int) -> Unit,
    onUpdateSchedule: (ClientTaskScheduleEntity, Long?, String) -> Unit
) {
    var editingSchedule by remember { mutableStateOf<ClientTaskScheduleEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Pantauan Jadwal Input Klien",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Otomatis menyesuaikan paket. Gunakan tombol pintas untuk langsung mengisi data terkait.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(schedules, key = { it.id }) { sched ->
            val taskType = TaskType.fromString(sched.taskType)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (sched.enabled) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(taskType.label, style = MaterialTheme.typography.titleSmall)
                        if (!sched.enabled) {
                            Text("Tidak aktif di paket", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (sched.enabled) {
                        val nextText = sched.nextDueMillis?.let { formatWibDate(it) } ?: "Fleksibel (belum dijadwalkan)"
                        val lastText = sched.lastCompletedMillis?.let { formatWibDate(it) } ?: "Belum pernah"
                        Text("Target berikutnya: $nextText", style = MaterialTheme.typography.bodySmall)
                        Text("Terakhir dikerjakan: $lastText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (sched.notes.isNotBlank()) {
                            Text("Catatan: ${sched.notes}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editingSchedule = sched }) {
                                Text("Atur Target")
                            }
                            Button(onClick = { onJumpToTab(taskType.targetTabIndex) }) {
                                Text("Buka Input")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    editingSchedule?.let { sched ->
        var daysFromNowText by remember { mutableStateOf("7") }
        var noteText by remember { mutableStateOf(sched.notes) }
        AlertDialog(
            onDismissRequest = { editingSchedule = null },
            title = { Text("Atur Target ${TaskType.fromString(sched.taskType).label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = daysFromNowText,
                        onValueChange = { daysFromNowText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target dalam (hari dari hari ini)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val days = daysFromNowText.toLongOrNull()
                    val targetMillis = days?.let {
                        LocalDate.now(WIB_ZONE).plusDays(it).atStartOfDay(WIB_ZONE).toInstant().toEpochMilli()
                    }
                    onUpdateSchedule(sched, targetMillis, noteText)
                    editingSchedule = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editingSchedule = null }) { Text("Batal") } }
        )
    }
}
