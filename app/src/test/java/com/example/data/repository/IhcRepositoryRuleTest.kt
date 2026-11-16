package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.IhcDatabase
import com.example.data.model.ClientStatus
import com.example.data.model.PackageTier
import com.example.data.model.WIB_ZONE
import com.example.data.remote.IhcFirestoreDataSource
import com.example.data.sync.IhcBackupManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class IhcRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var roomDb: IhcDatabase
    private lateinit var repo: IhcRepository
    private lateinit var firestoreSource: IhcFirestoreDataSource
    private lateinit var syncScope: CoroutineScope

    @Before
    override fun setUpFirebase() {
        super.setUpFirebase()
        val context = ApplicationProvider.getApplicationContext<Context>()
        roomDb = Room.inMemoryDatabaseBuilder(context, IhcDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = roomDb.ihcDao()
        firestoreSource = IhcFirestoreDataSource(firestore, auth)
        repo = IhcRepository(
            dao = dao,
            backupManager = IhcBackupManager(context, dao),
            firestoreSource = firestoreSource,
            awaitRemoteWrites = true
        )
        syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    override fun tearDownFirebase() {
        firestoreSource.stopSync()
        syncScope.cancel()
        roomDb.close()
        super.tearDownFirebase()
    }

    @Test
    fun saveClient_authenticatedCoach_writesClientInviteAndSchedulesToFirestore() = runBlocking {
        val coachUid = signInTestUser("coach_iyan_${UUID.randomUUID()}@ihc.id")
        val now = System.currentTimeMillis()
        val clientEmail = "klien_${UUID.randomUUID()}@example.com"

        val clientId = withTimeout(5000L) {
            repo.saveClient(
                existingId = null,
                fullName = "Andi Pratama",
                email = clientEmail,
                phone = "08123456789",
                packageTier = PackageTier.STANDARD,
                status = ClientStatus.AKTIF,
                goal = "Muscle Gain",
                startDateMillis = now,
                endDateMillis = now + 30 * 86_400_000L,
                totalSessionsQuota = 12,
                notes = "Tes Firestore"
            )
        }

        val clientDoc = withTimeout(5000L) {
            firestore.collection("clients").document(clientId).get().await()
        }
        assertTrue(clientDoc.exists())
        assertEquals(coachUid, clientDoc.getString("coachUid"))
        assertEquals("STANDARD", clientDoc.getString("packageTier"))

        val inviteDoc = withTimeout(5000L) {
            firestore.collection("clientInvites").document(clientEmail.lowercase()).get().await()
        }
        assertTrue(inviteDoc.exists())
        assertEquals(clientId, inviteDoc.getString("clientId"))
        assertEquals(true, inviteDoc.getBoolean("active"))

        val schedQuery = withTimeout(5000L) {
            firestore.collection("clientTaskSchedules")
                .whereEqualTo("coachUid", coachUid)
                .get()
                .await()
        }
        assertTrue(schedQuery.documents.any { it.getString("clientId") == clientId })
    }

    @Test
    fun startCloudSync_authenticatedCoach_mirrorsFirestoreClientsIntoRoomCache() = runBlocking {
        val coachUid = signInTestUser("coach_sync_${UUID.randomUUID()}@ihc.id")
        val clientId = "remote_client_${UUID.randomUUID()}"
        val clientEmail = "remote_${UUID.randomUUID()}@example.com"
        val now = System.currentTimeMillis()

        withTimeout(5000L) {
            firestore.collection("clients").document(clientId).set(
                mapOf(
                    "coachUid" to coachUid,
                    "fullName" to "Sinta Remote",
                    "emailNormalized" to clientEmail,
                    "phone" to "081999",
                    "packageTier" to "PREMIUM",
                    "status" to "AKTIF",
                    "goal" to "Recomp",
                    "startDateMillis" to now,
                    "endDateMillis" to now + 30 * 86_400_000L,
                    "totalSessionsQuota" to 24,
                    "notes" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        repo.startCloudSync(coachUid, syncScope) { err -> fail("Unexpected sync error: $err") }

        val synced = withTimeout(5000L) {
            repo.activeClients.first { list -> list.any { it.id == clientId } }
        }
        assertEquals("Sinta Remote", synced.first { it.id == clientId }.fullName)
    }

    @Test
    fun crossCoachAccess_otherCoachCannotReadClientDocument() = runBlocking {
        signInTestUser("coach_a_${UUID.randomUUID()}@ihc.id")
        val now = System.currentTimeMillis()
        val clientId = withTimeout(5000L) {
            repo.saveClient(
                existingId = null,
                fullName = "Private Client",
                email = "priv_${UUID.randomUUID()}@example.com",
                phone = "0811",
                packageTier = PackageTier.BASIC,
                status = ClientStatus.AKTIF,
                goal = "Fit",
                startDateMillis = now,
                endDateMillis = now + 30 * 86_400_000L,
                totalSessionsQuota = 4,
                notes = ""
            )
        }

        signInTestUser("coach_b_${UUID.randomUUID()}@ihc.id")
        try {
            withTimeout(5000L) {
                firestore.collection("clients").document(clientId).get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another coach's client")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun unauthenticatedSnapshot_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(3000L) {
                firestore.collection("clients").whereEqualTo("coachUid", "some_uid").snapshots().first()
            }
            fail("Expected PERMISSION_DENIED on unauthenticated snapshot listener")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun unauthenticatedDirectQuery_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(5000L) {
                firestore.collection("clients").whereEqualTo("coachUid", "any_uid").get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated client query")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun basicPackageClient_blockedFromRescheduleByRulesAndRepository() = runBlocking {
        val coachUid = signInTestUser("coach_basic_${UUID.randomUUID()}@ihc.id")
        val clientEmail = "basic_${UUID.randomUUID()}@example.com"
        val now = System.currentTimeMillis()
        val clientId = withTimeout(5000L) {
            repo.saveClient(
                existingId = null,
                fullName = "Budi Basic",
                email = clientEmail,
                phone = "0812",
                packageTier = PackageTier.BASIC,
                status = ClientStatus.AKTIF,
                goal = "Dasar",
                startDateMillis = now,
                endDateMillis = now + 30 * 86_400_000L,
                totalSessionsQuota = 4,
                notes = ""
            )
        }
        val client = repo.activeClients.first().first { it.id == clientId }
        val startMillis = LocalDate.now(WIB_ZONE).plusDays(2).atTime(LocalTime.of(9, 0)).atZone(WIB_ZONE).toInstant().toEpochMilli()
        val sessionRes = withTimeout(5000L) {
            repo.createSessionChecked(client, "Sesi Basic", startMillis, 60, "IHC Studio", "")
        }
        assertTrue(sessionRes is SessionSaveResult.Success)
        val session = repo.sessions.first().first { it.clientId == clientId }

        val coachReschedule = repo.coachRescheduleSession(
            session,
            startMillis + 86_400_000L,
            startMillis + 86_400_000L + 3_600_000L,
            "Pindah"
        )
        assertTrue(coachReschedule is SessionSaveResult.Conflict)

        // Client signs in and attempts to submit rescheduleRequest directly -> blocked by Firestore Rules!
        signInTestUser(clientEmail)
        try {
            withTimeout(5000L) {
                firestore.collection("rescheduleRequests").document("req_${UUID.randomUUID()}").set(
                    mapOf(
                        "coachUid" to coachUid,
                        "sessionId" to session.id,
                        "clientId" to clientId,
                        "clientEmail" to clientEmail,
                        "clientName" to "Budi Basic",
                        "originalStartMillis" to startMillis,
                        "requestedStartMillis" to (startMillis + 86_400_000L),
                        "requestedEndMillis" to (startMillis + 86_400_000L + 3_600_000L),
                        "reason" to "Mau pindah",
                        "status" to "MENUNGGU",
                        "coachNote" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            fail("Expected PERMISSION_DENIED when BASIC client submits rescheduleRequest")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }
}
