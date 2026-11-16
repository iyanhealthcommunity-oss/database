package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val WIB_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))
private val DAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale("id", "ID"))
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID"))
private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm 'WIB'", Locale("id", "ID"))

fun formatWibDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(WIB_ZONE).toLocalDate().format(DATE_FORMATTER)

fun formatWibDayDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(WIB_ZONE).toLocalDate().format(DAY_DATE_FORMATTER)

fun formatWibTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(WIB_ZONE).toLocalTime().format(TIME_FORMATTER)

fun formatWibDateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(WIB_ZONE).format(DATE_TIME_FORMATTER)

fun localDateToStartOfDayWib(date: LocalDate): Long =
    date.atStartOfDay(WIB_ZONE).toInstant().toEpochMilli()

fun epochMillisToWibDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(WIB_ZONE).toLocalDate()

fun todayStartWib(): Long = localDateToStartOfDayWib(LocalDate.now(WIB_ZONE))

fun todayEndWib(): Long = localDateToStartOfDayWib(LocalDate.now(WIB_ZONE).plusDays(1)) - 1L

enum class PackageTier(val label: String, val sortPriority: Int) {
    PREMIUM("Premium", 0),
    STANDARD("Standard", 1),
    BASIC("Basic", 2);

    companion object {
        fun fromString(value: String): PackageTier =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BASIC
    }
}

enum class ClientStatus(val label: String) {
    AKTIF("Aktif"),
    JEDA("Jeda"),
    SELESAI("Selesai");

    companion object {
        fun fromString(value: String): ClientStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AKTIF
    }
}

enum class SessionStatus(val label: String) {
    TERJADWAL("Terjadwal"),
    HADIR("Hadir"),
    TIDAK_HADIR("Tidak Hadir"),
    DIBATALKAN("Dibatalkan");

    companion object {
        fun fromString(value: String): SessionStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TERJADWAL
    }
}

enum class PhotoAngle(val label: String) {
    DEPAN("Depan"),
    BELAKANG("Belakang"),
    SAMPING_KANAN("Samping Kanan"),
    SAMPING_KIRI("Samping Kiri");

    companion object {
        fun fromString(value: String): PhotoAngle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEPAN
    }
}

enum class RequestStatus(val label: String) {
    MENUNGGU("Menunggu"),
    DISETUJUI("Disetujui"),
    DITOLAK("Ditolak"),
    DIBALAS("Dibalas");

    companion object {
        fun fromString(value: String): RequestStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MENUNGGU
    }
}

enum class TaskType(val label: String, val targetTabIndex: Int) {
    FOTO_PROGRES("Foto Progres", 2),
    PENGUKURAN_LINGKAR("Pengukuran Lingkar", 1),
    KOMPOSISI_TUBUH("Komposisi Tubuh", 1),
    MEAL_PLAN("Update Meal Plan", 3),
    WORKOUT("Update Program Workout", 3);

    companion object {
        fun fromString(value: String): TaskType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FOTO_PROGRES
    }
}

enum class WorkoutUpdateMode(val label: String) {
    NONE("Tanpa jadwal otomatis"),
    WEEKLY("Otomatis 1x seminggu"),
    FLEXIBLE("Fleksibel sesuai progres")
}

@Entity(tableName = "package_rules")
data class PackageRuleEntity(
    @PrimaryKey val tier: String,
    val maxSessionsPerWeek: Int,
    val canReschedule: Boolean,
    val photoAnglesCsv: String,
    val photoFrequencyDays: Int,
    val measurementFieldsCsv: String,
    val measurementFrequencyDays: Int,
    val hasBodyComposition: Boolean,
    val bodyCompositionFrequencyDays: Int,
    val hasMealPlan: Boolean,
    val mealPlanFrequencyDays: Int,
    val workoutUpdateMode: String,
    val workoutFrequencyDays: Int,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun allowedAngles(): List<PhotoAngle> =
        photoAnglesCsv.split(",").mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) null else PhotoAngle.fromString(trimmed)
        }.ifEmpty { listOf(PhotoAngle.DEPAN) }

    fun allowedMeasurementFields(): List<String> =
        measurementFieldsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf("PERUT") }

    companion object {
        fun defaults(): List<PackageRuleEntity> {
            val now = System.currentTimeMillis()
            return listOf(
                PackageRuleEntity(
                    tier = PackageTier.BASIC.name,
                    maxSessionsPerWeek = 1,
                    canReschedule = false,
                    photoAnglesCsv = PhotoAngle.DEPAN.name,
                    photoFrequencyDays = 7,
                    measurementFieldsCsv = "PERUT",
                    measurementFrequencyDays = 30,
                    hasBodyComposition = false,
                    bodyCompositionFrequencyDays = 0,
                    hasMealPlan = false,
                    mealPlanFrequencyDays = 0,
                    workoutUpdateMode = WorkoutUpdateMode.NONE.name,
                    workoutFrequencyDays = 0,
                    updatedAt = now
                ),
                PackageRuleEntity(
                    tier = PackageTier.STANDARD.name,
                    maxSessionsPerWeek = 3,
                    canReschedule = true,
                    photoAnglesCsv = PhotoAngle.DEPAN.name,
                    photoFrequencyDays = 7,
                    measurementFieldsCsv = "PERUT,LEHER,LENGAN",
                    measurementFrequencyDays = 7,
                    hasBodyComposition = false,
                    bodyCompositionFrequencyDays = 0,
                    hasMealPlan = true,
                    mealPlanFrequencyDays = 14,
                    workoutUpdateMode = WorkoutUpdateMode.WEEKLY.name,
                    workoutFrequencyDays = 7,
                    updatedAt = now
                ),
                PackageRuleEntity(
                    tier = PackageTier.PREMIUM.name,
                    maxSessionsPerWeek = 6,
                    canReschedule = true,
                    photoAnglesCsv = "${PhotoAngle.DEPAN.name},${PhotoAngle.BELAKANG.name},${PhotoAngle.SAMPING_KANAN.name},${PhotoAngle.SAMPING_KIRI.name}",
                    photoFrequencyDays = 7,
                    measurementFieldsCsv = "PERUT,LEHER,LENGAN",
                    measurementFrequencyDays = 7,
                    hasBodyComposition = true,
                    bodyCompositionFrequencyDays = 30,
                    hasMealPlan = true,
                    mealPlanFrequencyDays = 7,
                    workoutUpdateMode = WorkoutUpdateMode.FLEXIBLE.name,
                    workoutFrequencyDays = 0,
                    updatedAt = now
                )
            )
        }
    }
}

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val emailNormalized: String,
    val phone: String = "",
    val packageTier: String = PackageTier.BASIC.name,
    val status: String = ClientStatus.AKTIF.name,
    val goal: String = "",
    val startDateMillis: Long,
    val endDateMillis: Long,
    val totalSessionsQuota: Int = 4,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val recordedAt: Long,
    val waistCm: Double,
    val neckCm: Double? = null,
    val armCm: Double? = null,
    val weightKg: Double? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "body_compositions")
data class BodyCompositionEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val recordedAt: Long,
    val bodyFatPercent: Double,
    val muscleMassKg: Double,
    val bodyAgeYears: Int,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "progress_photos")
data class ProgressPhotoEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val angle: String,
    val takenAt: Long,
    val localOriginalPath: String,
    val previewBase64: String = "",
    val driveFileId: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "workout_exercises")
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val programDay: String = "Hari 1",
    val exerciseName: String,
    val sets: Int,
    val reps: String,
    val loadNote: String = "",
    val restSeconds: Int = 60,
    val coachNotes: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "workout_requests")
data class WorkoutRequestEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val clientName: String,
    val requestedExercise: String,
    val reason: String,
    val status: String = RequestStatus.MENUNGGU.name,
    val coachReply: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val clientName: String,
    val title: String = "Sesi Latihan",
    val startAtMillis: Long,
    val endAtMillis: Long,
    val location: String = "IHC Studio",
    val status: String = SessionStatus.TERJADWAL.name,
    val recurringGroupId: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "reschedule_requests")
data class RescheduleRequestEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val clientId: String,
    val clientName: String,
    val originalStartMillis: Long,
    val requestedStartMillis: Long,
    val requestedEndMillis: Long,
    val reason: String,
    val status: String = RequestStatus.MENUNGGU.name,
    val coachNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "client_task_schedules")
data class ClientTaskScheduleEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val taskType: String,
    val enabled: Boolean = true,
    val nextDueMillis: Long?,
    val lastCompletedMillis: Long? = null,
    val frequencyDays: Int = 7,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "meal_plan_notes")
data class MealPlanNoteEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val title: String,
    val note: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ChecklistFilter(val label: String) {
    PERLU_DIKERJAKAN("Perlu Dikerjakan"),
    SEMUA_JADWAL("Semua Jadwal"),
    SELESAI_HARI_INI("Selesai Hari Ini")
}

enum class CalendarViewMode(val label: String) {
    HARIAN("Harian"),
    MINGGUAN("Mingguan"),
    BULANAN("Bulanan")
}

data class CoachTaskUiItem(
    val schedule: ClientTaskScheduleEntity,
    val client: ClientEntity,
    val taskType: TaskType,
    val packageTier: PackageTier,
    val isCompletedToday: Boolean,
    val isDueOrOverdue: Boolean
)
