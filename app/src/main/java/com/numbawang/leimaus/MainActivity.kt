package com.numbawang.leimaus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.numbawang.leimaus.alarm.NotificationHelper
import com.numbawang.leimaus.data.update.ApkUpdateInstaller
import com.numbawang.leimaus.ui.MainViewModel
import com.numbawang.leimaus.ui.screens.AchievementsScreen
import com.numbawang.leimaus.ui.screens.HistoryScreen
import com.numbawang.leimaus.ui.screens.HomeScreen
import com.numbawang.leimaus.ui.screens.SettingsScreen
import com.numbawang.leimaus.ui.components.AchievementUnlockedBanner
import com.numbawang.leimaus.ui.components.QuickPunchBottomBar
import com.numbawang.leimaus.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var requestedTab by mutableIntStateOf(-1)

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("approval_month", viewModel.approval.state.value.month)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleUpdateInstallStatusIntent(intent)) return
        if (handleApprovalIntent(intent)) return
        handleOpenUpdateIntent(intent)
        handleSharedLocationIntent(intent)
        handlePunchIntent(intent)
    }

    private fun handleOpenUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_UPDATE, false) == true) {
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_UPDATE)
            requestedTab = 1
        }
    }

    private fun handleApprovalIntent(intent: Intent?): Boolean {
        val month = intent?.getStringExtra(NotificationHelper.EXTRA_APPROVAL_MONTH) ?: return false
        // Consume both extras defensively: opening this view can never trigger a punch or approval.
        intent.removeExtra(NotificationHelper.EXTRA_APPROVAL_MONTH)
        intent.removeExtra(NotificationHelper.EXTRA_PUNCH_ACTION)
        viewModel.approval.open(month)
        requestedTab = 4
        return true
    }

    /** Handles the sanitized failure callback forwarded by the private install receiver. */
    private fun handleUpdateInstallStatusIntent(intent: Intent?): Boolean {
        if (intent?.action != ApkUpdateInstaller.ACTION_INSTALL_STATUS) return false

        when (val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )) {
            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> {
                val systemMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val reason = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED ->
                        "Päivityksen asennus peruttiin."
                    PackageInstaller.STATUS_FAILURE_BLOCKED ->
                        "Android esti päivityksen asentamisen."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Päivityksen paketti tai allekirjoitus ei vastaa asennettua sovellusta."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "Päivitys ei ole yhteensopiva tämän laitteen kanssa."
                    PackageInstaller.STATUS_FAILURE_STORAGE ->
                        "Laitteessa ei ole riittävästi tallennustilaa päivitykselle."
                    else -> "Päivityksen asennus epäonnistui."
                }
                viewModel.onUpdateInstallFailed(
                    if (systemMessage.isNullOrBlank()) reason else "$reason $systemMessage",
                    isError = status != PackageInstaller.STATUS_FAILURE_ABORTED,
                )
            }
        }

        intent.action = null
        return true
    }

    /**
     * Tapping the body of a reminder notification should punch, not just open the app. The extra
     * is consumed so a configuration change does not replay the punch.
     */
    private fun handlePunchIntent(intent: android.content.Intent?) {
        val punchAction = intent?.getStringExtra(NotificationHelper.EXTRA_PUNCH_ACTION) ?: return
        intent.removeExtra(NotificationHelper.EXTRA_PUNCH_ACTION)
        viewModel.punchFromNotification(punchAction)
    }

    private fun handleSharedLocationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (android.content.Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    viewModel.processSharedLocationText(this, sharedText)
                }
            }
        } else if (android.content.Intent.ACTION_VIEW == action) {
            val data = intent.dataString
            if (!data.isNullOrBlank()) {
                viewModel.processSharedLocationText(this, data)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val handledUpdateStatus =
            savedInstanceState == null && handleUpdateInstallStatusIntent(intent)
        if (!handledUpdateStatus && !handleApprovalIntent(intent)) {
            handleOpenUpdateIntent(intent)
            handleSharedLocationIntent(intent)
            if (savedInstanceState == null) handlePunchIntent(intent)
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            MyApplicationTheme(appTheme = settings.appTheme) {
                val context = LocalContext.current
                var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(requestedTab) {
                    if (requestedTab >= 0) {
                        selectedTab = requestedTab
                        requestedTab = -1
                    }
                }

                val uiMessage by viewModel.uiMessage.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                // Request Notification and Location permissions
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { _ -> }
                )

                LaunchedEffect(Unit) {
                    val permissionsNeeded = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    if (permissionsNeeded.isNotEmpty()) {
                        permissionLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                }

                LaunchedEffect(uiMessage) {
                    uiMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg.text)
                        viewModel.clearUiMessage()
                    }
                }

                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        viewModel.clearToastMessage()
                    }
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Numbawang",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            navigationIcon = {
                                if (selectedTab != 0) {
                                    IconButton(onClick = { selectedTab = 0 }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Takaisin"
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            actions = {
                                IconButton(onClick = { selectedTab = 3 }) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "Saavutukset"
                                    )
                                }
                                IconButton(onClick = { selectedTab = 2 }) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Historia"
                                    )
                                }
                                IconButton(onClick = { selectedTab = 1 }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Asetukset"
                                    )
                                }
                                IconButton(onClick = { viewModel.triggerTestMorningNotification() }) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Testaa ilmoitusta",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (selectedTab != 4) QuickPunchBottomBar(viewModel = viewModel)
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { selectedTab = 1 },
                                onNavigateToApproval = {
                                    viewModel.approval.open()
                                    selectedTab = 4
                                }
                            )
                            1 -> SettingsScreen(
                                viewModel = viewModel
                            )
                            2 -> HistoryScreen(
                                viewModel = viewModel
                            )
                            3 -> AchievementsScreen(
                                viewModel = viewModel
                            )
                            4 -> {
                                val approvalState by viewModel.approval.state.collectAsState()
                                androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    if (approvalState.month.isBlank()) {
                                        viewModel.approval.open(savedInstanceState?.getString("approval_month")
                                            ?.takeIf { it.isNotBlank() } ?: com.numbawang.leimaus.approval.ApprovalCalendar.previousMonth())
                                    } else viewModel.approval.refresh()
                                }
                                com.numbawang.leimaus.ui.screens.ApprovalScreen(approvalState,
                                    viewModel.approval::refresh, viewModel.approval::select, viewModel.approval::approve)
                            }
                        }

                        val achievementPopup by viewModel.achievementPopup.collectAsState()
                        AchievementUnlockedBanner(
                            achievement = achievementPopup,
                            onDismiss = { viewModel.dismissAchievementPopup() },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}
