package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedLocationIntent(intent)
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
        handleSharedLocationIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            MyApplicationTheme(appTheme = settings.appTheme) {
                val context = LocalContext.current
                var selectedTab by remember { mutableIntStateOf(0) }
                val snackbarHostState = remember { SnackbarHostState() }

                val uiMessage by viewModel.uiMessage.collectAsState()

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

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Tuntivelho",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            actions = {
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
                        NavigationBar(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Etusivu") },
                                label = { Text("Etusivu") }
                            )

                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Asetukset") },
                                label = { Text("Asetukset") }
                            )

                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.History, contentDescription = "Historia") },
                                label = { Text("Historia") }
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { selectedTab = 1 }
                            )
                            1 -> SettingsScreen(
                                viewModel = viewModel
                            )
                            2 -> HistoryScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
