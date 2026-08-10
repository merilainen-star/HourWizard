package com.numbawang.leimaus.ui.components

import android.content.Intent
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
 * The download deliberately hands off to the browser and Android's own installer rather than
 * fetching the APK in-app. In-app installation would need `REQUEST_INSTALL_PACKAGES`, a
 * `FileProvider` and download handling, and would still show the same system "Update this app?"
 * dialog — it saves one tap for a permission Play Protect treats with suspicion.
 *
 * Downloading an APK to install over an existing app sounds risky, but the signing certificate
 * protects it: Android refuses to install a package signed by a different key over this one, so a
 * substituted binary cannot take the app's place.
 */
@Composable
fun UpdateCard(status: UpdateStatus, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

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
                        text = "Lataa ja avaa tiedosto — Android kysyy luvan päivitykseen. " +
                            "Leimaushistoria ja asetukset säilyvät.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, status.apkUrl.toUri())
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lataa päivitys")
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
