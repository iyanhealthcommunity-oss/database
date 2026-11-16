package com.example.data.remote

import com.example.data.local.IhcDao
import com.example.data.model.ActivityLogEntity
import com.example.data.model.BodyCompositionEntity
import com.example.data.model.ClientEntity
import com.example.data.model.ClientStatus
import com.example.data.model.ClientTaskScheduleEntity
import com.example.data.model.MealPlanNoteEntity
import com.example.data.model.MeasurementEntity
import com.example.data.model.PackageRuleEntity
import com.example.data.model.ProgressPhotoEntity
import com.example.data.model.RescheduleRequestEntity
import com.example.data.model.SessionEntity
import com.example.data.model.WorkoutExerciseEntity
import com.example.data.model.WorkoutRequestEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class IhcFirestoreDataSource(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val syncJobs = mutableListOf<Job>()

    fun currentCoachUid(): String? = auth.currentUser?.uid

    private fun requireCoachUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Coach must be signed in with Google before accessing Firestore.")

    private fun DocumentSnapshot.tsMillis(field: String, fallback: Long = System.currentTimeMillis()): Long =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: getLong(field)
            ?: fallback

    fun stopSync() {
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
    }

    /**
     * Attaches real-time Firestore snapshot listeners (Auth-gated, scoped to coachUid)
     * and mirrors all remote changes into the local Room offline cache.
     */
    fun startSyncToLocalCache(
        coachUid: String,
        dao: IhcDao,
        scope: CoroutineScope,
        onError: (String) -> Unit
    ) {
        stopSync()
        if (auth.currentUser?.uid != coachUid) return

        fun launchCollectionSync(
            collectionName: String,
            handler: suspend (List<DocumentSnapshot>) -> Unit
        ) {
            val job = scope.launch {
                db.collection(collectionName)
                    .whereEqualTo("coachUid", coachUid)
                    .snapshots()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val ex = firestoreError ?: (error as? Exception)
                        if (ex != null && auth.currentUser?.uid == coachUid) {
                            handleFirestoreError(ex, OperationType.LIST, collectionName, auth)
                            onError(firestoreError?.localizedMessage ?: ex.localizedMessage ?: "Gagal sinkronisasi $collectionName")
                        }
                    }
                    .collectLatest { snap ->
                        handler(snap.documents)
                    }
            }
            syncJobs.add(job)
        }

        launchCollectionSync("packageRules") { docs ->
            val list = docs.mapNotNull { doc ->
                val tier = doc.getString("tier") ?: doc.id
                PackageRuleEntity(
                    tier = tier,
                    maxSessionsPerWeek = (doc.getLong("maxSessionsPerWeek") ?: 1L).toInt(),
                    canReschedule = doc.getBoolean("canReschedule") ?: false,
                    photoAnglesCsv = doc.getString("photoAnglesCsv") ?: "DEPAN",
                    photoFrequencyDays = (doc.getLong("photoFrequencyDays") ?: 7L).toInt(),
                    measurementFieldsCsv = doc.getString("measurementFieldsCsv") ?: "PERUT",
                    measurementFrequencyDays = (doc.getLong("measurementFrequencyDays") ?: 30L).toInt(),
                    hasBodyComposition = doc.getBoolean("hasBodyComposition") ?: false,
                    bodyCompositionFrequencyDays = (doc.getLong("bodyCompositionFrequencyDays") ?: 0L).toInt(),
                    hasMealPlan = doc.getBoolean("hasMealPlan") ?: false,
                    mealPlanFrequencyDays = (doc.getLong("mealPlanFrequencyDays") ?: 0L).toInt(),
                    workoutUpdateMode = doc.getString("workoutUpdateMode") ?: "NONE",
                    workoutFrequencyDays = (doc.getLong("workoutFrequencyDays") ?: 0L).toInt(),
                    updatedAt = doc.tsMillis("updatedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertPackageRules(list)
        }

        launchCollectionSync("clients") { docs ->
            val list = docs.map { doc ->
                val updated = doc.tsMillis("updatedAt")
                ClientEntity(
                    id = doc.id,
                    fullName = doc.getString("fullName").orEmpty(),
                    emailNormalized = doc.getString("emailNormalized").orEmpty().lowercase(),
                    phone = doc.getString("phone").orEmpty(),
                    packageTier = doc.getString("packageTier") ?: "BASIC",
                    status = doc.getString("status") ?: ClientStatus.AKTIF.name,
                    goal = doc.getString("goal").orEmpty(),
                    startDateMillis = doc.getLong("startDateMillis") ?: updated,
                    endDateMillis = doc.getLong("endDateMillis") ?: updated,
                    totalSessionsQuota = (doc.getLong("totalSessionsQuota") ?: 4L).toInt(),
                    notes = doc.getString("notes").orEmpty(),
                    createdAt = doc.tsMillis("createdAt", updated),
                    updatedAt = updated,
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertClients(list)
        }

        launchCollectionSync("measurements") { docs ->
            val list = docs.map { doc ->
                MeasurementEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    recordedAt = doc.getLong("recordedAt") ?: doc.tsMillis("updatedAt"),
                    waistCm = doc.getDouble("waistCm") ?: 0.0,
                    neckCm = doc.getDouble("neckCm"),
                    armCm = doc.getDouble("armCm"),
                    weightKg = doc.getDouble("weightKg"),
                    notes = doc.getString("notes").orEmpty(),
                    updatedAt = doc.tsMillis("updatedAt"),
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertMeasurements(list)
        }

        launchCollectionSync("bodyCompositions") { docs ->
            val list = docs.map { doc ->
                BodyCompositionEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    recordedAt = doc.getLong("recordedAt") ?: doc.tsMillis("updatedAt"),
                    bodyFatPercent = doc.getDouble("bodyFatPercent") ?: 0.0,
                    muscleMassKg = doc.getDouble("muscleMassKg") ?: 0.0,
                    bodyAgeYears = (doc.getLong("bodyAgeYears") ?: 25L).toInt(),
                    notes = doc.getString("notes").orEmpty(),
                    updatedAt = doc.tsMillis("updatedAt"),
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertBodyCompositions(list)
        }

        launchCollectionSync("progressPhotos") { docs ->
            val existingLocalPaths = dao.getAllPhotosIncludingDeleted().associate { it.id to it.localOriginalPath }
            val list = docs.map { doc ->
                ProgressPhotoEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    angle = doc.getString("angle") ?: "DEPAN",
                    takenAt = doc.getLong("takenAt") ?: doc.tsMillis("updatedAt"),
                    localOriginalPath = existingLocalPaths[doc.id] ?: doc.getString("localOriginalPath").orEmpty(),
                    previewBase64 = doc.getString("previewBase64").orEmpty(),
                    driveFileId = doc.getString("driveFileId").orEmpty(),
                    notes = doc.getString("notes").orEmpty(),
                    updatedAt = doc.tsMillis("updatedAt"),
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertPhotos(list)
        }

        launchCollectionSync("workoutExercises") { docs ->
            val list = docs.map { doc ->
                WorkoutExerciseEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    programDay = doc.getString("programDay") ?: "Hari 1",
                    exerciseName = doc.getString("exerciseName").orEmpty(),
                    sets = (doc.getLong("sets") ?: 3L).toInt(),
                    reps = doc.getString("reps") ?: "10-12",
                    loadNote = doc.getString("loadNote").orEmpty(),
                    restSeconds = (doc.getLong("restSeconds") ?: 60L).toInt(),
                    coachNotes = doc.getString("coachNotes").orEmpty(),
                    sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
                    updatedAt = doc.tsMillis("updatedAt"),
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertWorkoutExercises(list)
        }

        launchCollectionSync("workoutRequests") { docs ->
            val list = docs.map { doc ->
                val updated = doc.tsMillis("updatedAt")
                WorkoutRequestEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    clientName = doc.getString("clientName").orEmpty(),
                    requestedExercise = doc.getString("requestedExercise").orEmpty(),
                    reason = doc.getString("reason").orEmpty(),
                    status = doc.getString("status") ?: "MENUNGGU",
                    coachReply = doc.getString("coachReply").orEmpty(),
                    createdAt = doc.tsMillis("createdAt", updated),
                    updatedAt = updated
                )
            }
            if (list.isNotEmpty()) dao.upsertWorkoutRequests(list)
        }

        launchCollectionSync("sessions") { docs ->
            val list = docs.map { doc ->
                val updated = doc.tsMillis("updatedAt")
                SessionEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    clientName = doc.getString("clientName").orEmpty(),
                    title = doc.getString("title") ?: "Sesi Latihan",
                    startAtMillis = doc.getLong("startAtMillis") ?: updated,
                    endAtMillis = doc.getLong("endAtMillis") ?: (updated + 3_600_000L),
                    location = doc.getString("location") ?: "IHC Studio",
                    status = doc.getString("status") ?: "TERJADWAL",
                    recurringGroupId = doc.getString("recurringGroupId"),
                    notes = doc.getString("notes").orEmpty(),
                    createdAt = doc.tsMillis("createdAt", updated),
                    updatedAt = updated,
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertSessions(list)
        }

        launchCollectionSync("rescheduleRequests") { docs ->
            val list = docs.map { doc ->
                val updated = doc.tsMillis("updatedAt")
                RescheduleRequestEntity(
                    id = doc.id,
                    sessionId = doc.getString("sessionId").orEmpty(),
                    clientId = doc.getString("clientId").orEmpty(),
                    clientName = doc.getString("clientName").orEmpty(),
                    originalStartMillis = doc.getLong("originalStartMillis") ?: 0L,
                    requestedStartMillis = doc.getLong("requestedStartMillis") ?: 0L,
                    requestedEndMillis = doc.getLong("requestedEndMillis") ?: 0L,
                    reason = doc.getString("reason").orEmpty(),
                    status = doc.getString("status") ?: "MENUNGGU",
                    coachNote = doc.getString("coachNote").orEmpty(),
                    createdAt = doc.tsMillis("createdAt", updated),
                    updatedAt = updated
                )
            }
            if (list.isNotEmpty()) dao.upsertRescheduleRequests(list)
        }

        launchCollectionSync("clientTaskSchedules") { docs ->
            val list = docs.map { doc ->
                ClientTaskScheduleEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    taskType = doc.getString("taskType") ?: "FOTO_PROGRES",
                    enabled = doc.getBoolean("enabled") ?: true,
                    nextDueMillis = doc.getLong("nextDueMillis"),
                    lastCompletedMillis = doc.getLong("lastCompletedMillis"),
                    frequencyDays = (doc.getLong("frequencyDays") ?: 7L).toInt(),
                    notes = doc.getString("notes").orEmpty(),
                    updatedAt = doc.tsMillis("updatedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertTaskSchedules(list)
        }

        launchCollectionSync("mealPlanNotes") { docs ->
            val list = docs.map { doc ->
                MealPlanNoteEntity(
                    id = doc.id,
                    clientId = doc.getString("clientId").orEmpty(),
                    title = doc.getString("title").orEmpty(),
                    note = doc.getString("note").orEmpty(),
                    updatedAt = doc.tsMillis("updatedAt"),
                    deletedAt = doc.getLong("deletedAt")
                )
            }
            if (list.isNotEmpty()) dao.upsertMealPlanNotes(list)
        }

        launchCollectionSync("activityLogs") { docs ->
            val list = docs.map { doc ->
                ActivityLogEntity(
                    id = doc.id,
                    action = doc.getString("action").orEmpty(),
                    entityType = doc.getString("entityType").orEmpty(),
                    entityId = doc.getString("entityId").orEmpty(),
                    summary = doc.getString("summary").orEmpty(),
                    createdAt = doc.tsMillis("createdAt")
                )
            }
            if (list.isNotEmpty()) dao.insertActivityLogs(list)
        }
    }

    suspend fun upsertPackageRule(rule: PackageRuleEntity) {
        val uid = requireCoachUid()
        val docRef = db.collection("packageRules").document(rule.tier)
        val payload = mapOf(
            "coachUid" to uid,
            "tier" to rule.tier,
            "maxSessionsPerWeek" to rule.maxSessionsPerWeek,
            "canReschedule" to rule.canReschedule,
            "photoAnglesCsv" to rule.photoAnglesCsv,
            "photoFrequencyDays" to rule.photoFrequencyDays,
            "measurementFieldsCsv" to rule.measurementFieldsCsv,
            "measurementFrequencyDays" to rule.measurementFrequencyDays,
            "hasBodyComposition" to rule.hasBodyComposition,
            "bodyCompositionFrequencyDays" to rule.bodyCompositionFrequencyDays,
            "hasMealPlan" to rule.hasMealPlan,
            "mealPlanFrequencyDays" to rule.mealPlanFrequencyDays,
            "workoutUpdateMode" to rule.workoutUpdateMode,
            "workoutFrequencyDays" to rule.workoutFrequencyDays,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertClient(client: ClientEntity, isCreate: Boolean) {
        val uid = requireCoachUid()
        val docRef = db.collection("clients").document(client.id)
        val payload = mutableMapOf<String, Any>(
            "coachUid" to uid,
            "fullName" to client.fullName,
            "emailNormalized" to client.emailNormalized.lowercase(),
            "phone" to client.phone,
            "packageTier" to client.packageTier,
            "status" to client.status,
            "goal" to client.goal,
            "startDateMillis" to client.startDateMillis,
            "endDateMillis" to client.endDateMillis,
            "totalSessionsQuota" to client.totalSessionsQuota,
            "notes" to client.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        client.deletedAt?.let { payload["deletedAt"] = it }

        val shouldSetCreated = isCreate || runCatching { !docRef.get().await().exists() }.getOrDefault(false)
        if (shouldSetCreated) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            executeWrite(OperationType.CREATE, docRef.path) {
                docRef.set(payload).await()
            }
        } else {
            executeWrite(OperationType.UPDATE, docRef.path) {
                docRef.set(payload, SetOptions.merge()).await()
            }
        }

        // Keep clientInvites/{emailNormalized} in sync so web portal registration works immediately
        val emailKey = client.emailNormalized.trim().lowercase()
        if (emailKey.isNotBlank()) {
            val inviteRef = db.collection("clientInvites").document(emailKey)
            val invitePayload = mapOf(
                "coachUid" to uid,
                "clientId" to client.id,
                "packageTier" to client.packageTier,
                "active" to (client.deletedAt == null && client.status != ClientStatus.SELESAI.name),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            executeWrite(OperationType.WRITE, inviteRef.path) {
                inviteRef.set(invitePayload, SetOptions.merge()).await()
            }
        }
    }

    suspend fun upsertMeasurement(item: MeasurementEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("measurements").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "recordedAt" to item.recordedAt,
            "waistCm" to item.waistCm,
            "neckCm" to item.neckCm,
            "armCm" to item.armCm,
            "weightKg" to item.weightKg,
            "notes" to item.notes,
            "deletedAt" to item.deletedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertBodyComposition(item: BodyCompositionEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("bodyCompositions").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "recordedAt" to item.recordedAt,
            "bodyFatPercent" to item.bodyFatPercent,
            "muscleMassKg" to item.muscleMassKg,
            "bodyAgeYears" to item.bodyAgeYears,
            "notes" to item.notes,
            "deletedAt" to item.deletedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertProgressPhoto(item: ProgressPhotoEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("progressPhotos").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "angle" to item.angle,
            "takenAt" to item.takenAt,
            "previewBase64" to item.previewBase64,
            "driveFileId" to item.driveFileId,
            "notes" to item.notes,
            "deletedAt" to item.deletedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertWorkoutExercise(item: WorkoutExerciseEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("workoutExercises").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "programDay" to item.programDay,
            "exerciseName" to item.exerciseName,
            "sets" to item.sets,
            "reps" to item.reps,
            "loadNote" to item.loadNote,
            "restSeconds" to item.restSeconds,
            "coachNotes" to item.coachNotes,
            "sortOrder" to item.sortOrder,
            "deletedAt" to item.deletedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun updateWorkoutRequest(item: WorkoutRequestEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("workoutRequests").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "clientName" to item.clientName,
            "requestedExercise" to item.requestedExercise,
            "reason" to item.reason,
            "status" to item.status,
            "coachReply" to item.coachReply,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertSession(item: SessionEntity, clientEmail: String, isCreate: Boolean) {
        val uid = requireCoachUid()
        val docRef = db.collection("sessions").document(item.id)
        val payload = mutableMapOf<String, Any>(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "clientName" to item.clientName,
            "title" to item.title,
            "startAtMillis" to item.startAtMillis,
            "endAtMillis" to item.endAtMillis,
            "location" to item.location,
            "status" to item.status,
            "notes" to item.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        item.recurringGroupId?.let { payload["recurringGroupId"] = it }
        item.deletedAt?.let { payload["deletedAt"] = it }

        val shouldSetCreated = isCreate || runCatching { !docRef.get().await().exists() }.getOrDefault(false)
        if (shouldSetCreated) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            executeWrite(OperationType.CREATE, docRef.path) {
                docRef.set(payload).await()
            }
        } else {
            executeWrite(OperationType.UPDATE, docRef.path) {
                docRef.set(payload, SetOptions.merge()).await()
            }
        }
    }

    suspend fun updateRescheduleRequest(item: RescheduleRequestEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("rescheduleRequests").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "sessionId" to item.sessionId,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "clientName" to item.clientName,
            "originalStartMillis" to item.originalStartMillis,
            "requestedStartMillis" to item.requestedStartMillis,
            "requestedEndMillis" to item.requestedEndMillis,
            "reason" to item.reason,
            "status" to item.status,
            "coachNote" to item.coachNote,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        executeWrite(OperationType.UPDATE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertTaskSchedule(item: ClientTaskScheduleEntity) {
        val uid = requireCoachUid()
        val docRef = db.collection("clientTaskSchedules").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "taskType" to item.taskType,
            "enabled" to item.enabled,
            "nextDueMillis" to item.nextDueMillis,
            "lastCompletedMillis" to item.lastCompletedMillis,
            "frequencyDays" to item.frequencyDays,
            "notes" to item.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun upsertMealPlanNote(item: MealPlanNoteEntity, clientEmail: String) {
        val uid = requireCoachUid()
        val docRef = db.collection("mealPlanNotes").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "clientId" to item.clientId,
            "clientEmail" to clientEmail.lowercase(),
            "title" to item.title,
            "note" to item.note,
            "deletedAt" to item.deletedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        executeWrite(OperationType.WRITE, docRef.path) {
            docRef.set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun insertActivityLog(item: ActivityLogEntity) {
        val uid = requireCoachUid()
        val docRef = db.collection("activityLogs").document(item.id)
        val payload = mapOf(
            "coachUid" to uid,
            "action" to item.action,
            "entityType" to item.entityType,
            "entityId" to item.entityId,
            "summary" to item.summary,
            "createdAt" to FieldValue.serverTimestamp()
        )
        executeWrite(OperationType.CREATE, docRef.path) {
            docRef.set(payload).await()
        }
    }

    /**
     * Pushes any locally cached records (created offline or pre-Firebase) up to Firestore once signed in.
     */
    suspend fun pushLocalCacheSnapshot(dao: IhcDao) {
        val uid = currentCoachUid() ?: return
        val clientsById = dao.getAllClientsIncludingDeleted().associateBy { it.id }

        val existingRuleDocs = runCatching {
            db.collection("packageRules").whereEqualTo("coachUid", uid).get().await()
        }.getOrNull()
        if (existingRuleDocs == null || existingRuleDocs.isEmpty) {
            dao.getAllPackageRules().forEach { upsertPackageRule(it) }
        }

        val existingClientDocs = runCatching {
            db.collection("clients").whereEqualTo("coachUid", uid).get().await().documents.map { it.id }.toSet()
        }.getOrDefault(emptySet())
        clientsById.values.filter { it.id !in existingClientDocs }.forEach { client ->
            upsertClient(client, isCreate = true)
        }

        dao.getAllTaskSchedules().forEach { sched ->
            val docExists = runCatching {
                db.collection("clientTaskSchedules").document(sched.id).get().await().exists()
            }.getOrDefault(true)
            if (!docExists) upsertTaskSchedule(sched)
        }

        dao.getAllSessionsIncludingDeleted().forEach { session ->
            val docExists = runCatching {
                db.collection("sessions").document(session.id).get().await().exists()
            }.getOrDefault(true)
            val email = clientsById[session.clientId]?.emailNormalized.orEmpty()
            if (!docExists && email.isNotBlank()) upsertSession(session, email, isCreate = true)
        }

        dao.getAllMeasurementsIncludingDeleted().forEach { item ->
            val email = clientsById[item.clientId]?.emailNormalized.orEmpty()
            val exists = runCatching { db.collection("measurements").document(item.id).get().await().exists() }.getOrDefault(true)
            if (!exists && email.isNotBlank()) upsertMeasurement(item, email)
        }

        dao.getAllBodyCompositionsIncludingDeleted().forEach { item ->
            val email = clientsById[item.clientId]?.emailNormalized.orEmpty()
            val exists = runCatching { db.collection("bodyCompositions").document(item.id).get().await().exists() }.getOrDefault(true)
            if (!exists && email.isNotBlank()) upsertBodyComposition(item, email)
        }

        dao.getAllPhotosIncludingDeleted().forEach { item ->
            val email = clientsById[item.clientId]?.emailNormalized.orEmpty()
            val exists = runCatching { db.collection("progressPhotos").document(item.id).get().await().exists() }.getOrDefault(true)
            if (!exists && email.isNotBlank()) upsertProgressPhoto(item, email)
        }

        dao.getAllWorkoutsIncludingDeleted().forEach { item ->
            val email = clientsById[item.clientId]?.emailNormalized.orEmpty()
            val exists = runCatching { db.collection("workoutExercises").document(item.id).get().await().exists() }.getOrDefault(true)
            if (!exists && email.isNotBlank()) upsertWorkoutExercise(item, email)
        }

        dao.getAllMealPlanNotesIncludingDeleted().forEach { item ->
            val email = clientsById[item.clientId]?.emailNormalized.orEmpty()
            val exists = runCatching { db.collection("mealPlanNotes").document(item.id).get().await().exists() }.getOrDefault(true)
            if (!exists && email.isNotBlank()) upsertMealPlanNote(item, email)
        }
    }

    private suspend inline fun executeWrite(
        op: OperationType,
        path: String,
        crossinline block: suspend () -> Unit
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, path, auth)
            throw e
        }
    }
}
