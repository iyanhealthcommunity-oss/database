package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLogEntity
import com.example.data.model.RequestStatus
import com.example.data.model.RescheduleRequestEntity
import com.example.data.model.WorkoutRequestEntity
import com.example.data.model.formatWibDateTime
import com.example.ui.components.IhcEmptyState
import com.example.ui.components.IhcHeaderBar
import com.example.ui.components.IhcRequestStatusBadge
import com.example.ui.theme.IhcDangerRed
import com.example.ui.theme.IhcEmerald

@Composable
fun RescheduleScreen(
    rescheduleRequests: List<RescheduleRequestEntity>,
    workoutRequests: List<WorkoutRequestEntity>,
    activityLogs: List<ActivityLogEntity>,
    onHandleReschedule: (RescheduleRequestEntity, Boolean, String) -> Unit,
    onReplyWorkoutRequest: (WorkoutRequestEntity, RequestStatus, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeRescheduleDialog by remember { mutableStateOf<Pair<RescheduleRequestEntity, Boolean>?>(null) }
    var activeWorkoutDialog by remember { mutableStateOf<WorkoutRequestEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        IhcHeaderBar(
            title = "Permintaan & Log",
            subtitle = "Reschedule klien, request gerakan, dan audit aktivitas"
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Reschedule (${rescheduleRequests.count { RequestStatus.fromString(it.status) == RequestStatus.MENUNGGU }})") },
                modifier = Modifier.testTag("requests_tab_reschedule")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Workout (${workoutRequests.count { RequestStatus.fromString(it.status) == RequestStatus.MENUNGGU }})") },
                modifier = Modifier.testTag("requests_tab_workout")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Activity Log") },
                modifier = Modifier.testTag("requests_tab_log")
            )
        }

        when (selectedTab) {
            0 -> {
                if (rescheduleRequests.isEmpty()) {
                    IhcEmptyState(
                        icon = Icons.Outlined.SwapHoriz,
                        title = "Belum ada permintaan reschedule",
                        description = "Permintaan reschedule dari klien Standard & Premium di portal web akan tampil di sini."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rescheduleRequests, key = { it.id }) { req ->
                            val st = RequestStatus.fromString(req.status)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(req.clientName, style = MaterialTheme.typography.titleSmall)
                                        IhcRequestStatusBadge(st)
                                    }
                                    Text("Jadwal Awal: ${formatWibDateTime(req.originalStartMillis)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Usulan Baru: ${formatWibDateTime(req.requestedStartMillis)}", style = MaterialTheme.typography.bodySmall, color = IhcEmerald)
                                    if (req.reason.isNotBlank()) Text("Alasan: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                                    if (req.coachNote.isNotBlank()) Text("Catatan Coach: ${req.coachNote}", style = MaterialTheme.typography.bodySmall)

                                    if (st == RequestStatus.MENUNGGU) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { activeRescheduleDialog = req to true },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Setujui") }
                                            OutlinedButton(
                                                onClick = { activeRescheduleDialog = req to false },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IhcDangerRed),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Tolak") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (workoutRequests.isEmpty()) {
                    IhcEmptyState(
                        icon = Icons.Outlined.FitnessCenter,
                        title = "Belum ada request gerakan",
                        description = "Request gerakan workout dari klien portal web akan tampil di sini."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(workoutRequests, key = { it.id }) { req ->
                            val st = RequestStatus.fromString(req.status)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${req.clientName} • ${req.requestedExercise}", style = MaterialTheme.typography.titleSmall)
                                        IhcRequestStatusBadge(st)
                                    }
                                    if (req.reason.isNotBlank()) Text("Alasan: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                                    if (req.coachReply.isNotBlank()) Text("Balasan: ${req.coachReply}", style = MaterialTheme.typography.bodySmall, color = IhcEmerald)
                                    TextButton(onClick = { activeWorkoutDialog = req }) {
                                        Text("Balas / Ubah Status")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (activityLogs.isEmpty()) {
                    IhcEmptyState(
                        icon = Icons.Outlined.History,
                        title = "Belum ada catatan aktivitas",
                        description = "Perubahan jadwal, kehadiran, dan paket otomatis tercatat di sini."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activityLogs, key = { it.id }) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(log.action, style = MaterialTheme.typography.labelMedium)
                                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatWibDateTime(log.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeRescheduleDialog?.let { (req, approve) ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { activeRescheduleDialog = null },
            title = { Text(if (approve) "Setujui Reschedule?" else "Tolak Reschedule?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${req.clientName} → ${formatWibDateTime(req.requestedStartMillis)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Catatan coach (opsional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onHandleReschedule(req, approve, note)
                    activeRescheduleDialog = null
                }) { Text(if (approve) "Setujui" else "Tolak") }
            },
            dismissButton = { TextButton(onClick = { activeRescheduleDialog = null }) { Text("Batal") } }
        )
    }

    activeWorkoutDialog?.let { req ->
        var reply by remember { mutableStateOf(req.coachReply) }
        var status by remember { mutableStateOf(RequestStatus.DISETUJUI) }
        AlertDialog(
            onDismissRequest = { activeWorkoutDialog = null },
            title = { Text("Balas Request ${req.clientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(req.requestedExercise, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(RequestStatus.DISETUJUI, RequestStatus.DIBALAS, RequestStatus.DITOLAK).forEach { st ->
                            FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.label) })
                        }
                    }
                    OutlinedTextField(value = reply, onValueChange = { reply = it }, label = { Text("Balasan untuk klien") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onReplyWorkoutRequest(req, status, reply)
                    activeWorkoutDialog = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { activeWorkoutDialog = null }) { Text("Batal") } }
        )
    }
}
