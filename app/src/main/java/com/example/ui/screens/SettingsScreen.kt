package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CoachSessionState
import com.example.data.model.PackageRuleEntity
import com.example.data.model.PackageTier
import com.example.data.model.formatWibDateTime
import com.example.ui.components.IhcHeaderBar
import com.example.ui.components.IhcPackageBadge
import com.example.ui.theme.IhcDangerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    sessionState: CoachSessionState,
    packageRules: List<PackageRuleEntity>,
    onSavePackageRule: (PackageRuleEntity) -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSyncDriveNow: () -> Unit,
    onExportJsonString: suspend () -> String,
    onRestoreJsonString: (String) -> Unit,
    onNotify: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingRule by remember { mutableStateOf<PackageRuleEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = onExportJsonString()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    onNotify("Backup JSON berhasil diekspor.")
                } catch (e: Exception) {
                    onNotify("Gagal mengekspor JSON: ${e.localizedMessage}")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    if (!content.isNullOrBlank()) {
                        onRestoreJsonString(content)
                    } else {
                        onNotify("File JSON kosong.")
                    }
                } catch (e: Exception) {
                    onNotify("Gagal membaca file backup: ${e.localizedMessage}")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IhcHeaderBar(
            title = "Pengaturan",
            subtitle = "${sessionState.coachName} (${sessionState.coachEmail})"
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cloud Firestore & Google Drive Backup Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cloud Firestore & Google Drive", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Akun Google aktif: ${sessionState.coachEmail} • Penyimpanan utama: Cloud Firestore (cache offline aktif).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Folder Google Drive tersembunyi (appDataFolder) memakai akun Google yang sama tanpa token manual. Terakhir sinkron: " +
                                (sessionState.lastSyncMillis?.let { formatWibDateTime(it) } ?: "Belum pernah"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = sessionState.lastSyncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sinkron Drive otomatis saat login & ubah data", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = sessionState.autoSyncDrive,
                                onCheckedChange = onSetAutoSync,
                                modifier = Modifier.testTag("settings_autosync_switch")
                            )
                        }

                        Button(
                            onClick = onSyncDriveNow,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_sync_now_btn")
                        ) {
                            Icon(Icons.Outlined.CloudSync, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sinkronkan Drive & Foto Sekarang")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("ihc_backup_${System.currentTimeMillis()}.json") },
                                modifier = Modifier.weight(1f).testTag("settings_export_json_btn")
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Ekspor JSON")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.weight(1f).testTag("settings_import_json_btn")
                            ) {
                                Icon(Icons.Outlined.Upload, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Pulihkan JSON")
                            }
                        }
                    }
                }
            }

            // Package Rules Editor Section
            item {
                Text("Fasilitas per Paket (Dapat Diubah)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Perubahan otomatis menyesuaikan jadwal tugas dan form klien di paket tersebut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(packageRules.sortedBy { PackageTier.fromString(it.tier).sortPriority }, key = { it.tier }) { rule ->
                val tier = PackageTier.fromString(rule.tier)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IhcPackageBadge(tier = tier)
                            TextButton(
                                onClick = { editingRule = rule },
                                modifier = Modifier.testTag("edit_rule_${rule.tier.lowercase()}")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Ubah")
                            }
                        }
                        Text("• Maks Pertemuan: ${rule.maxSessionsPerWeek}x / minggu", style = MaterialTheme.typography.bodySmall)
                        Text("• Reschedule: ${if (rule.canReschedule) "Aktif" else "Terkunci"}", style = MaterialTheme.typography.bodySmall)
                        Text("• Foto Progres: ${rule.allowedAngles().joinToString { it.label }} (${rule.photoFrequencyDays}h)", style = MaterialTheme.typography.bodySmall)
                        Text("• Lingkar: ${rule.measurementFieldsCsv} (${rule.measurementFrequencyDays}h)", style = MaterialTheme.typography.bodySmall)
                        Text("• Komposisi Tubuh: ${if (rule.hasBodyComposition) "Tiap ${rule.bodyCompositionFrequencyDays}h" else "Tidak"}", style = MaterialTheme.typography.bodySmall)
                        Text("• Meal Plan: ${if (rule.hasMealPlan) "Tiap ${rule.mealPlanFrequencyDays}h" else "Tidak"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = IhcDangerRed),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_logout_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Keluar Akun Coach")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    editingRule?.let { rule ->
        var maxMeetings by remember { mutableStateOf(rule.maxSessionsPerWeek.toString()) }
        var canReschedule by remember { mutableStateOf(rule.canReschedule) }
        var photoFreq by remember { mutableStateOf(rule.photoFrequencyDays.toString()) }
        var measureFreq by remember { mutableStateOf(rule.measurementFrequencyDays.toString()) }
        var hasBodyComp by remember { mutableStateOf(rule.hasBodyComposition) }
        var bodyCompFreq by remember { mutableStateOf(rule.bodyCompositionFrequencyDays.toString()) }
        var hasMealPlan by remember { mutableStateOf(rule.hasMealPlan) }
        var mealFreq by remember { mutableStateOf(rule.mealPlanFrequencyDays.toString()) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text("Ubah Fasilitas Paket ${rule.tier}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxMeetings,
                        onValueChange = { maxMeetings = it.filter { c -> c.isDigit() } },
                        label = { Text("Maks pertemuan / minggu") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Izinkan Reschedule")
                        Switch(checked = canReschedule, onCheckedChange = { canReschedule = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = photoFreq,
                            onValueChange = { photoFreq = it.filter { c -> c.isDigit() } },
                            label = { Text("Foto (hari)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = measureFreq,
                            onValueChange = { measureFreq = it.filter { c -> c.isDigit() } },
                            label = { Text("Lingkar (hari)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Komposisi Tubuh")
                        Switch(checked = hasBodyComp, onCheckedChange = { hasBodyComp = it })
                    }
                    if (hasBodyComp) {
                        OutlinedTextField(
                            value = bodyCompFreq,
                            onValueChange = { bodyCompFreq = it.filter { c -> c.isDigit() } },
                            label = { Text("Frekuensi Komposisi (hari)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Meal Plan")
                        Switch(checked = hasMealPlan, onCheckedChange = { hasMealPlan = it })
                    }
                    if (hasMealPlan) {
                        OutlinedTextField(
                            value = mealFreq,
                            onValueChange = { mealFreq = it.filter { c -> c.isDigit() } },
                            label = { Text("Frekuensi Meal Plan (hari)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSavePackageRule(
                        rule.copy(
                            maxSessionsPerWeek = (maxMeetings.toIntOrNull() ?: 1).coerceIn(1, 14),
                            canReschedule = canReschedule,
                            photoFrequencyDays = (photoFreq.toIntOrNull() ?: 7).coerceAtLeast(1),
                            measurementFrequencyDays = (measureFreq.toIntOrNull() ?: 7).coerceAtLeast(1),
                            hasBodyComposition = hasBodyComp,
                            bodyCompositionFrequencyDays = if (hasBodyComp) (bodyCompFreq.toIntOrNull() ?: 30).coerceAtLeast(1) else 0,
                            hasMealPlan = hasMealPlan,
                            mealPlanFrequencyDays = if (hasMealPlan) (mealFreq.toIntOrNull() ?: 14).coerceAtLeast(1) else 0
                        )
                    )
                    editingRule = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editingRule = null }) { Text("Batal") } }
        )
    }
}
