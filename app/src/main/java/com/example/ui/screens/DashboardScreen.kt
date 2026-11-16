package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ChecklistFilter
import com.example.data.model.ClientEntity
import com.example.data.model.ClientStatus
import com.example.data.model.CoachTaskUiItem
import com.example.data.model.RequestStatus
import com.example.data.model.RescheduleRequestEntity
import com.example.data.model.SessionEntity
import com.example.data.model.SessionStatus
import com.example.data.model.WorkoutRequestEntity
import com.example.data.model.formatWibDate
import com.example.data.model.formatWibTime
import com.example.data.model.todayEndWib
import com.example.data.model.todayStartWib
import com.example.ui.components.IhcEmptyState
import com.example.ui.components.IhcHeaderBar
import com.example.ui.components.IhcPackageBadge
import com.example.ui.theme.IhcAmber
import com.example.ui.theme.IhcAmberContainer
import com.example.ui.theme.IhcEmerald
import com.example.ui.theme.IhcEmeraldContainer
import com.example.ui.theme.IhcOnEmeraldContainer
import com.example.ui.theme.IhcPureWhite

@Composable
fun DashboardScreen(
    coachName: String,
    clients: List<ClientEntity>,
    sessions: List<SessionEntity>,
    rescheduleRequests: List<RescheduleRequestEntity>,
    workoutRequests: List<WorkoutRequestEntity>,
    checklistTasks: List<CoachTaskUiItem>,
    checklistFilter: ChecklistFilter,
    onSelectChecklistFilter: (ChecklistFilter) -> Unit,
    onToggleTaskCompleted: (CoachTaskUiItem) -> Unit,
    onOpenClientTab: (clientId: String, tabIndex: Int) -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToRequests: () -> Unit
) {
    val activeClients = remember(clients) {
        clients.filter { ClientStatus.fromString(it.status) == ClientStatus.AKTIF }
    }
    val startToday = remember { todayStartWib() }
    val endToday = remember { todayEndWib() }

    val todaySessions = remember(sessions, startToday, endToday) {
        sessions.filter {
            it.startAtMillis in startToday..endToday &&
                SessionStatus.fromString(it.status) != SessionStatus.DIBATALKAN
        }
    }
    val pendingReschedules = remember(rescheduleRequests) {
        rescheduleRequests.filter { RequestStatus.fromString(it.status) == RequestStatus.MENUNGGU }
    }
    val pendingWorkouts = remember(workoutRequests) {
        workoutRequests.filter { RequestStatus.fromString(it.status) == RequestStatus.MENUNGGU }
    }
    val expiringClients = remember(activeClients) {
        val sevenDaysLater = System.currentTimeMillis() + 7 * 86_400_000L
        activeClients.filter { it.endDateMillis <= sevenDaysLater }
    }

    // Plain clean background per Section E.2
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IhcPureWhite)
    ) {
        IhcHeaderBar(
            title = "Halo, $coachName",
            subtitle = "IHC Management • Zona Waktu WIB"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = "Klien Aktif",
                        value = activeClients.size.toString(),
                        icon = Icons.Outlined.Groups,
                        onClick = onNavigateToClients,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dash_stat_clients")
                    )
                    DashboardStatCard(
                        title = "Sesi Hari Ini",
                        value = todaySessions.size.toString(),
                        icon = Icons.Outlined.EventAvailable,
                        onClick = onNavigateToCalendar,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dash_stat_sessions")
                    )
                    DashboardStatCard(
                        title = "Permintaan",
                        value = (pendingReschedules.size + pendingWorkouts.size).toString(),
                        icon = Icons.Outlined.SwapHoriz,
                        onClick = onNavigateToRequests,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dash_stat_requests")
                    )
                }
            }

            // Reminder & Pending Inbox banner
            if (pendingReschedules.isNotEmpty() || pendingWorkouts.isNotEmpty() || expiringClients.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = IhcAmberContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = "Pengingat Coach",
                                    tint = IhcAmber
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pengingat & Permintaan Klien",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = IhcAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (pendingReschedules.isNotEmpty()) {
                                Text(
                                    text = "• ${pendingReschedules.size} permintaan reschedule menunggu persetujuan.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (pendingWorkouts.isNotEmpty()) {
                                Text(
                                    text = "• ${pendingWorkouts.size} request gerakan workout menunggu balasan coach.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (expiringClients.isNotEmpty()) {
                                Text(
                                    text = "• ${expiringClients.size} klien aktif masa paketnya berakhir <= 7 hari.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (pendingReschedules.isNotEmpty() || pendingWorkouts.isNotEmpty()) {
                                TextButton(
                                    onClick = onNavigateToRequests,
                                    modifier = Modifier.testTag("dash_open_requests_btn")
                                ) {
                                    Text("Tinjau Permintaan")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Today's sessions preview
            if (todaySessions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Jadwal Hari Ini (WIB)", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = onNavigateToCalendar) { Text("Kalender") }
                            }
                            todaySessions.take(3).forEach { s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${formatWibTime(s.startAtMillis)} - ${formatWibTime(s.endAtMillis)} • ${s.clientName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = s.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Coach Checklist Section (Section D.2)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Checklist Tugas Coach",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Urutan prioritas: Premium → Standard → Basic. Centang untuk menjadwalkan otomatis siklus berikutnya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChecklistFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = checklistFilter == filter,
                                onClick = { onSelectChecklistFilter(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("checklist_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (checklistTasks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        IhcEmptyState(
                            icon = if (clients.isEmpty()) Icons.Outlined.FitnessCenter else Icons.Outlined.CheckCircle,
                            title = if (clients.isEmpty()) "Belum ada klien aktif" else "Tidak ada tugas di filter ini",
                            description = if (clients.isEmpty()) {
                                "Tambahkan klien di menu Klien agar tugas foto, lingkar, meal plan, dan workout terotomasi."
                            } else {
                                "Semua tugas yang dipilih sudah beres atau belum jatuh tempo."
                            }
                        )
                    }
                }
            } else {
                items(checklistTasks, key = { it.schedule.id }) { item ->
                    CoachChecklistTaskRow(
                        item = item,
                        onToggleComplete = { onToggleTaskCompleted(item) },
                        onOpenTargetTab = {
                            onOpenClientTab(item.client.id, item.taskType.targetTabIndex)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = IhcEmerald,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoachChecklistTaskRow(
    item: CoachTaskUiItem,
    onToggleComplete: () -> Unit,
    onOpenTargetTab: () -> Unit
) {
    val dueText = item.schedule.nextDueMillis?.let { "Target: ${formatWibDate(it)}" } ?: "Fleksibel (manual)"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checklist_item_${item.schedule.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompletedToday) IhcEmeraldContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCompletedToday,
                onCheckedChange = { if (!item.isCompletedToday) onToggleComplete() },
                modifier = Modifier.testTag("checklist_checkbox_${item.schedule.id}")
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.taskType.label,
                        style = MaterialTheme.typography.titleSmall
                    )
                    IhcPackageBadge(tier = item.packageTier)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.client.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (item.isCompletedToday) "Selesai hari ini • $dueText" else dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isCompletedToday) IhcOnEmeraldContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onOpenTargetTab,
                modifier = Modifier.testTag("checklist_open_client_${item.schedule.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Buka tab input klien"
                )
            }
        }
    }
}
