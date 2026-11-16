package com.example.data.repository

import android.net.Uri
import com.example.data.local.IhcDao
import com.example.data.model.ActivityLogEntity
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
import com.example.data.model.RescheduleRequestEntity
import com.example.data.model.SessionEntity
import com.example.data.model.SessionStatus
import com.example.data.model.TaskType
import com.example.data.model.WIB_ZONE
import com.example.data.model.WorkoutExerciseEntity
import com.example.data.model.WorkoutRequestEntity
import com.example.data.model.WorkoutUpdateMode
import com.example.data.model.epochMillisToWibDate
import com.example.data.model.formatWibDateTime
import com.example.data.remote.IhcFirestoreDataSource
import com.example.data.sync.IhcBackupManager
import com.example.data.sync.SyncOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

sealed class SessionSaveResult {
    data class Success(val sessionId: String, val warning: String? = null) : SessionSaveResult()
    data class Conflict(val message: String) : SessionSaveResult()
}

class IhcRepository(
    private val dao: IhcDao,
    private val backupManager: IhcBackupManager,
    private val firestoreSource: IhcFirestoreDataSource? = null,
    private val awaitRemoteWrites: Boolean = false
) {
    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var onCloudWriteError: ((String) -> Unit)? = null

    val packageRules: Flow<List<PackageRuleEntity>> = dao.observePackageRules()
    val activeClients: Flow<List<ClientEntity>> = dao.observeActiveClients()
    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()
    val rescheduleRequests: Flow<List<RescheduleRequestEntity>> = dao.observeRescheduleRequests()
    val workoutRequests: Flow<List<WorkoutRequestEntity>> = dao.observeWorkoutRequests()
    val allTaskSchedules: Flow<List<ClientTaskScheduleEntity>> = dao.observeAllTaskSchedules()
    val activityLogs: Flow<List<ActivityLogEntity>> = dao.observeActivityLogs()

    fun startCloudSync(coachUid: String, scope: CoroutineScope, onSyncError: (String) -> Unit) {
        val source = firestoreSource ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { source.pushLocalCacheSnapshot(dao) }
        }
        source.startSyncToLocalCache(coachUid, dao, scope, onSyncError)
    }

    fun stopCloudSync() {
        firestoreSource?.stopSync()
    }

    private suspend fun syncCloudWrite(block: suspend IhcFirestoreDataSource.() -> Unit) {
        val source = firestoreSource ?: return
        if (source.currentCoachUid() == null) {
            if (awaitRemoteWrites) {
                source.block()
            }
            return
        }
        if (awaitRemoteWrites) {
            withTimeout(5000L) {
                source.block()
            }
            return
        }
        if (backupManager.isOnline()) {
            try {
                withTimeoutOrNull(5000L) {
                    source.block()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onCloudWriteError?.invoke(e.localizedMessage ?: "Sinkronisasi Cloud Firestore tertunda")
            }
        } else {
            // Offline: enqueue into Firestore persistent disk cache without blocking UI coroutine
            cloudScope.launch {
                runCatching { source.block() }
            }
        }
    }

    private suspend fun resolveClientEmail(clientId: String): String =
        dao.getClientById(clientId)?.emailNormalized?.takeIf { it.contains("@") } ?: "klien@ihc.id"

    fun observeMeasurements(clientId: String): Flow<List<MeasurementEntity>> =
        dao.observeMeasurementsForClient(clientId)

    fun observeBodyCompositions(clientId: String): Flow<List<BodyCompositionEntity>> =
        dao.observeBodyCompositionsForClient(clientId)

    fun observePhotos(clientId: String): Flow<List<ProgressPhotoEntity>> =
        dao.observePhotosForClient(clientId)

    fun observeWorkouts(clientId: String): Flow<List<WorkoutExerciseEntity>> =
        dao.observeWorkoutsForClient(clientId)

    fun observeMealPlanNotes(clientId: String): Flow<List<MealPlanNoteEntity>> =
        dao.observeMealPlanNotesForClient(clientId)

    fun observeTaskSchedulesForClient(clientId: String): Flow<List<ClientTaskScheduleEntity>> =
        dao.observeTaskSchedulesForClient(clientId)

    suspend fun ensureDefaultPackageRules() {
        val current = dao.getAllPackageRules()
        if (current.isEmpty()) {
            val defaults = PackageRuleEntity.defaults()
            dao.upsertPackageRules(defaults)
            if (firestoreSource?.currentCoachUid() != null) {
                defaults.forEach { rule ->
                    syncCloudWrite { upsertPackageRule(rule) }
                }
            }
        }
    }

    suspend fun savePackageRule(rule: PackageRuleEntity) {
        val updated = rule.copy(updatedAt = System.currentTimeMillis())
        dao.upsertPackageRule(updated)
        syncCloudWrite { upsertPackageRule(updated) }
        // Re-sync task schedules for all active clients on this package tier
        val clients = dao.getAllClientsIncludingDeleted()
            .filter { it.deletedAt == null && it.packageTier == rule.tier }
        clients.forEach { client ->
            syncClientTaskSchedules(client.id, updated)
        }
        logActivity("UPDATE_PAKET_RULE", "PackageRule", rule.tier, "Aturan paket ${rule.tier} diperbarui")
    }

    suspend fun saveClient(
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
    ): String {
        ensureDefaultPackageRules()
        val now = System.currentTimeMillis()
        val id = existingId ?: UUID.randomUUID().toString()
        val existing = if (existingId != null) dao.getClientById(existingId) else null
        val client = ClientEntity(
            id = id,
            fullName = fullName.trim(),
            emailNormalized = email.trim().lowercase(),
            phone = phone.trim(),
            packageTier = packageTier.name,
            status = status.name,
            goal = goal.trim(),
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis,
            totalSessionsQuota = totalSessionsQuota.coerceAtLeast(1),
            notes = notes.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null
        )
        dao.upsertClient(client)
        syncCloudWrite { upsertClient(client, isCreate = (existing == null)) }

        val rule = dao.getPackageRule(packageTier.name)
            ?: PackageRuleEntity.defaults().first { it.tier == packageTier.name }
        syncClientTaskSchedules(id, rule)

        val action = if (existing == null) "TAMBAH_KLIEN" else "UBAH_KLIEN"
        logActivity(action, "Client", id, "${client.fullName} (${packageTier.label}) disimpan")
        return id
    }

    suspend fun softDeleteClient(client: ClientEntity) {
        val now = System.currentTimeMillis()
        val archived = client.copy(deletedAt = now, updatedAt = now)
        dao.upsertClient(archived)
        syncCloudWrite { upsertClient(archived, isCreate = false) }
        logActivity("ARSIP_KLIEN", "Client", client.id, "Klien ${client.fullName} diarsipkan (soft delete)")
    }

    /**
     * Automatically provisions or updates the client's 5 input schedule tasks according to their package rule.
     */
    suspend fun syncClientTaskSchedules(clientId: String, rule: PackageRuleEntity) {
        val now = System.currentTimeMillis()
        val existingMap = dao.getTaskSchedulesForClient(clientId).associateBy { it.taskType }

        fun buildSchedule(
            taskType: TaskType,
            enabled: Boolean,
            freqDays: Int,
            preserveManualDue: Boolean = false
        ): ClientTaskScheduleEntity {
            val prev = existingMap[taskType.name]
            val id = prev?.id ?: "${clientId}_${taskType.name}"
            val computedNextDue = when {
                !enabled -> null
                preserveManualDue -> prev?.nextDueMillis
                prev?.nextDueMillis != null -> prev.nextDueMillis
                else -> now
            }
            return ClientTaskScheduleEntity(
                id = id,
                clientId = clientId,
                taskType = taskType.name,
                enabled = enabled,
                nextDueMillis = computedNextDue,
                lastCompletedMillis = prev?.lastCompletedMillis,
                frequencyDays = freqDays,
                notes = prev?.notes ?: "",
                updatedAt = now
            )
        }

        val workoutEnabled = rule.workoutUpdateMode != WorkoutUpdateMode.NONE.name
        val workoutManual = rule.workoutUpdateMode == WorkoutUpdateMode.FLEXIBLE.name

        val updatedList = listOf(
            buildSchedule(TaskType.FOTO_PROGRES, enabled = true, freqDays = rule.photoFrequencyDays.coerceAtLeast(1)),
            buildSchedule(TaskType.PENGUKURAN_LINGKAR, enabled = true, freqDays = rule.measurementFrequencyDays.coerceAtLeast(1)),
            buildSchedule(TaskType.KOMPOSISI_TUBUH, enabled = rule.hasBodyComposition, freqDays = rule.bodyCompositionFrequencyDays.coerceAtLeast(1)),
            buildSchedule(TaskType.MEAL_PLAN, enabled = rule.hasMealPlan, freqDays = rule.mealPlanFrequencyDays.coerceAtLeast(1)),
            buildSchedule(TaskType.WORKOUT, enabled = workoutEnabled, freqDays = rule.workoutFrequencyDays.coerceAtLeast(0), preserveManualDue = workoutManual)
        )
        dao.upsertTaskSchedules(updatedList)
        updatedList.forEach { sched ->
            syncCloudWrite { upsertTaskSchedule(sched) }
        }
    }

    suspend fun updateClientTaskSchedule(
        schedule: ClientTaskScheduleEntity,
        nextDueMillis: Long?,
        notes: String
    ) {
        val now = System.currentTimeMillis()
        val updated = schedule.copy(
            nextDueMillis = nextDueMillis,
            notes = notes.trim(),
            updatedAt = now
        )
        dao.upsertTaskSchedule(updated)
        syncCloudWrite { upsertTaskSchedule(updated) }
        logActivity("ATUR_JADWAL_INPUT", "ClientTaskSchedule", schedule.id, "Jadwal ${TaskType.fromString(schedule.taskType).label} diperbarui")
    }

    suspend fun markCoachTaskCompleted(schedule: ClientTaskScheduleEntity, clientName: String) {
        val now = System.currentTimeMillis()
        val taskType = TaskType.fromString(schedule.taskType)
        val nextDue = if (schedule.frequencyDays > 0) {
            now + schedule.frequencyDays * 86_400_000L
        } else {
            // Flexible mode (Premium workout): clear next due until coach sets a new target date
            null
        }
        val updated = schedule.copy(
            lastCompletedMillis = now,
            nextDueMillis = nextDue,
            updatedAt = now
        )
        dao.upsertTaskSchedule(updated)
        syncCloudWrite { upsertTaskSchedule(updated) }
        logActivity("SELESAI_TUGAS_COACH", "ClientTaskSchedule", schedule.id, "${taskType.label} untuk $clientName selesai dicentang")
    }

    suspend fun addMeasurement(
        clientId: String,
        waistCm: Double,
        neckCm: Double?,
        armCm: Double?,
        weightKg: Double?,
        notes: String
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = MeasurementEntity(
            id = id,
            clientId = clientId,
            recordedAt = now,
            waistCm = waistCm,
            neckCm = neckCm,
            armCm = armCm,
            weightKg = weightKg,
            notes = notes.trim(),
            updatedAt = now
        )
        dao.upsertMeasurement(entity)
        val clientEmail = resolveClientEmail(clientId)
        syncCloudWrite { upsertMeasurement(entity, clientEmail) }
        advanceTaskScheduleAfterInput(clientId, TaskType.PENGUKURAN_LINGKAR, now)
        logActivity("INPUT_PENGUKURAN", "Measurement", id, "Pengukuran lingkar dicatat (Perut ${waistCm}cm)")
    }

    suspend fun addBodyComposition(
        clientId: String,
        bodyFatPercent: Double,
        muscleMassKg: Double,
        bodyAgeYears: Int,
        notes: String
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = BodyCompositionEntity(
            id = id,
            clientId = clientId,
            recordedAt = now,
            bodyFatPercent = bodyFatPercent,
            muscleMassKg = muscleMassKg,
            bodyAgeYears = bodyAgeYears,
            notes = notes.trim(),
            updatedAt = now
        )
        dao.upsertBodyComposition(entity)
        val clientEmail = resolveClientEmail(clientId)
        syncCloudWrite { upsertBodyComposition(entity, clientEmail) }
        advanceTaskScheduleAfterInput(clientId, TaskType.KOMPOSISI_TUBUH, now)
        logActivity("INPUT_KOMPOSISI", "BodyComposition", id, "Komposisi tubuh dicatat (BF $bodyFatPercent%)")
    }

    suspend fun addProgressPhoto(
        clientId: String,
        angle: PhotoAngle,
        uri: Uri,
        notes: String
    ) {
        val now = System.currentTimeMillis()
        val (originalPath, previewBase64) = backupManager.saveProgressPhotoLocally(uri, clientId)
        val id = UUID.randomUUID().toString()
        val entity = ProgressPhotoEntity(
            id = id,
            clientId = clientId,
            angle = angle.name,
            takenAt = now,
            localOriginalPath = originalPath,
            previewBase64 = previewBase64,
            notes = notes.trim(),
            updatedAt = now
        )
        dao.upsertPhoto(entity)
        val clientEmail = resolveClientEmail(clientId)
        syncCloudWrite { upsertProgressPhoto(entity, clientEmail) }
        advanceTaskScheduleAfterInput(clientId, TaskType.FOTO_PROGRES, now)
        logActivity("UPLOAD_FOTO_PROGRES", "ProgressPhoto", id, "Foto progres sudut ${angle.label} disimpan")
    }

    suspend fun addWorkoutExercise(
        clientId: String,
        programDay: String,
        exerciseName: String,
        sets: Int,
        reps: String,
        loadNote: String,
        restSeconds: Int,
        coachNotes: String
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = WorkoutExerciseEntity(
            id = id,
            clientId = clientId,
            programDay = programDay.trim().ifBlank { "Hari 1" },
            exerciseName = exerciseName.trim(),
            sets = sets.coerceAtLeast(1),
            reps = reps.trim().ifBlank { "10-12" },
            loadNote = loadNote.trim(),
            restSeconds = restSeconds.coerceIn(10, 600),
            coachNotes = coachNotes.trim(),
            sortOrder = (now % 100000).toInt(),
            updatedAt = now
        )
        dao.upsertWorkoutExercise(entity)
        val clientEmail = resolveClientEmail(clientId)
        syncCloudWrite { upsertWorkoutExercise(entity, clientEmail) }
        advanceTaskScheduleAfterInput(clientId, TaskType.WORKOUT, now)
        logActivity("TAMBAH_WORKOUT", "WorkoutExercise", id, "Gerakan ${exerciseName.trim()} ditambahkan")
    }

    suspend fun softDeleteWorkoutExercise(item: WorkoutExerciseEntity) {
        val now = System.currentTimeMillis()
        val archived = item.copy(deletedAt = now, updatedAt = now)
        dao.upsertWorkoutExercise(archived)
        val clientEmail = resolveClientEmail(item.clientId)
        syncCloudWrite { upsertWorkoutExercise(archived, clientEmail) }
        logActivity("HAPUS_WORKOUT", "WorkoutExercise", item.id, "Gerakan ${item.exerciseName} diarsipkan")
    }

    suspend fun addMealPlanNote(clientId: String, title: String, note: String) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = MealPlanNoteEntity(
            id = id,
            clientId = clientId,
            title = title.trim().ifBlank { "Update Meal Plan" },
            note = note.trim(),
            updatedAt = now
        )
        dao.upsertMealPlanNote(entity)
        val clientEmail = resolveClientEmail(clientId)
        syncCloudWrite { upsertMealPlanNote(entity, clientEmail) }
        advanceTaskScheduleAfterInput(clientId, TaskType.MEAL_PLAN, now)
        logActivity("UPDATE_MEAL_PLAN", "MealPlanNote", id, "Catatan Meal Plan diperbarui")
    }

    suspend fun replyWorkoutRequest(
        request: WorkoutRequestEntity,
        status: RequestStatus,
        coachReply: String
    ) {
        val now = System.currentTimeMillis()
        val updated = request.copy(
            status = status.name,
            coachReply = coachReply.trim(),
            updatedAt = now
        )
        dao.upsertWorkoutRequest(updated)
        val clientEmail = resolveClientEmail(request.clientId)
        syncCloudWrite { updateWorkoutRequest(updated, clientEmail) }
        logActivity("BALAS_REQUEST_WORKOUT", "WorkoutRequest", request.id, "Request '${request.requestedExercise}' -> ${status.label}")
    }

    private suspend fun advanceTaskScheduleAfterInput(clientId: String, taskType: TaskType, completedAt: Long) {
        val schedule = dao.getTaskSchedulesForClient(clientId)
            .firstOrNull { it.taskType == taskType.name && it.enabled } ?: return
        val nextDue = if (schedule.frequencyDays > 0) {
            completedAt + schedule.frequencyDays * 86_400_000L
        } else {
            null
        }
        val updated = schedule.copy(
            lastCompletedMillis = completedAt,
            nextDueMillis = nextDue,
            updatedAt = completedAt
        )
        dao.upsertTaskSchedule(updated)
        syncCloudWrite { upsertTaskSchedule(updated) }
    }

    suspend fun createSessionChecked(
        client: ClientEntity,
        title: String,
        startAtMillis: Long,
        durationMinutes: Int,
        location: String,
        notes: String,
        recurringGroupId: String? = null
    ): SessionSaveResult {
        val endAtMillis = startAtMillis + durationMinutes.coerceAtLeast(15) * 60_000L
        val activeSessions = dao.getActiveNonCancelledSessions()
        val conflicts = activeSessions.filter { existing ->
            existing.startAtMillis < endAtMillis && existing.endAtMillis > startAtMillis
        }
        if (conflicts.isNotEmpty()) {
            val first = conflicts.first()
            return SessionSaveResult.Conflict(
                "Jadwal bentrok dengan ${first.clientName} (${formatWibDateTime(first.startAtMillis)})."
            )
        }

        val rule = dao.getPackageRule(client.packageTier)
            ?: PackageRuleEntity.defaults().first { it.tier == client.packageTier }
        val sessionDate = epochMillisToWibDate(startAtMillis)
        val weekStart = sessionDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val weeklyCount = activeSessions.count { s ->
            if (s.clientId != client.id) return@count false
            val d = epochMillisToWibDate(s.startAtMillis)
            !d.isBefore(weekStart) && !d.isAfter(weekEnd)
        }

        val warning = if (weeklyCount >= rule.maxSessionsPerWeek) {
            "Peringatan: ${client.fullName} sudah mencapai kapasitas ${rule.maxSessionsPerWeek}x/minggu untuk paket ${PackageTier.fromString(client.packageTier).label}."
        } else {
            null
        }

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val session = SessionEntity(
            id = id,
            clientId = client.id,
            clientName = client.fullName,
            title = title.trim().ifBlank { "Sesi Latihan" },
            startAtMillis = startAtMillis,
            endAtMillis = endAtMillis,
            location = location.trim().ifBlank { "IHC Studio" },
            status = SessionStatus.TERJADWAL.name,
            recurringGroupId = recurringGroupId,
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now
        )
        dao.upsertSession(session)
        syncCloudWrite { upsertSession(session, client.emailNormalized, isCreate = true) }
        logActivity("BUAT_SESI", "Session", id, "Sesi ${client.fullName} pada ${formatWibDateTime(startAtMillis)}")
        return SessionSaveResult.Success(id, warning)
    }

    suspend fun createRecurringSessions(
        client: ClientEntity,
        title: String,
        firstDateWib: LocalDate,
        timeWib: LocalTime,
        durationMinutes: Int,
        weeksCount: Int,
        location: String,
        notes: String
    ): Pair<Int, List<String>> {
        val groupId = UUID.randomUUID().toString()
        var created = 0
        val messages = mutableListOf<String>()
        for (w in 0 until weeksCount.coerceIn(1, 16)) {
            val targetDate = firstDateWib.plusWeeks(w.toLong())
            val startMillis = targetDate.atTime(timeWib).atZone(WIB_ZONE).toInstant().toEpochMilli()
            when (val res = createSessionChecked(client, title, startMillis, durationMinutes, location, notes, groupId)) {
                is SessionSaveResult.Success -> {
                    created++
                    if (res.warning != null) messages.add("Minggu ${w + 1}: ${res.warning}")
                }
                is SessionSaveResult.Conflict -> {
                    messages.add("Minggu ${w + 1} dilewati: ${res.message}")
                }
            }
        }
        return created to messages
    }

    suspend fun updateSessionStatus(session: SessionEntity, newStatus: SessionStatus) {
        val now = System.currentTimeMillis()
        val updated = session.copy(status = newStatus.name, updatedAt = now)
        dao.upsertSession(updated)
        val clientEmail = resolveClientEmail(session.clientId)
        syncCloudWrite { upsertSession(updated, clientEmail, isCreate = false) }
        logActivity("UBAH_KEHADIRAN", "Session", session.id, "Status sesi ${session.clientName} (${formatWibDateTime(session.startAtMillis)}) -> ${newStatus.label}")
    }

    suspend fun coachRescheduleSession(
        session: SessionEntity,
        newStartMillis: Long,
        newEndMillis: Long,
        reason: String
    ): SessionSaveResult {
        val client = dao.getClientById(session.clientId)
        val rule = client?.let { dao.getPackageRule(it.packageTier) }
        if (rule != null && !rule.canReschedule) {
            return SessionSaveResult.Conflict("Paket Basic tidak memiliki fasilitas reschedule.")
        }
        val activeSessions = dao.getActiveNonCancelledSessions().filter { it.id != session.id }
        val conflict = activeSessions.firstOrNull { it.startAtMillis < newEndMillis && it.endAtMillis > newStartMillis }
        if (conflict != null) {
            return SessionSaveResult.Conflict("Bentrok dengan sesi ${conflict.clientName} pada ${formatWibDateTime(conflict.startAtMillis)}.")
        }
        val now = System.currentTimeMillis()
        val updated = session.copy(
            startAtMillis = newStartMillis,
            endAtMillis = newEndMillis,
            notes = if (reason.isNotBlank()) "Reschedule: $reason" else session.notes,
            updatedAt = now
        )
        dao.upsertSession(updated)
        val clientEmail = client?.emailNormalized ?: resolveClientEmail(session.clientId)
        syncCloudWrite { upsertSession(updated, clientEmail, isCreate = false) }
        logActivity("RESCHEDULE_COACH", "Session", session.id, "Sesi ${session.clientName} dipindah ke ${formatWibDateTime(newStartMillis)}")
        return SessionSaveResult.Success(session.id)
    }

    suspend fun handleRescheduleRequest(
        request: RescheduleRequestEntity,
        approve: Boolean,
        coachNote: String
    ): String? {
        val client = dao.getClientById(request.clientId)
        val rule = client?.let { dao.getPackageRule(it.packageTier) }
        if (approve && rule != null && !rule.canReschedule) {
            return "Klien paket Basic tidak dapat melakukan reschedule."
        }
        val now = System.currentTimeMillis()
        val clientEmail = client?.emailNormalized ?: resolveClientEmail(request.clientId)
        if (approve) {
            val session = dao.getSessionById(request.sessionId)
            if (session != null) {
                val activeSessions = dao.getActiveNonCancelledSessions().filter { it.id != session.id }
                val conflict = activeSessions.firstOrNull {
                    it.startAtMillis < request.requestedEndMillis && it.endAtMillis > request.requestedStartMillis
                }
                if (conflict != null) {
                    return "Tidak dapat disetujui karena bentrok dengan ${conflict.clientName} (${formatWibDateTime(conflict.startAtMillis)})."
                }
                val updatedSession = session.copy(
                    startAtMillis = request.requestedStartMillis,
                    endAtMillis = request.requestedEndMillis,
                    updatedAt = now
                )
                dao.upsertSession(updatedSession)
                syncCloudWrite { upsertSession(updatedSession, clientEmail, isCreate = false) }
            }
        }
        val nextStatus = if (approve) RequestStatus.DISETUJUI else RequestStatus.DITOLAK
        val updatedReq = request.copy(
            status = nextStatus.name,
            coachNote = coachNote.trim(),
            updatedAt = now
        )
        dao.upsertRescheduleRequest(updatedReq)
        syncCloudWrite { updateRescheduleRequest(updatedReq, clientEmail) }
        logActivity(
            if (approve) "SETUJUI_RESCHEDULE" else "TOLAK_RESCHEDULE",
            "RescheduleRequest",
            request.id,
            "Reschedule ${request.clientName} -> ${nextStatus.label}"
        )
        return null
    }

    suspend fun exportBackupJson(): String = backupManager.exportAllToJson()

    suspend fun importBackupJson(json: String): Int {
        val count = backupManager.mergeFromJson(json)
        if (count > 0 && firestoreSource?.currentCoachUid() != null) {
            syncCloudWrite { pushLocalCacheSnapshot(dao) }
        }
        logActivity("RESTORE_BACKUP", "System", "json", "Pulihkan $count item dari cadangan JSON")
        return count
    }

    suspend fun syncGoogleDrive(accessToken: String): SyncOutcome {
        val outcome = backupManager.syncWithGoogleDriveAppData(accessToken)
        if (outcome is SyncOutcome.Success) {
            if (firestoreSource?.currentCoachUid() != null) {
                syncCloudWrite {
                    pushLocalCacheSnapshot(dao)
                    val clientsById = dao.getAllClientsIncludingDeleted().associateBy { it.id }
                    dao.getAllPhotosIncludingDeleted()
                        .filter { it.deletedAt == null && it.driveFileId.isNotBlank() }
                        .forEach { photo ->
                            val email = clientsById[photo.clientId]?.emailNormalized
                            if (!email.isNullOrBlank()) {
                                upsertProgressPhoto(photo, email)
                            }
                        }
                }
            }
            logActivity("SINKRON_DRIVE", "System", "appDataFolder", outcome.message)
        }
        return outcome
    }

    suspend fun logActivity(action: String, entityType: String, entityId: String, summary: String) {
        val entry = ActivityLogEntity(
            id = UUID.randomUUID().toString(),
            action = action,
            entityType = entityType,
            entityId = entityId,
            summary = summary,
            createdAt = Instant.now().toEpochMilli()
        )
        dao.insertActivityLog(entry)
        syncCloudWrite { insertActivityLog(entry) }
    }
}
