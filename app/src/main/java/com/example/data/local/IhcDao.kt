package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
import kotlinx.coroutines.flow.Flow

@Dao
interface IhcDao {

    // Package Rules
    @Query("SELECT * FROM package_rules")
    fun observePackageRules(): Flow<List<PackageRuleEntity>>

    @Query("SELECT * FROM package_rules")
    suspend fun getAllPackageRules(): List<PackageRuleEntity>

    @Query("SELECT * FROM package_rules WHERE tier = :tier LIMIT 1")
    suspend fun getPackageRule(tier: String): PackageRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackageRules(rules: List<PackageRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackageRule(rule: PackageRuleEntity)

    // Clients
    @Query("SELECT * FROM clients WHERE deletedAt IS NULL ORDER BY fullName ASC")
    fun observeActiveClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY updatedAt DESC")
    suspend fun getAllClientsIncludingDeleted(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :clientId LIMIT 1")
    suspend fun getClientById(clientId: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClient(client: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClients(clients: List<ClientEntity>)

    // Measurements
    @Query("SELECT * FROM measurements WHERE deletedAt IS NULL AND clientId = :clientId ORDER BY recordedAt DESC")
    fun observeMeasurementsForClient(clientId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY recordedAt DESC")
    suspend fun getAllMeasurementsIncludingDeleted(): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(item: MeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurements(items: List<MeasurementEntity>)

    // Body Composition
    @Query("SELECT * FROM body_compositions WHERE deletedAt IS NULL AND clientId = :clientId ORDER BY recordedAt DESC")
    fun observeBodyCompositionsForClient(clientId: String): Flow<List<BodyCompositionEntity>>

    @Query("SELECT * FROM body_compositions ORDER BY recordedAt DESC")
    suspend fun getAllBodyCompositionsIncludingDeleted(): List<BodyCompositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBodyComposition(item: BodyCompositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBodyCompositions(items: List<BodyCompositionEntity>)

    // Progress Photos
    @Query("SELECT * FROM progress_photos WHERE deletedAt IS NULL AND clientId = :clientId ORDER BY takenAt DESC")
    fun observePhotosForClient(clientId: String): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos ORDER BY takenAt DESC")
    suspend fun getAllPhotosIncludingDeleted(): List<ProgressPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(item: ProgressPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(items: List<ProgressPhotoEntity>)

    // Workout Exercises
    @Query("SELECT * FROM workout_exercises WHERE deletedAt IS NULL AND clientId = :clientId ORDER BY programDay ASC, sortOrder ASC")
    fun observeWorkoutsForClient(clientId: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises ORDER BY sortOrder ASC")
    suspend fun getAllWorkoutsIncludingDeleted(): List<WorkoutExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutExercise(item: WorkoutExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    // Workout Requests
    @Query("SELECT * FROM workout_requests ORDER BY createdAt DESC")
    fun observeWorkoutRequests(): Flow<List<WorkoutRequestEntity>>

    @Query("SELECT * FROM workout_requests")
    suspend fun getAllWorkoutRequests(): List<WorkoutRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutRequest(item: WorkoutRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutRequests(items: List<WorkoutRequestEntity>)

    // Sessions
    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startAtMillis ASC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startAtMillis ASC")
    suspend fun getAllSessionsIncludingDeleted(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL AND status != 'DIBATALKAN'")
    suspend fun getActiveNonCancelledSessions(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<SessionEntity>)

    // Reschedule Requests
    @Query("SELECT * FROM reschedule_requests ORDER BY createdAt DESC")
    fun observeRescheduleRequests(): Flow<List<RescheduleRequestEntity>>

    @Query("SELECT * FROM reschedule_requests")
    suspend fun getAllRescheduleRequests(): List<RescheduleRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRescheduleRequest(item: RescheduleRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRescheduleRequests(items: List<RescheduleRequestEntity>)

    // Client Task Schedules (Jadwal Input + Checklist Tugas Coach)
    @Query("SELECT * FROM client_task_schedules")
    fun observeAllTaskSchedules(): Flow<List<ClientTaskScheduleEntity>>

    @Query("SELECT * FROM client_task_schedules WHERE clientId = :clientId")
    fun observeTaskSchedulesForClient(clientId: String): Flow<List<ClientTaskScheduleEntity>>

    @Query("SELECT * FROM client_task_schedules WHERE clientId = :clientId")
    suspend fun getTaskSchedulesForClient(clientId: String): List<ClientTaskScheduleEntity>

    @Query("SELECT * FROM client_task_schedules")
    suspend fun getAllTaskSchedules(): List<ClientTaskScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTaskSchedule(item: ClientTaskScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTaskSchedules(items: List<ClientTaskScheduleEntity>)

    // Meal Plan Notes
    @Query("SELECT * FROM meal_plan_notes WHERE deletedAt IS NULL AND clientId = :clientId ORDER BY updatedAt DESC")
    fun observeMealPlanNotesForClient(clientId: String): Flow<List<MealPlanNoteEntity>>

    @Query("SELECT * FROM meal_plan_notes ORDER BY updatedAt DESC")
    suspend fun getAllMealPlanNotesIncludingDeleted(): List<MealPlanNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMealPlanNote(item: MealPlanNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMealPlanNotes(items: List<MealPlanNoteEntity>)

    // Activity Logs
    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT 150")
    fun observeActivityLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC")
    suspend fun getAllActivityLogs(): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLogs(logs: List<ActivityLogEntity>)
}
