package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object IhcAuthHelper {
    private const val TAG = "Auth"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        auth: FirebaseAuth,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        auth: FirebaseAuth,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit,
        scope: CoroutineScope
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Konfigurasi Google Sign-In belum lengkap (default_web_client_id tidak ditemukan).")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activity = context as? Activity
                    ?: return@launch onAuthError("Activity tidak tersedia untuk Google Sign-In.")
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Tipe kredensial Google tidak dikenali.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Gagal masuk dengan akun Google.")
            }
        }
    }

    /**
     * Automatically authorizes Google Drive appDataFolder scope using the signed-in Google account
     * via Google Play Services AuthorizationClient (silent if already granted, or returns IntentSender
     * for one-time consent).
     */
    fun authorizeGoogleDriveAppData(
        activity: Activity,
        onAccessTokenReady: (String) -> Unit,
        onResolutionRequired: (IntentSenderRequest) -> Unit,
        onError: (String) -> Unit
    ) {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(authRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    if (pendingIntent != null) {
                        onResolutionRequired(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        onError("Izin Google Drive memerlukan konfirmasi, tetapi dialog tidak tersedia.")
                    }
                } else {
                    val token = authorizationResult.accessToken
                    if (!token.isNullOrBlank()) {
                        onAccessTokenReady(token)
                    } else {
                        onError("Token akses Google Drive kosong.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Drive authorization failed: ${e.message}", e)
                onError("Otorisasi Google Drive gagal: ${e.localizedMessage ?: "Periksa koneksi atau Google Drive API"}")
            }
    }

    fun extractDriveTokenFromIntent(activity: Activity, data: Intent?): String? {
        return try {
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            result.accessToken?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed extracting Drive token from consent intent: ${e.message}", e)
            null
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        auth: FirebaseAuth,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
