package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IhcDatabase
import com.example.data.model.ClientStatus
import com.example.data.model.PackageTier
import com.example.data.model.TaskType
import com.example.data.model.WIB_ZONE
import com.example.data.repository.IhcRepository
import com.example.data.repository.SessionSaveResult
import com.example.data.sync.IhcBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IhcManagementTest {

    private lateinit var db: IhcDatabase
    private lateinit var repo: IhcRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IhcDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.ihcDao()
        repo = IhcRepository(dao, IhcBackupManager(context, dao))
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun packageAutomation_provisionsExactTasksForBasicVsPremium() = runBlocking {
        val now = System.currentTimeMillis()
        val end = now + 30 * 86_400_000L

        val basicId = repo.saveClient(
            existingId = null,
            fullName = "Budi Basic",
            email = "BUDI@IHC.ID",
            phone = "0811",
            packageTier = PackageTier.BASIC,
            status = ClientStatus.AKTIF,
            goal = "Turun BB",
            startDateMillis = now,
            endDateMillis = end,
            totalSessionsQuota = 4,
            notes = ""
        )
        val premiumId = repo.saveClient(
            existingId = null,
            fullName = "Putri Premium",
            email = "Putri@IHC.id",
            phone = "0812",
            packageTier = PackageTier.PREMIUM,
            status = ClientStatus.AKTIF,
            goal = "Body Recomp",
            startDateMillis = now,
            endDateMillis = end,
            totalSessionsQuota = 24,
            notes = ""
        )

        val clients = repo.activeClients.first()
        assertEquals("budi@ihc.id", clients.first { it.id == basicId }.emailNormalized)

        val basicSchedules = repo.observeTaskSchedulesForClient(basicId).first()
        val basicEnabled = basicSchedules.filter { it.enabled }.map { TaskType.fromString(it.taskType) }.toSet()
        assertEquals(setOf(TaskType.FOTO_PROGRES, TaskType.PENGUKURAN_LINGKAR), basicEnabled)

        val premiumSchedules = repo.observeTaskSchedulesForClient(premiumId).first()
        val premiumEnabled = premiumSchedules.filter { it.enabled }.map { TaskType.fromString(it.taskType) }.toSet()
        assertEquals(5, premiumEnabled.size)
        assertTrue(TaskType.KOMPOSISI_TUBUH in premiumEnabled)
        assertTrue(TaskType.MEAL_PLAN in premiumEnabled)
    }

    @Test
    fun sessionConflictAndBasicReschedule_enforcedCorrectly() = runBlocking {
        val now = System.currentTimeMillis()
        val end = now + 30 * 86_400_000L
        val basicId = repo.saveClient(
            null, "Budi", "budi@ihc.id", "", PackageTier.BASIC, ClientStatus.AKTIF, "", now, end, 4, ""
        )
        val stdId = repo.saveClient(
            null, "Sinta", "sinta@ihc.id", "", PackageTier.STANDARD, ClientStatus.AKTIF, "", now, end, 12, ""
        )
        val clients = repo.activeClients.first()
        val budi = clients.first { it.id == basicId }
        val sinta = clients.first { it.id == stdId }

        val startMillis = LocalDate.now(WIB_ZONE).plusDays(1).atTime(LocalTime.of(8, 0)).atZone(WIB_ZONE).toInstant().toEpochMilli()
        val firstRes = repo.createSessionChecked(budi, "Sesi Pagi", startMillis, 60, "Studio", "")
        assertTrue(firstRes is SessionSaveResult.Success)

        val conflictRes = repo.createSessionChecked(sinta, "Sesi Bentrok", startMillis + 15 * 60_000L, 60, "Studio", "")
        assertTrue(conflictRes is SessionSaveResult.Conflict)

        val sessions = repo.sessions.first()
        val budiSession = sessions.first { it.clientId == basicId }
        val basicReschedule = repo.coachRescheduleSession(
            budiSession,
            startMillis + 86_400_000L,
            startMillis + 86_400_000L + 3_600_000L,
            "Coba pindah"
        )
        assertTrue(basicReschedule is SessionSaveResult.Conflict)
    }

    @Test
    fun checklistCompletion_reschedulesAutomaticallyAndBackupMergesByTimestamp() = runBlocking {
        val now = System.currentTimeMillis()
        val stdId = repo.saveClient(
            null, "Andi", "andi@ihc.id", "", PackageTier.STANDARD, ClientStatus.AKTIF, "", now, now + 30 * 86_400_000L, 12, ""
        )
        val mealSched = repo.observeTaskSchedulesForClient(stdId).first()
            .first { it.taskType == TaskType.MEAL_PLAN.name }

        repo.markCoachTaskCompleted(mealSched, "Andi")
        val updatedMeal = repo.observeTaskSchedulesForClient(stdId).first()
            .first { it.taskType == TaskType.MEAL_PLAN.name }
        assertNotNull(updatedMeal.lastCompletedMillis)
        assertNotNull(updatedMeal.nextDueMillis)
        assertTrue(updatedMeal.nextDueMillis!! > updatedMeal.lastCompletedMillis!!)

        val exportedJson = repo.exportBackupJson()
        assertTrue(exportedJson.contains("andi@ihc.id"))

        val merged = repo.importBackupJson(exportedJson)
        // Same timestamps -> should NOT blindly overwrite local newer/equal records
        assertEquals(0, merged)
        assertFalse(repo.activeClients.first().isEmpty())
    }

    @Test
    fun progressPhotoDriveFileId_survivesBackupExportAndMerge() = runBlocking {
        val now = System.currentTimeMillis()
        val dao = db.ihcDao()
        val photo = com.example.data.model.ProgressPhotoEntity(
            id = "photo-1",
            clientId = "client-1",
            angle = "DEPAN",
            takenAt = now,
            localOriginalPath = "/tmp/orig_photo-1.jpg",
            previewBase64 = "ZmFrZV9wcmV2aWV3",
            driveFileId = "drive-file-999",
            notes = "Depan minggu 1",
            updatedAt = now
        )
        dao.upsertPhoto(photo)

        val json = repo.exportBackupJson()
        assertTrue(json.contains("drive-file-999"))

        // Simulate newer remote driveFileId arriving from another device
        val newerJson = json
            .replace("drive-file-999", "drive-file-1000")
            .replace("\"updatedAt\": $now", "\"updatedAt\": ${now + 5000}")
        val mergedCount = repo.importBackupJson(newerJson)
        assertTrue(mergedCount > 0)
        val restored = dao.getAllPhotosIncludingDeleted().first { it.id == "photo-1" }
        assertEquals("drive-file-1000", restored.driveFileId)
    }
}
