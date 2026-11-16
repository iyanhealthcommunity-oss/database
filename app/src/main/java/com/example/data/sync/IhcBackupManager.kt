package com.example.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import com.example.data.local.IhcDao
import com.example.data.model.ActivityLogEntity
import com.example.data.model.BodyCompositionEntity
import com.example.data.model.ClientEntity
import com.example.data.model.ClientTaskScheduleEntity
import com.example.data.model.MealPlanNoteEntity
import com.example.data.model.MeasurementEntity
import com.example.data.model.PackageRuleEntity
import com.example.data.model.ProgressPhotoEntity
import com.example.data.model.RescheduleRequestEntity
import com.example.data.model.SessionEntity
import com.example.data.model.WorkoutExerciseEntity
import com.example.data.model.WorkoutRequestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class SyncOutcome {
    data class Success(val message: String, val syncedAtMillis: Long) : SyncOutcome()
    data class Error(val reason: String) : SyncOutcome()
}

class IhcBackupManager(
    private val context: Context,
    private val dao: IhcDao
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Copies original selected photo into app private storage and produces a compressed JPEG preview
     * (max 480px) encoded as Base64 for lightweight client-portal preview sync.
     */
    suspend fun saveProgressPhotoLocally(sourceUri: Uri, clientId: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "progress_photos/$clientId").apply { mkdirs() }
            val originalFile = File(dir, "orig_${UUID.randomUUID()}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(originalFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Tidak dapat membaca file foto dari galeri.")

            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
            val previewBase64 = if (bitmap != null) {
                val maxDim = 480f
                val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 68, baos)
                if (scaled != bitmap) scaled.recycle()
                bitmap.recycle()
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            } else {
                ""
            }

            originalFile.absolutePath to previewBase64
        }

    suspend fun exportAllToJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())

        root.put("packageRules", JSONArray().apply {
            dao.getAllPackageRules().forEach { r ->
                put(JSONObject().apply {
                    put("tier", r.tier)
                    put("maxSessionsPerWeek", r.maxSessionsPerWeek)
                    put("canReschedule", r.canReschedule)
                    put("photoAnglesCsv", r.photoAnglesCsv)
                    put("photoFrequencyDays", r.photoFrequencyDays)
                    put("measurementFieldsCsv", r.measurementFieldsCsv)
                    put("measurementFrequencyDays", r.measurementFrequencyDays)
                    put("hasBodyComposition", r.hasBodyComposition)
                    put("bodyCompositionFrequencyDays", r.bodyCompositionFrequencyDays)
                    put("hasMealPlan", r.hasMealPlan)
                    put("mealPlanFrequencyDays", r.mealPlanFrequencyDays)
                    put("workoutUpdateMode", r.workoutUpdateMode)
                    put("workoutFrequencyDays", r.workoutFrequencyDays)
                    put("updatedAt", r.updatedAt)
                })
            }
        })

        root.put("clients", JSONArray().apply {
            dao.getAllClientsIncludingDeleted().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("fullName", c.fullName)
                    put("emailNormalized", c.emailNormalized)
                    put("phone", c.phone)
                    put("packageTier", c.packageTier)
                    put("status", c.status)
                    put("goal", c.goal)
                    put("startDateMillis", c.startDateMillis)
                    put("endDateMillis", c.endDateMillis)
                    put("totalSessionsQuota", c.totalSessionsQuota)
                    put("notes", c.notes)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                    if (c.deletedAt != null) put("deletedAt", c.deletedAt)
                })
            }
        })

        root.put("measurements", JSONArray().apply {
            dao.getAllMeasurementsIncludingDeleted().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("clientId", m.clientId)
                    put("recordedAt", m.recordedAt)
                    put("waistCm", m.waistCm)
                    if (m.neckCm != null) put("neckCm", m.neckCm)
                    if (m.armCm != null) put("armCm", m.armCm)
                    if (m.weightKg != null) put("weightKg", m.weightKg)
                    put("notes", m.notes)
                    put("updatedAt", m.updatedAt)
                    if (m.deletedAt != null) put("deletedAt", m.deletedAt)
                })
            }
        })

        root.put("bodyCompositions", JSONArray().apply {
            dao.getAllBodyCompositionsIncludingDeleted().forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("clientId", b.clientId)
                    put("recordedAt", b.recordedAt)
                    put("bodyFatPercent", b.bodyFatPercent)
                    put("muscleMassKg", b.muscleMassKg)
                    put("bodyAgeYears", b.bodyAgeYears)
                    put("notes", b.notes)
                    put("updatedAt", b.updatedAt)
                    if (b.deletedAt != null) put("deletedAt", b.deletedAt)
                })
            }
        })

        root.put("progressPhotos", JSONArray().apply {
            dao.getAllPhotosIncludingDeleted().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("clientId", p.clientId)
                    put("angle", p.angle)
                    put("takenAt", p.takenAt)
                    put("localOriginalPath", p.localOriginalPath)
                    put("previewBase64", p.previewBase64)
                    put("driveFileId", p.driveFileId)
                    put("notes", p.notes)
                    put("updatedAt", p.updatedAt)
                    if (p.deletedAt != null) put("deletedAt", p.deletedAt)
                })
            }
        })

        root.put("workoutExercises", JSONArray().apply {
            dao.getAllWorkoutsIncludingDeleted().forEach { w ->
                put(JSONObject().apply {
                    put("id", w.id)
                    put("clientId", w.clientId)
                    put("programDay", w.programDay)
                    put("exerciseName", w.exerciseName)
                    put("sets", w.sets)
                    put("reps", w.reps)
                    put("loadNote", w.loadNote)
                    put("restSeconds", w.restSeconds)
                    put("coachNotes", w.coachNotes)
                    put("sortOrder", w.sortOrder)
                    put("updatedAt", w.updatedAt)
                    if (w.deletedAt != null) put("deletedAt", w.deletedAt)
                })
            }
        })

        root.put("workoutRequests", JSONArray().apply {
            dao.getAllWorkoutRequests().forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("clientId", r.clientId)
                    put("clientName", r.clientName)
                    put("requestedExercise", r.requestedExercise)
                    put("reason", r.reason)
                    put("status", r.status)
                    put("coachReply", r.coachReply)
                    put("createdAt", r.createdAt)
                    put("updatedAt", r.updatedAt)
                })
            }
        })

        root.put("sessions", JSONArray().apply {
            dao.getAllSessionsIncludingDeleted().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("clientId", s.clientId)
                    put("clientName", s.clientName)
                    put("title", s.title)
                    put("startAtMillis", s.startAtMillis)
                    put("endAtMillis", s.endAtMillis)
                    put("location", s.location)
                    put("status", s.status)
                    if (s.recurringGroupId != null) put("recurringGroupId", s.recurringGroupId)
                    put("notes", s.notes)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                    if (s.deletedAt != null) put("deletedAt", s.deletedAt)
                })
            }
        })

        root.put("rescheduleRequests", JSONArray().apply {
            dao.getAllRescheduleRequests().forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("sessionId", r.sessionId)
                    put("clientId", r.clientId)
                    put("clientName", r.clientName)
                    put("originalStartMillis", r.originalStartMillis)
                    put("requestedStartMillis", r.requestedStartMillis)
                    put("requestedEndMillis", r.requestedEndMillis)
                    put("reason", r.reason)
                    put("status", r.status)
                    put("coachNote", r.coachNote)
                    put("createdAt", r.createdAt)
                    put("updatedAt", r.updatedAt)
                })
            }
        })

        root.put("clientTaskSchedules", JSONArray().apply {
            dao.getAllTaskSchedules().forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("clientId", t.clientId)
                    put("taskType", t.taskType)
                    put("enabled", t.enabled)
                    if (t.nextDueMillis != null) put("nextDueMillis", t.nextDueMillis)
                    if (t.lastCompletedMillis != null) put("lastCompletedMillis", t.lastCompletedMillis)
                    put("frequencyDays", t.frequencyDays)
                    put("notes", t.notes)
                    put("updatedAt", t.updatedAt)
                })
            }
        })

        root.put("mealPlanNotes", JSONArray().apply {
            dao.getAllMealPlanNotesIncludingDeleted().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("clientId", m.clientId)
                    put("title", m.title)
                    put("note", m.note)
                    put("updatedAt", m.updatedAt)
                    if (m.deletedAt != null) put("deletedAt", m.deletedAt)
                })
            }
        })

        root.put("activityLogs", JSONArray().apply {
            dao.getAllActivityLogs().forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("action", l.action)
                    put("entityType", l.entityType)
                    put("entityId", l.entityId)
                    put("summary", l.summary)
                    put("createdAt", l.createdAt)
                })
            }
        })

        root.toString(2)
    }

    /**
     * Timestamp-aware merge: only overwrites local entity when incoming updatedAt > local updatedAt.
     */
    suspend fun mergeFromJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        var mergedCount = 0

        root.optJSONArray("packageRules")?.let { arr ->
            val existing = dao.getAllPackageRules().associateBy { it.tier }
            val toWrite = mutableListOf<PackageRuleEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tier = o.getString("tier")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[tier]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        PackageRuleEntity(
                            tier = tier,
                            maxSessionsPerWeek = o.getInt("maxSessionsPerWeek"),
                            canReschedule = o.getBoolean("canReschedule"),
                            photoAnglesCsv = o.getString("photoAnglesCsv"),
                            photoFrequencyDays = o.getInt("photoFrequencyDays"),
                            measurementFieldsCsv = o.getString("measurementFieldsCsv"),
                            measurementFrequencyDays = o.getInt("measurementFrequencyDays"),
                            hasBodyComposition = o.getBoolean("hasBodyComposition"),
                            bodyCompositionFrequencyDays = o.getInt("bodyCompositionFrequencyDays"),
                            hasMealPlan = o.getBoolean("hasMealPlan"),
                            mealPlanFrequencyDays = o.getInt("mealPlanFrequencyDays"),
                            workoutUpdateMode = o.getString("workoutUpdateMode"),
                            workoutFrequencyDays = o.getInt("workoutFrequencyDays"),
                            updatedAt = incomingUpdated
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertPackageRules(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("clients")?.let { arr ->
            val existing = dao.getAllClientsIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<ClientEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        ClientEntity(
                            id = id,
                            fullName = o.getString("fullName"),
                            emailNormalized = o.getString("emailNormalized").lowercase(),
                            phone = o.optString("phone"),
                            packageTier = o.getString("packageTier"),
                            status = o.getString("status"),
                            goal = o.optString("goal"),
                            startDateMillis = o.getLong("startDateMillis"),
                            endDateMillis = o.getLong("endDateMillis"),
                            totalSessionsQuota = o.optInt("totalSessionsQuota", 4),
                            notes = o.optString("notes"),
                            createdAt = o.optLong("createdAt", incomingUpdated),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertClients(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("measurements")?.let { arr ->
            val existing = dao.getAllMeasurementsIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<MeasurementEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        MeasurementEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            recordedAt = o.getLong("recordedAt"),
                            waistCm = o.getDouble("waistCm"),
                            neckCm = if (o.has("neckCm") && !o.isNull("neckCm")) o.getDouble("neckCm") else null,
                            armCm = if (o.has("armCm") && !o.isNull("armCm")) o.getDouble("armCm") else null,
                            weightKg = if (o.has("weightKg") && !o.isNull("weightKg")) o.getDouble("weightKg") else null,
                            notes = o.optString("notes"),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertMeasurements(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("bodyCompositions")?.let { arr ->
            val existing = dao.getAllBodyCompositionsIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<BodyCompositionEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        BodyCompositionEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            recordedAt = o.getLong("recordedAt"),
                            bodyFatPercent = o.getDouble("bodyFatPercent"),
                            muscleMassKg = o.getDouble("muscleMassKg"),
                            bodyAgeYears = o.getInt("bodyAgeYears"),
                            notes = o.optString("notes"),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertBodyCompositions(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("progressPhotos")?.let { arr ->
            val existing = dao.getAllPhotosIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<ProgressPhotoEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        ProgressPhotoEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            angle = o.getString("angle"),
                            takenAt = o.getLong("takenAt"),
                            localOriginalPath = o.optString("localOriginalPath"),
                            previewBase64 = o.optString("previewBase64"),
                            driveFileId = o.optString("driveFileId"),
                            notes = o.optString("notes"),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertPhotos(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("workoutExercises")?.let { arr ->
            val existing = dao.getAllWorkoutsIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<WorkoutExerciseEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        WorkoutExerciseEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            programDay = o.optString("programDay", "Hari 1"),
                            exerciseName = o.getString("exerciseName"),
                            sets = o.getInt("sets"),
                            reps = o.getString("reps"),
                            loadNote = o.optString("loadNote"),
                            restSeconds = o.optInt("restSeconds", 60),
                            coachNotes = o.optString("coachNotes"),
                            sortOrder = o.optInt("sortOrder", 0),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertWorkoutExercises(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("workoutRequests")?.let { arr ->
            val existing = dao.getAllWorkoutRequests().associateBy { it.id }
            val toWrite = mutableListOf<WorkoutRequestEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        WorkoutRequestEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            clientName = o.getString("clientName"),
                            requestedExercise = o.getString("requestedExercise"),
                            reason = o.optString("reason"),
                            status = o.getString("status"),
                            coachReply = o.optString("coachReply"),
                            createdAt = o.optLong("createdAt", incomingUpdated),
                            updatedAt = incomingUpdated
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertWorkoutRequests(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("sessions")?.let { arr ->
            val existing = dao.getAllSessionsIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<SessionEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        SessionEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            clientName = o.getString("clientName"),
                            title = o.optString("title", "Sesi Latihan"),
                            startAtMillis = o.getLong("startAtMillis"),
                            endAtMillis = o.getLong("endAtMillis"),
                            location = o.optString("location", "IHC Studio"),
                            status = o.getString("status"),
                            recurringGroupId = if (o.has("recurringGroupId") && !o.isNull("recurringGroupId")) o.getString("recurringGroupId") else null,
                            notes = o.optString("notes"),
                            createdAt = o.optLong("createdAt", incomingUpdated),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertSessions(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("rescheduleRequests")?.let { arr ->
            val existing = dao.getAllRescheduleRequests().associateBy { it.id }
            val toWrite = mutableListOf<RescheduleRequestEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        RescheduleRequestEntity(
                            id = id,
                            sessionId = o.getString("sessionId"),
                            clientId = o.getString("clientId"),
                            clientName = o.getString("clientName"),
                            originalStartMillis = o.getLong("originalStartMillis"),
                            requestedStartMillis = o.getLong("requestedStartMillis"),
                            requestedEndMillis = o.getLong("requestedEndMillis"),
                            reason = o.optString("reason"),
                            status = o.getString("status"),
                            coachNote = o.optString("coachNote"),
                            createdAt = o.optLong("createdAt", incomingUpdated),
                            updatedAt = incomingUpdated
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertRescheduleRequests(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("clientTaskSchedules")?.let { arr ->
            val existing = dao.getAllTaskSchedules().associateBy { it.id }
            val toWrite = mutableListOf<ClientTaskScheduleEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        ClientTaskScheduleEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            taskType = o.getString("taskType"),
                            enabled = o.optBoolean("enabled", true),
                            nextDueMillis = if (o.has("nextDueMillis") && !o.isNull("nextDueMillis")) o.getLong("nextDueMillis") else null,
                            lastCompletedMillis = if (o.has("lastCompletedMillis") && !o.isNull("lastCompletedMillis")) o.getLong("lastCompletedMillis") else null,
                            frequencyDays = o.optInt("frequencyDays", 7),
                            notes = o.optString("notes"),
                            updatedAt = incomingUpdated
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertTaskSchedules(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("mealPlanNotes")?.let { arr ->
            val existing = dao.getAllMealPlanNotesIncludingDeleted().associateBy { it.id }
            val toWrite = mutableListOf<MealPlanNoteEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val incomingUpdated = o.optLong("updatedAt", 0L)
                val current = existing[id]
                if (current == null || incomingUpdated > current.updatedAt) {
                    toWrite.add(
                        MealPlanNoteEntity(
                            id = id,
                            clientId = o.getString("clientId"),
                            title = o.getString("title"),
                            note = o.optString("note"),
                            updatedAt = incomingUpdated,
                            deletedAt = if (o.has("deletedAt") && !o.isNull("deletedAt")) o.getLong("deletedAt") else null
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertMealPlanNotes(toWrite)
                mergedCount += toWrite.size
            }
        }

        root.optJSONArray("activityLogs")?.let { arr ->
            val existingIds = dao.getAllActivityLogs().map { it.id }.toSet()
            val toWrite = mutableListOf<ActivityLogEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                if (id !in existingIds) {
                    toWrite.add(
                        ActivityLogEntity(
                            id = id,
                            action = o.getString("action"),
                            entityType = o.getString("entityType"),
                            entityId = o.getString("entityId"),
                            summary = o.getString("summary"),
                            createdAt = o.getLong("createdAt")
                        )
                    )
                }
            }
            if (toWrite.isNotEmpty()) {
                dao.insertActivityLogs(toWrite)
                mergedCount += toWrite.size
            }
        }

        mergedCount
    }

    /**
     * Syncs the complete IHC snapshot + original photos to Google Drive appDataFolder using Drive API v3.
     * Enforces timestamp check before overwriting and handles offline / quota / auth errors cleanly.
     */
    suspend fun syncWithGoogleDriveAppData(accessToken: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext SyncOutcome.Error("Perangkat sedang offline. Sinkronisasi Google Drive akan dilanjutkan saat koneksi tersedia.")
        }
        if (accessToken.isBlank()) {
            return@withContext SyncOutcome.Error("Token OAuth Google Drive (appDataFolder) belum tersedia. Gunakan Ekspor Backup JSON di Pengaturan atau hubungkan Google OAuth.")
        }

        try {
            val listReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%3D%27ihc_backup.json%27&fields=files(id,name,modifiedTime)")
                .header("Authorization", "Bearer $accessToken")
                .build()

            val existingFileId = httpClient.newCall(listReq).execute().use { listResp ->
                if (listResp.code == 401 || listResp.code == 403) {
                    val errBody = listResp.body?.string().orEmpty()
                    if (errBody.contains("storageQuotaExceeded", ignoreCase = true)) {
                        return@withContext SyncOutcome.Error("Penyimpanan Google Drive penuh (storageQuotaExceeded). Kosongkan ruang Drive Anda terlebih dahulu.")
                    }
                    return@withContext SyncOutcome.Error("Izin akses Google Drive tidak valid atau kedaluwarsa (HTTP ${listResp.code}).")
                }
                if (!listResp.isSuccessful) {
                    return@withContext SyncOutcome.Error("Gagal memeriksa folder aplikasi Drive (HTTP ${listResp.code}).")
                }

                val filesArray = JSONObject(listResp.body?.string().orEmpty()).optJSONArray("files")
                if (filesArray != null && filesArray.length() > 0) {
                    filesArray.getJSONObject(0).getString("id")
                } else {
                    null
                }
            }

            // 1. Pull remote JSON first and merge newest timestamps so we never overwrite newer remote data blindly
            if (existingFileId != null) {
                val downloadReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$existingFileId?alt=media")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                httpClient.newCall(downloadReq).execute().use { downloadResp ->
                    if (downloadResp.isSuccessful) {
                        val remoteJson = downloadResp.body?.string()
                        if (!remoteJson.isNullOrBlank()) {
                            mergeFromJson(remoteJson)
                        }
                    }
                }
            }

            // 2. Upload original photo files not yet in Drive, and download original photos missing locally (reinstall/new device)
            var uploadedPhotos = 0
            var restoredPhotos = 0
            val allPhotos = dao.getAllPhotosIncludingDeleted().filter { it.deletedAt == null }
            for (photo in allPhotos) {
                val localFile = File(photo.localOriginalPath)
                if (photo.driveFileId.isBlank() && photo.localOriginalPath.isNotBlank() && localFile.exists()) {
                    val uploadedId = uploadOriginalPhotoToDrive(accessToken, photo, localFile)
                    if (uploadedId != null) {
                        dao.upsertPhoto(
                            photo.copy(
                                driveFileId = uploadedId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        uploadedPhotos++
                    }
                } else if (photo.driveFileId.isNotBlank() && !localFile.exists()) {
                    val restoredPath = downloadOriginalPhotoFromDrive(accessToken, photo)
                    if (restoredPath != null) {
                        dao.upsertPhoto(
                            photo.copy(
                                localOriginalPath = restoredPath,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        restoredPhotos++
                    }
                }
            }

            val mergedPayload = exportAllToJson()
            val metadataJson = JSONObject().apply {
                put("name", "ihc_backup.json")
                if (existingFileId == null) {
                    put("parents", JSONArray().put("appDataFolder"))
                }
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    mergedPayload.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .build()

            val uploadUrl = if (existingFileId == null) {
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            } else {
                "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart"
            }

            val uploadReqBuilder = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $accessToken")

            val uploadReq = if (existingFileId == null) {
                uploadReqBuilder.post(multipartBody).build()
            } else {
                uploadReqBuilder.patch(multipartBody).build()
            }

            httpClient.newCall(uploadReq).execute().use { uploadResp ->
                if (!uploadResp.isSuccessful) {
                    val errBody = uploadResp.body?.string().orEmpty()
                    if (errBody.contains("storageQuotaExceeded", ignoreCase = true)) {
                        return@withContext SyncOutcome.Error("Google Drive penuh. Tidak dapat menyimpan cadangan.")
                    }
                    return@withContext SyncOutcome.Error("Gagal mengunggah cadangan ke Google Drive (HTTP ${uploadResp.code}).")
                }
            }

            val now = System.currentTimeMillis()
            SyncOutcome.Success(
                "Sinkronisasi appDataFolder berhasil (foto diunggah: $uploadedPhotos, foto dipulihkan: $restoredPhotos).",
                now
            )
        } catch (e: Exception) {
            SyncOutcome.Error("Gagal sinkronisasi Drive: ${e.localizedMessage ?: "Kesalahan jaringan"}")
        }
    }

    private fun uploadOriginalPhotoToDrive(
        accessToken: String,
        photo: ProgressPhotoEntity,
        file: File
    ): String? {
        val metaJson = JSONObject().apply {
            put("name", "ihc_photo_${photo.id}.jpg")
            put("parents", JSONArray().put("appDataFolder"))
        }.toString()
        val bytes = file.readBytes()
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metaJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val respJson = JSONObject(resp.body?.string().orEmpty())
            respJson.optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun downloadOriginalPhotoFromDrive(
        accessToken: String,
        photo: ProgressPhotoEntity
    ): String? {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${photo.driveFileId}?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val dir = File(context.filesDir, "progress_photos/${photo.clientId}").apply { mkdirs() }
            val target = File(dir, "orig_${photo.id}.jpg")
            FileOutputStream(target).use { it.write(bytes) }
            target.absolutePath
        }
    }
}
