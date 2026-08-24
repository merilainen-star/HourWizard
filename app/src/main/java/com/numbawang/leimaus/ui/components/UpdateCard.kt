package com.numbawang.leimaus.ui.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.numbawang.leimaus.BuildConfig
import com.numbawang.leimaus.data.update.UpdateStatus

/**
 * Says whether the installed build is the one GitHub Actions last published, and offers the
 * download when it is not.
 *
 * The APK is streamed into Android's private PackageInstaller staging area. The user never has
 * to save or reopen a file from Downloads, but Android still owns the final update confirmation.
 */
@Composable
fun UpdateCard(
    status: UpdateStatus,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { onInstall() },
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status is UpdateStatus.Available) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sovelluksen versio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when (status) {
                UpdateStatus.Idle, UpdateStatus.Checking -> {
                    Text(
                        text = "Tarkistetaan…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }

                is UpdateStatus.UpToDate -> Text(
                    text = "Ajan tasalla (${status.versionName}).",
                    style = MaterialTheme.typography.bodyMedium
                )

                is UpdateStatus.Available -> {
                    Text(
                        text = "Päivitys saatavilla: ${status.versionName} (${status.sizeMb} MB).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Sovellus lataa päivityksen ja avaa Androidin asennusvahvistuksen " +
                            "automaattisesti. Leimaushistoria ja asetukset säilyvät.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                // Preserve the selected release before leaving for system settings;
                                // SettingsScreen performs its normal update check again on resume.
                                onInstall()
                                installPermissionLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        "package:${context.packageName}".toUri(),
                                    )
                                )
                            } else {
                                onInstall()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lataa ja asenna päivitys")
                    }
                }

                is UpdateStatus.Downloading -> {
                    Text(
                        text = "Ladataan versiota ${status.versionName}: " +
                            "${status.progressPercent} %",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { status.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Voit jatkaa sovelluksen käyttöä latauksen aikana.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is UpdateStatus.AwaitingInstallConfirmation -> {
                    Text(
                        text = "Versio ${status.versionName} on ladattu ja tarkistettu.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Odotetaan Androidin asennusvahvistusta…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                UpdateStatus.LocalBuild -> Text(
                    text = "Paikallinen build (${BuildConfig.VERSION_NAME}). " +
                        "Päivityksiä verrataan vain GitHubista asennettuihin versioihin.",
                    style = MaterialTheme.typography.bodyMedium
                )

                is UpdateStatus.Failed -> {
                    Text(
                        text = "Version tarkistus ei onnistunut: ${status.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onCheck) { Text("Yritä uudelleen") }
                }
            }
        }
    }
}
