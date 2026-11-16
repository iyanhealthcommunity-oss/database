package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.IhcAuthHelper
import com.example.data.remote.authStateFlow
import com.example.ui.components.IhcExitScreen
import com.example.ui.components.IhcLogoImage
import com.example.ui.components.IhcPulsingLogoLoading
import com.example.ui.components.IhcSplashScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ClientProfileScreen
import com.example.ui.screens.ClientsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RescheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.IhcTheme
import com.example.ui.viewmodel.IhcViewModel
import com.google.firebase.auth.FirebaseAuth

private enum class IhcTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Beranda", Icons.Filled.Home, Icons.Outlined.Home, "nav_dashboard"),
    CLIENTS("Klien", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_clients"),
    CALENDAR("Kalender", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
    REQUESTS("Permintaan", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz, "nav_requests"),
    SETTINGS("Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IhcTheme {
                IhcRootApp()
            }
        }
    }
}

@Composable
fun IhcRootApp() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val viewModel: IhcViewModel = viewModel(factory = IhcViewModel.Factory(app))

    val currentUser by remember(auth) { auth.authStateFlow() }.collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val isSplash by viewModel.isSplashVisible.collectAsStateWithLifecycle()
    val isExiting by viewModel.isExiting.collectAsStateWithLifecycle()
    val isLoadingOp by viewModel.isLoadingOperation.collectAsStateWithLifecycle()
    val loadingMessage by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var hasAttemptedAutoSignIn by remember { mutableStateOf(false) }

    LaunchedEffect(isSplash) {
        if (!isSplash && !hasAttemptedAutoSignIn && auth.currentUser == null) {
            hasAttemptedAutoSignIn = true
            IhcAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                auth = auth,
                onAuthSuccess = { authError = null },
                onUnauthenticated = { /* Stay on LoginScreen */ },
                scope = scope
            )
        }
    }

    LaunchedEffect(currentUser?.uid) {
        val user = currentUser
        if (user != null) {
            isSigningIn = false
            authError = null
            viewModel.onCoachAuthenticated(user.uid, user.email, user.displayName)
        } else {
            viewModel.onCoachUnauthenticated()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val signedInUser = currentUser
        when {
            isSplash -> IhcSplashScreen()
            isExiting -> IhcExitScreen()
            signedInUser == null -> LoginScreen(
                isSigningIn = isSigningIn,
                authError = authError,
                onGoogleSignInClick = {
                    isSigningIn = true
                    authError = null
                    IhcAuthHelper.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        auth = auth,
                        onAuthSuccess = { isSigningIn = false },
                        onAuthError = { err ->
                            isSigningIn = false
                            authError = err
                        },
                        onAuthCancelled = { isSigningIn = false },
                        scope = scope
                    )
                }
            )
            else -> key(signedInUser.uid) {
                IhcMainWorkspace(
                    coachUid = signedInUser.uid,
                    credentialManager = credentialManager,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState
                )
            }
        }

        if (isLoadingOp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    IhcPulsingLogoLoading(message = loadingMessage)
                }
            }
        }
    }
}

@Composable
private fun IhcMainWorkspace(
    coachUid: String,
    credentialManager: CredentialManager,
    viewModel: IhcViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var pendingDriveSyncShowBanner by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val act = activity ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val token = IhcAuthHelper.extractDriveTokenFromIntent(act, result.data)
            if (!token.isNullOrBlank()) {
                viewModel.onDriveAccessTokenGranted(token, showBanner = pendingDriveSyncShowBanner)
            } else {
                viewModel.onDriveAuthorizationError("Izin Google Drive tidak mengembalikan token akses.", showBanner = pendingDriveSyncShowBanner)
            }
        } else {
            viewModel.onDriveAuthorizationError("Izin akses Google Drive (appDataFolder) dibatalkan.", showBanner = pendingDriveSyncShowBanner)
        }
    }

    fun requestDriveAuthorization(showBanner: Boolean) {
        val act = activity ?: return
        pendingDriveSyncShowBanner = showBanner
        IhcAuthHelper.authorizeGoogleDriveAppData(
            activity = act,
            onAccessTokenReady = { token ->
                viewModel.onDriveAccessTokenGranted(token, showBanner = showBanner)
            },
            onResolutionRequired = { intentSenderRequest ->
                driveConsentLauncher.launch(intentSenderRequest)
            },
            onError = { err ->
                viewModel.onDriveAuthorizationError(err, showBanner = showBanner)
            }
        )
    }

    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    var autoDriveAttemptedForUid by remember(coachUid) { mutableStateOf(false) }
    LaunchedEffect(coachUid, sessionState.autoSyncDrive) {
        if (sessionState.autoSyncDrive && !autoDriveAttemptedForUid) {
            autoDriveAttemptedForUid = true
            requestDriveAuthorization(showBanner = false)
        }
    }
    val packageRules by viewModel.packageRules.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val rescheduleRequests by viewModel.rescheduleRequests.collectAsStateWithLifecycle()
    val workoutRequests by viewModel.workoutRequests.collectAsStateWithLifecycle()
    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val checklistTasks by viewModel.coachChecklistTasks.collectAsStateWithLifecycle()
    val checklistFilter by viewModel.checklistFilter.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(IhcTab.DASHBOARD) }
    var activeClientId by remember { mutableStateOf<String?>(null) }
    var activeClientTabIndex by remember { mutableIntStateOf(0) }

    if (activeClientId != null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ClientProfileScreen(
                    clientId = activeClientId!!,
                    initialTabIndex = activeClientTabIndex,
                    viewModel = viewModel,
                    onBack = { activeClientId = null }
                )
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        IhcTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        header = {
                            Spacer(Modifier.height(10.dp))
                            IhcLogoImage(size = 44.dp, cornerRadius = 12.dp)
                            Spacer(Modifier.height(10.dp))
                        }
                    ) {
                        IhcTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        IhcTab.DASHBOARD -> DashboardScreen(
                            coachName = sessionState.coachName,
                            clients = clients,
                            sessions = sessions,
                            rescheduleRequests = rescheduleRequests,
                            workoutRequests = workoutRequests,
                            checklistTasks = checklistTasks,
                            checklistFilter = checklistFilter,
                            onSelectChecklistFilter = viewModel::setChecklistFilter,
                            onToggleTaskCompleted = viewModel::markCoachTaskCompleted,
                            onOpenClientTab = { id, tabIdx ->
                                activeClientTabIndex = tabIdx
                                activeClientId = id
                            },
                            onNavigateToClients = { currentTab = IhcTab.CLIENTS },
                            onNavigateToCalendar = { currentTab = IhcTab.CALENDAR },
                            onNavigateToRequests = { currentTab = IhcTab.REQUESTS }
                        )
                        IhcTab.CLIENTS -> ClientsScreen(
                            clients = clients,
                            sessions = sessions,
                            packageRules = packageRules,
                            onClientClick = { id ->
                                activeClientTabIndex = 0
                                activeClientId = id
                            },
                            onSaveClient = { exId, name, email, phone, tier, status, goal, start, end, quota, notes ->
                                viewModel.saveClient(exId, name, email, phone, tier, status, goal, start, end, quota, notes)
                            }
                        )
                        IhcTab.CALENDAR -> CalendarScreen(
                            clients = clients,
                            sessions = sessions,
                            packageRules = packageRules,
                            onCreateSession = { client, title, d, t, dur, loc, note ->
                                viewModel.createSession(client, title, d, t, dur, loc, note)
                            },
                            onCreateRecurring = { client, title, d, t, dur, weeks, loc, note ->
                                viewModel.createRecurringSessions(client, title, d, t, dur, weeks, loc, note)
                            },
                            onUpdateSessionStatus = viewModel::updateSessionStatus,
                            onCoachReschedule = { session, d, t, dur, reason ->
                                viewModel.coachRescheduleSession(session, d, t, dur, reason)
                            }
                        )
                        IhcTab.REQUESTS -> RescheduleScreen(
                            rescheduleRequests = rescheduleRequests,
                            workoutRequests = workoutRequests,
                            activityLogs = activityLogs,
                            onHandleReschedule = viewModel::handleRescheduleRequest,
                            onReplyWorkoutRequest = viewModel::replyWorkoutRequest
                        )
                        IhcTab.SETTINGS -> SettingsScreen(
                            sessionState = sessionState,
                            packageRules = packageRules,
                            onSavePackageRule = viewModel::savePackageRule,
                            onSetAutoSync = viewModel::setAutoSync,
                            onSyncDriveNow = { requestDriveAuthorization(showBanner = true) },
                            onExportJsonString = viewModel::exportBackupJson,
                            onRestoreJsonString = viewModel::restoreBackupJson,
                            onNotify = viewModel::showBanner,
                            onSignOut = { viewModel.signOutCoach(credentialManager) }
                        )
                    }
                }
            }
        }
    }
}
