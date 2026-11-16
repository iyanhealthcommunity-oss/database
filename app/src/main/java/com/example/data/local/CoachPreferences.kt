package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.coachDataStore: DataStore<Preferences> by preferencesDataStore(name = "ihc_coach_prefs")

data class CoachSessionState(
    val coachUid: String = "",
    val coachName: String = "Coach Iyan",
    val coachEmail: String = "iyanhealthcommunity@gmail.com",
    val autoSyncDrive: Boolean = true,
    val lastSyncMillis: Long? = null,
    val lastSyncMessage: String = "Cloud Firestore aktif • Siap sinkron Google Drive (appDataFolder)"
)

class CoachPreferences(private val context: Context) {

    private object Keys {
        val COACH_NAME = stringPreferencesKey("coach_name")
        val COACH_EMAIL = stringPreferencesKey("coach_email")
        val AUTO_SYNC_DRIVE = booleanPreferencesKey("auto_sync_drive")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
    }

    val sessionFlow: Flow<CoachSessionState> = context.coachDataStore.data.map { prefs ->
        CoachSessionState(
            coachName = prefs[Keys.COACH_NAME] ?: "Coach Iyan",
            coachEmail = prefs[Keys.COACH_EMAIL] ?: "iyanhealthcommunity@gmail.com",
            autoSyncDrive = prefs[Keys.AUTO_SYNC_DRIVE] ?: true,
            lastSyncMillis = prefs[Keys.LAST_SYNC_MILLIS],
            lastSyncMessage = prefs[Keys.LAST_SYNC_MESSAGE] ?: "Cloud Firestore aktif • Siap sinkron Google Drive (appDataFolder)"
        )
    }

    suspend fun updateCoachIdentity(email: String?, name: String?) {
        context.coachDataStore.edit { prefs ->
            if (!email.isNullOrBlank()) prefs[Keys.COACH_EMAIL] = email.trim().lowercase()
            if (!name.isNullOrBlank()) prefs[Keys.COACH_NAME] = name.trim()
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.coachDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_DRIVE] = enabled
        }
    }

    suspend fun updateSyncStatus(timestampMillis: Long?, message: String) {
        context.coachDataStore.edit { prefs ->
            if (timestampMillis != null) prefs[Keys.LAST_SYNC_MILLIS] = timestampMillis
            prefs[Keys.LAST_SYNC_MESSAGE] = message
        }
    }
}
