package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ClientEntity
import com.example.data.model.ClientStatus
import com.example.data.model.PackageRuleEntity
import com.example.data.model.PackageTier
import com.example.data.model.SessionEntity
import com.example.data.model.SessionStatus
import com.example.data.model.WIB_ZONE
import com.example.data.model.formatWibDate
import com.example.ui.components.IhcEmptyState
import com.example.ui.components.IhcHeaderBar
import com.example.ui.components.IhcPackageBadge
import java.time.LocalDate

@Composable
fun ClientsScreen(
    clients: List<ClientEntity>,
    sessions: List<SessionEntity>,
    packageRules: List<PackageRuleEntity>,
    onClientClick: (String) -> Unit,
    onSaveClient: (
        existingId: String?,
        fullName: String,
        email: String,
        phone: String,
        packageTier: PackageTier,
        status: ClientStatus,
        goal: String,
        startDateMillis: Long,
        endDateMillis: Long,
        totalSessionsQuota: Int,
        notes: String
    ) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTierFilter by remember { mutableStateOf<PackageTier?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredClients = remember(clients, searchQuery, selectedTierFilter) {
        clients.filter { c ->
            val matchesTier = selectedTierFilter == null || c.packageTier == selectedTierFilter!!.name
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() || c.fullName.lowercase().contains(q) || c.emailNormalized.contains(q)
            matchesTier && matchesQuery
        }.sortedWith(
            compareBy<ClientEntity> { PackageTier.fromString(it.packageTier).sortPriority }
                .thenBy { it.fullName.lowercase() }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_client_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Klien")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            IhcHeaderBar(
                title = "Daftar Klien",
                subtitle = "${clients.size} klien aktif • Otomatisasi paket"
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Cari nama atau email klien…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("clients_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTierFilter == null,
                    onClick = { selectedTierFilter = null },
                    label = { Text("Semua Paket") }
                )
                PackageTier.entries.forEach { tier ->
                    FilterChip(
                        selected = selectedTierFilter == tier,
                        onClick = { selectedTierFilter = tier },
                        label = { Text(tier.label) },
                        modifier = Modifier.testTag("clients_filter_${tier.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredClients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    IhcEmptyState(
                        icon = Icons.Outlined.GroupAdd,
                        title = "Belum ada klien",
                        description = "Tekan tombol + untuk menambah klien baru. Email klien otomatis terdaftar untuk portal web."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredClients, key = { it.id }) { client ->
                        val tier = PackageTier.fromString(client.packageTier)
                        val usedSessions = sessions.count {
                            it.clientId == client.id && SessionStatus.fromString(it.status) == SessionStatus.HADIR
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClientClick(client.id) }
                                .testTag("client_card_${client.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = client.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IhcPackageBadge(tier = tier)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = client.emailNormalized,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Sesi hadir: $usedSessions / ${client.totalSessionsQuota}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = "s.d. ${formatWibDate(client.endDateMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        ClientFormDialog(
            initialClient = null,
            packageRules = packageRules,
            onDismiss = { showAddDialog = false },
            onConfirm = { fullName, email, phone, tier, status, goal, startMillis, endMillis, quota, notes ->
                onSaveClient(null, fullName, email, phone, tier, status, goal, startMillis, endMillis, quota, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ClientFormDialog(
    initialClient: ClientEntity?,
    packageRules: List<PackageRuleEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        fullName: String,
        email: String,
        phone: String,
        packageTier: PackageTier,
        status: ClientStatus,
        goal: String,
        startDateMillis: Long,
        endDateMillis: Long,
        totalSessionsQuota: Int,
        notes: String
    ) -> Unit
) {
    var fullName by remember { mutableStateOf(initialClient?.fullName ?: "") }
    var email by remember { mutableStateOf(initialClient?.emailNormalized ?: "") }
    var phone by remember { mutableStateOf(initialClient?.phone ?: "") }
    var selectedTier by remember {
        mutableStateOf(initialClient?.let { PackageTier.fromString(it.packageTier) } ?: PackageTier.STANDARD)
    }
    var selectedStatus by remember {
        mutableStateOf(initialClient?.let { ClientStatus.fromString(it.status) } ?: ClientStatus.AKTIF)
    }
    var goal by remember { mutableStateOf(initialClient?.goal ?: "") }
    var durationWeeksText by remember { mutableStateOf("4") }
    var quotaText by remember {
        mutableStateOf(initialClient?.totalSessionsQuota?.toString() ?: "12")
    }
    var notes by remember { mutableStateOf(initialClient?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val selectedRule = packageRules.firstOrNull { it.tier == selectedTier.name }
        ?: PackageRuleEntity.defaults().first { it.tier == selectedTier.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialClient == null) "Tambah Klien Baru" else "Edit Data Klien")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; validationError = null },
                    label = { Text("Nama Lengkap*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("client_form_name")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; validationError = null },
                    label = { Text("Email Klien (untuk Portal Web)*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("client_form_email")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("No. HP / WhatsApp") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Pilih Paket Coaching", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PackageTier.entries.forEach { tier ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick = {
                                selectedTier = tier
                                if (initialClient == null) {
                                    quotaText = when (tier) {
                                        PackageTier.BASIC -> "4"
                                        PackageTier.STANDARD -> "12"
                                        PackageTier.PREMIUM -> "24"
                                    }
                                }
                            },
                            label = { Text(tier.label) },
                            modifier = Modifier.testTag("client_form_tier_${tier.name.lowercase()}")
                        )
                    }
                }

                // Automatic facility summary preview
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Fasilitas Otomatis (${selectedTier.label}):",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "• Maks pertemuan: ${selectedRule.maxSessionsPerWeek}x/minggu\n" +
                                "• Reschedule: ${if (selectedRule.canReschedule) "Aktif" else "Terkunci"}\n" +
                                "• Foto: ${selectedRule.allowedAngles().joinToString { it.label }} (${selectedRule.photoFrequencyDays}h)\n" +
                                "• Lingkar: ${selectedRule.measurementFieldsCsv} (${selectedRule.measurementFrequencyDays}h)\n" +
                                "• Komposisi Tubuh: ${if (selectedRule.hasBodyComposition) "Ya (${selectedRule.bodyCompositionFrequencyDays}h)" else "Tidak"}\n" +
                                "• Meal Plan: ${if (selectedRule.hasMealPlan) "Update/${selectedRule.mealPlanFrequencyDays}h" else "Tidak"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (initialClient != null) {
                    Text("Status Klien", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClientStatus.entries.forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quotaText,
                        onValueChange = { quotaText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Kuota Sesi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    if (initialClient == null) {
                        OutlinedTextField(
                            value = durationWeeksText,
                            onValueChange = { durationWeeksText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Durasi (minggu)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Tujuan Latihan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Coach") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = fullName.trim()
                    val normalizedEmail = email.trim().lowercase()
                    if (trimmedName.isBlank()) {
                        validationError = "Nama klien wajib diisi."
                        return@Button
                    }
                    if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
                        validationError = "Email klien harus valid untuk login portal web."
                        return@Button
                    }
                    val startMillis = initialClient?.startDateMillis
                        ?: LocalDate.now(WIB_ZONE).atStartOfDay(WIB_ZONE).toInstant().toEpochMilli()
                    val weeks = (durationWeeksText.toLongOrNull() ?: 4L).coerceIn(1L, 104L)
                    val endMillis = initialClient?.endDateMillis
                        ?: LocalDate.now(WIB_ZONE).plusWeeks(weeks).atStartOfDay(WIB_ZONE).toInstant().toEpochMilli()
                    val quota = (quotaText.toIntOrNull() ?: 4).coerceAtLeast(1)

                    onConfirm(
                        trimmedName,
                        normalizedEmail,
                        phone,
                        selectedTier,
                        selectedStatus,
                        goal,
                        startMillis,
                        endMillis,
                        quota,
                        notes
                    )
                },
                modifier = Modifier.testTag("client_form_save")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
