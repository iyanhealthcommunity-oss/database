package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.auth.IhcAuthHelper
import com.example.data.local.CoachPreferences
import com.example.data.local.CoachSessionState
import com.example.data.local.IhcDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.BodyCompositionEntity
import com.example.data.model.ChecklistFilter
import com.example.data.model.ClientEntity
import com.example.data.model.ClientStatus
import com.example.data.model.ClientTaskScheduleEntity
import com.example.data.model.CoachTaskUiItem
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
import com.example.data.model.todayEndWib
import com.example.data.model.todayStartWib
import com.example.data.remote.IhcFirestoreDataSource
import com.example.data.repository.IhcRepository
import com.example.data.repository.SessionSaveResult
import com.example.data.sync.IhcBackupManager
import com.example.data.sync.SyncOutcome
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class IhcViewModel(
    private val repository: IhcRepository,
    private val preferences: CoachPreferences,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _activeCoachUid = MutableStateFlow(auth.currentUser?.uid.orEmpty())
    private val _sessionDriveToken = MutableStateFlow<String?>(null)

    val sessionState: StateFlow<CoachSessionState> = combine(
        preferences.sessionFlow,
        _activeCoachUid
    ) { prefsState, uid ->
        val user = auth.currentUser
        prefsState.copy(
            coachUid = uid,
            coachEmail = user?.email?.lowercase() ?: prefsState.coachEmail,
            coachName = user?.displayName?.takeIf { it.isNotBlank() } ?: prefsState.coachName
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CoachSessionState())

    private val _isSplashVisible = MutableStateFlow(true)
    val isSplashVisible: StateFlow<Boolean> = _isSplashVisible.asStateFlow()

    private val _isExiting = MutableStateFlow(false)
    val isExiting: StateFlow<Boolean> = _isExiting.asStateFlow()

    private val _isLoadingOperation = MutableStateFlow(false)
    val isLoadingOperation: StateFlow<Boolean> = _isLoadingOperation.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Memproses data IHC…")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _checklistFilter = MutableStateFlow(ChecklistFilter.PERLU_DIKERJAKAN)
    val checklistFilter: StateFlow<ChecklistFilter> = _checklistFilter.asStateFlow()

    val packageRules: StateFlow<List<PackageRuleEntity>> = repository.packageRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PackageRuleEntity.defaults())

    val clients: StateFlow<List<ClientEntity>> = repository.activeClients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionEntity>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rescheduleRequests: StateFlow<List<RescheduleRequestEntity>> = repository.rescheduleRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workoutRequests: StateFlow<List<WorkoutRequestEntity>> = repository.workoutRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs: StateFlow<List<ActivityLogEntity>> = repository.activityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coachChecklistTasks: StateFlow<List<CoachTaskUiItem>> = combine(
        repository.allTaskSchedules,
        repository.activeClients,
        _checklistFilter
    ) { schedules, clientList, filter ->
        val clientMap = clientList.associateBy { it.id }
        val startToday = todayStartWib()
        val endToday = todayEndWib()

        val allItems = schedules.mapNotNull { schedule ->
            if (!schedule.enabled) return@mapNotNull null
            val client = clientMap[schedule.clientId] ?: return@mapNotNull null
            if (ClientStatus.fromString(client.status) != ClientStatus.AKTIF) return@mapNotNull null

            val tier = PackageTier.fromString(client.packageTier)
            val taskType = TaskType.fromString(schedule.taskType)
            val completedToday = schedule.lastCompletedMillis?.let { it in startToday..endToday } ?: false
            val dueOrOverdue = schedule.nextDueMillis?.let { it <= endToday } ?: false

            CoachTaskUiItem(
                schedule = schedule,
                client = client,
                taskType = taskType,
                packageTier = tier,
                isCompletedToday = completedToday,
                isDueOrOverdue = dueOrOverdue
            )
        }

        val filtered = when (filter) {
            ChecklistFilter.PERLU_DIKERJAKAN -> allItems.filter { it.isDueOrOverdue && !it.isCompletedToday }
            ChecklistFilter.SEMUA_JADWAL -> allItems
            ChecklistFilter.SELESAI_HARI_INI -> allItems.filter { it.isCompletedToday }
        }

        filtered.sortedWith(
            compareBy<CoachTaskUiItem> { it.packageTier.sortPriority }
                .thenBy { it.schedule.nextDueMillis ?: Long.MAX_VALUE }
                .thenBy { it.client.fullName.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        repository.onCloudWriteError = { msg ->
            _bannerMessage.value = msg
        }
        viewModelScope.launch {
            repository.ensureDefaultPackageRules()
            delay(650L)
            _isSplashVisible.value = false
        }
    }

    override fun onCleared() {
        repository.stopCloudSync()
        super.onCleared()
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(message: String) {
        _bannerMessage.value = message
    }

    fun setChecklistFilter(filter: ChecklistFilter) {
        _checklistFilter.value = filter
    }

    fun onCoachAuthenticated(uid: String, email: String?, name: String?) {
        val prevUid = _activeCoachUid.value
        _activeCoachUid.value = uid
        viewModelScope.launch {
            preferences.updateCoachIdentity(email, name)
            repository.ensureDefaultPackageRules()
            repository.startCloudSync(uid, viewModelScope) { errMsg ->
                _bannerMessage.value = errMsg
            }
            if (prevUid != uid && !email.isNullOrBlank()) {
                repository.logActivity("LOGIN_COACH", "Auth", uid, "Coach masuk via Google ($email)")
            }
        }
    }

    fun onCoachUnauthenticated() {
        _activeCoachUid.value = ""
        _sessionDriveToken.value = null
        repository.stopCloudSync()
    }

    fun signOutCoach(credentialManager: CredentialManager) {
        viewModelScope.launch {
            _isExiting.value = true
            val email = sessionState.value.coachEmail
            repository.logActivity("LOGOUT_COACH", "Auth", _activeCoachUid.value.ifBlank { email }, "Coach keluar dari aplikasi")
            delay(650L)
            repository.stopCloudSync()
            _sessionDriveToken.value = null
            _activeCoachUid.value = ""
            IhcAuthHelper.signOut(credentialManager, auth, viewModelScope) {
                _isExiting.value = false
            }
        }
    }

    fun observeMeasurements(clientId: String): Flow<List<MeasurementEntity>> =
        repository.observeMeasurements(clientId)

    fun observeBodyCompositions(clientId: String): Flow<List<BodyCompositionEntity>> =
        repository.observeBodyCompositions(clientId)

    fun observePhotos(clientId: String): Flow<List<ProgressPhotoEntity>> =
        repository.observePhotos(clientId)

    fun observeWorkouts(clientId: String): Flow<List<WorkoutExerciseEntity>> =
        repository.observeWorkouts(clientId)

    fun observeMealPlanNotes(clientId: String): Flow<List<MealPlanNoteEntity>> =
        repository.observeMealPlanNotes(clientId)

    fun observeTaskSchedulesForClient(clientId: String): Flow<List<ClientTaskScheduleEntity>> =
        repository.observeTaskSchedulesForClient(clientId)

    fun saveClient(
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
        notes: String,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runWithLoading("Menyimpan data & fasilitas paket klien…") {
                val id = repository.saveClient(
                    existingId, fullName, email, phone, packageTier, status,
                    goal, startDateMillis, endDateMillis, totalSessionsQuota, notes
                )
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Data klien $fullName (${packageTier.label}) berhasil disimpan."
                onSaved(id)
            }
        }
    }

    fun softDeleteClient(client: ClientEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runWithLoading("Mengarsipkan data klien…") {
                repository.softDeleteClient(client)
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "${client.fullName} dipindahkan ke arsip (soft delete)."
                onDone()
            }
        }
    }

    fun addMeasurement(
        clientId: String,
        waistCm: Double,
        neckCm: Double?,
        armCm: Double?,
        weightKg: Double?,
        notes: String
    ) {
        viewModelScope.launch {
            runWithLoading("Menyimpan pengukuran lingkar…") {
                repository.addMeasurement(clientId, waistCm, neckCm, armCm, weightKg, notes)
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Pengukuran lingkar berhasil dicatat."
            }
        }
    }

    fun addBodyComposition(
        clientId: String,
        bodyFatPercent: Double,
        muscleMassKg: Double,
        bodyAgeYears: Int,
        notes: String
    ) {
        viewModelScope.launch {
            runWithLoading("Menyimpan komposisi tubuh…") {
                repository.addBodyComposition(clientId, bodyFatPercent, muscleMassKg, bodyAgeYears, notes)
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Komposisi tubuh berhasil dicatat."
            }
        }
    }

    fun addProgressPhoto(clientId: String, angle: PhotoAngle, uri: Uri, notes: String) {
        viewModelScope.launch {
            runWithLoading("Mengompresi pratinjau & menyimpan foto asli…") {
                repository.addProgressPhoto(clientId, angle, uri, notes)
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Foto progres (${angle.label}) berhasil disimpan."
            }
        }
    }

    fun addWorkoutExercise(
        clientId: String,
        programDay: String,
        exerciseName: String,
        sets: Int,
        reps: String,
        loadNote: String,
        restSeconds: Int,
        coachNotes: String
    ) {
        viewModelScope.launch {
            runWithLoading("Menyimpan gerakan latihan…") {
                repository.addWorkoutExercise(
                    clientId, programDay, exerciseName, sets, reps, loadNote, restSeconds, coachNotes
                )
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Gerakan $exerciseName ditambahkan ke program."
            }
        }
    }

    fun deleteWorkoutExercise(item: WorkoutExerciseEntity) {
        viewModelScope.launch {
            repository.softDeleteWorkoutExercise(item)
            triggerAutoSyncIfEnabled()
        }
    }

    fun addMealPlanNote(clientId: String, title: String, note: String) {
        viewModelScope.launch {
            runWithLoading("Menyimpan catatan Meal Plan…") {
                repository.addMealPlanNote(clientId, title, note)
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Catatan update Meal Plan disimpan."
            }
        }
    }

    fun updateTaskSchedule(schedule: ClientTaskScheduleEntity, nextDueMillis: Long?, notes: String) {
        viewModelScope.launch {
            repository.updateClientTaskSchedule(schedule, nextDueMillis, notes)
            triggerAutoSyncIfEnabled()
            _bannerMessage.value = "Target jadwal input diperbarui."
        }
    }

    fun markCoachTaskCompleted(item: CoachTaskUiItem) {
        viewModelScope.launch {
            repository.markCoachTaskCompleted(item.schedule, item.client.fullName)
            triggerAutoSyncIfEnabled()
            _bannerMessage.value = "${item.taskType.label} (${item.client.fullName}) selesai. Jadwal berikutnya diatur otomatis."
        }
    }

    fun createSession(
        client: ClientEntity,
        title: String,
        dateWib: LocalDate,
        timeWib: LocalTime,
        durationMinutes: Int,
        location: String,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val startMillis = dateWib.atTime(timeWib).atZone(WIB_ZONE).toInstant().toEpochMilli()
            when (val res = repository.createSessionChecked(client, title, startMillis, durationMinutes, location, notes)) {
                is SessionSaveResult.Conflict -> _bannerMessage.value = res.message
                is SessionSaveResult.Success -> {
                    triggerAutoSyncIfEnabled()
                    _bannerMessage.value = res.warning ?: "Sesi ${client.fullName} berhasil dijadwalkan (WIB)."
                    onSuccess()
                }
            }
        }
    }

    fun createRecurringSessions(
        client: ClientEntity,
        title: String,
        firstDateWib: LocalDate,
        timeWib: LocalTime,
        durationMinutes: Int,
        weeksCount: Int,
        location: String,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runWithLoading("Membuat jadwal rutin & memeriksa bentrok…") {
                val (created, warnings) = repository.createRecurringSessions(
                    client, title, firstDateWib, timeWib, durationMinutes, weeksCount, location, notes
                )
                triggerAutoSyncIfEnabled()
                val note = if (warnings.isNotEmpty()) "\n" + warnings.joinToString("\n") else ""
                _bannerMessage.value = "$created sesi rutin berhasil dibuat.$note"
                onSuccess()
            }
        }
    }

    fun updateSessionStatus(session: SessionEntity, status: SessionStatus) {
        viewModelScope.launch {
            repository.updateSessionStatus(session, status)
            triggerAutoSyncIfEnabled()
            _bannerMessage.value = "Kehadiran ${session.clientName}: ${status.label}."
        }
    }

    fun coachRescheduleSession(
        session: SessionEntity,
        newDateWib: LocalDate,
        newTimeWib: LocalTime,
        durationMinutes: Int,
        reason: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val newStart = newDateWib.atTime(newTimeWib).atZone(WIB_ZONE).toInstant().toEpochMilli()
            val newEnd = newStart + durationMinutes.coerceAtLeast(15) * 60_000L
            when (val res = repository.coachRescheduleSession(session, newStart, newEnd, reason)) {
                is SessionSaveResult.Conflict -> _bannerMessage.value = res.message
                is SessionSaveResult.Success -> {
                    triggerAutoSyncIfEnabled()
                    _bannerMessage.value = "Sesi berhasil di-reschedule."
                    onSuccess()
                }
            }
        }
    }

    fun handleRescheduleRequest(request: RescheduleRequestEntity, approve: Boolean, coachNote: String) {
        viewModelScope.launch {
            val error = repository.handleRescheduleRequest(request, approve, coachNote)
            if (error != null) {
                _bannerMessage.value = error
            } else {
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = if (approve) "Permintaan reschedule disetujui." else "Permintaan reschedule ditolak."
            }
        }
    }

    fun replyWorkoutRequest(request: WorkoutRequestEntity, status: RequestStatus, reply: String) {
        viewModelScope.launch {
            repository.replyWorkoutRequest(request, status, reply)
            triggerAutoSyncIfEnabled()
            _bannerMessage.value = "Request gerakan '${request.requestedExercise}' diperbarui."
        }
    }

    fun savePackageRule(rule: PackageRuleEntity) {
        viewModelScope.launch {
            runWithLoading("Menyesuaikan fasilitas paket ${rule.tier}…") {
                repository.savePackageRule(rule)
                triggerAutoSyncIfEnabled()
                _bannerMessage.value = "Fasilitas paket ${rule.tier} disimpan & disinkronkan ke klien terkait."
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoSync(enabled) }
    }

    fun onDriveAccessTokenGranted(token: String, showBanner: Boolean = true) {
        _sessionDriveToken.value = token
        syncGoogleDriveWithToken(token, showBanner = showBanner)
    }

    fun onDriveAuthorizationError(reason: String, showBanner: Boolean = true) {
        viewModelScope.launch {
            preferences.updateSyncStatus(null, reason)
            if (showBanner) _bannerMessage.value = reason
        }
    }

    fun syncGoogleDriveWithToken(accessToken: String, showBanner: Boolean = true) {
        _sessionDriveToken.value = accessToken
        viewModelScope.launch {
            if (showBanner) {
                runWithLoading("Menyinkronkan data & foto asli ke Google Drive (appDataFolder)…") {
                    performDriveSync(accessToken, showBanner = true)
                }
            } else {
                performDriveSync(accessToken, showBanner = false)
            }
        }
    }

    private suspend fun performDriveSync(accessToken: String, showBanner: Boolean) {
        when (val outcome = repository.syncGoogleDrive(accessToken)) {
            is SyncOutcome.Success -> {
                preferences.updateSyncStatus(outcome.syncedAtMillis, outcome.message)
                if (showBanner) _bannerMessage.value = outcome.message
            }
            is SyncOutcome.Error -> {
                if (outcome.reason.contains("401") || outcome.reason.contains("kedaluwarsa", ignoreCase = true)) {
                    _sessionDriveToken.value = null
                }
                preferences.updateSyncStatus(null, outcome.reason)
                if (showBanner) _bannerMessage.value = outcome.reason
            }
        }
    }

    private suspend fun triggerAutoSyncIfEnabled() {
        val state = sessionState.value
        val token = _sessionDriveToken.value
        if (state.autoSyncDrive && !token.isNullOrBlank()) {
            performDriveSync(token, showBanner = false)
        }
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun restoreBackupJson(json: String) {
        viewModelScope.launch {
            runWithLoading("Memulihkan cadangan JSON (cek timestamp terbaru)…") {
                try {
                    val count = repository.importBackupJson(json)
                    _bannerMessage.value = "Pemulihan selesai: $count item diperbarui berdasarkan waktu ubah terbaru."
                } catch (e: Exception) {
                    _bannerMessage.value = "Gagal membaca file backup JSON: ${e.localizedMessage}"
                }
            }
        }
    }

    private suspend fun runWithLoading(message: String, block: suspend () -> Unit) {
        _loadingMessage.value = message
        _isLoadingOperation.value = true
        try {
            block()
        } finally {
            _isLoadingOperation.value = false
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = IhcDatabase.getInstance(application)
            val dao = db.ihcDao()
            val backupManager = IhcBackupManager(application, dao)
            val auth = FirebaseAuth.getInstance()
            val databaseId = application.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            val firestoreSource = IhcFirestoreDataSource(firestore, auth)
            val repo = IhcRepository(dao, backupManager, firestoreSource)
            val prefs = CoachPreferences(application)
            return IhcViewModel(repo, prefs, auth) as T
        }
    }
}
