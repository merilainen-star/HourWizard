package com.numbawang.leimaus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.ui.MainViewModel
import com.numbawang.leimaus.ui.components.BalanceChartCard

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val rawResponse by viewModel.rawApiResponse.collectAsState()

    var selectedLogForDebug by remember { mutableStateOf<StampEntity?>(null) }
    var showServerDebugDialog by remember { mutableStateOf(false) }
    var showManualShift by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val loading by viewModel.isLoading.collectAsState()

    if (showManualShift) {
        com.numbawang.leimaus.ui.components.ManualShiftDialog(viewModel) { showManualShift = false }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Leimaushistoria & Tuntitase",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tuntitaseen kertymä ja leimaushistoria",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showServerDebugDialog = true },
                    modifier = Modifier.testTag("server_debug_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Palvelimen API-lokit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Tyhjennä historia",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Tuntitase Kertymäkäyrä
            item(key = "manual_shift") {
                Button(onClick = { showManualShift = true }, enabled = !loading,
                    modifier = Modifier.fillMaxWidth().testTag("add_manual_shift")) {
                    Text("Lisää unohtunut työvuoro")
                }
            }
            item(key = "balance_chart") {
                BalanceChartCard(logs = logs, settings = settings)
            }

            // Section Title for Logs
            item(key = "logs_header") {
                Text(
                    text = "Leimausloki",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            if (logs.isEmpty()) {
                item(key = "empty_logs") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Ei vielä leimauslokeja",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aiemmat leimaukset näkyvät täällä aikajärjestyksessä.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (log.actionType == "SISÄÄN") Color(0xFFD1FAE5) else Color(0xFFFFEDD5)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (log.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (log.isSuccess) {
                                        if (log.actionType == "SISÄÄN") Color(0xFF059669) else Color(0xFFEA580C)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = log.actionType,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (log.actionType == "SISÄÄN") Color(0xFF059669) else Color(0xFFEA580C)
                                    )

                                    val helsinkiTz = remember { java.util.TimeZone.getTimeZone("Europe/Helsinki") }
                                    val displayFormattedTime = remember(log.timestamp, log.formattedTime) {
                                        if (log.timestamp > 0) {
                                            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                                                .apply { timeZone = helsinkiTz }
                                                .format(java.util.Date(log.timestamp))
                                        } else {
                                            log.formattedTime
                                        }
                                    }

                                    Text(
                                        text = displayFormattedTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (log.balance.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "Saldo: ${log.balance}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { selectedLogForDebug = log },
                                modifier = Modifier.testTag("log_item_debug_${log.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "Näytä leimauksen lokitiedot",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Individual Log Debug Dialog
    selectedLogForDebug?.let { log ->
        val clipboardManager = LocalClipboardManager.current
        val detailsText = remember(log) {
            buildString {
                appendLine("=== LEIMAUKSEN LOKITIEDOT ===")
                appendLine("Tyyppi: ${log.actionType}")
                appendLine("Aika: ${log.formattedTime} (ts=${log.timestamp})")
                appendLine("Tila: ${if (log.isSuccess) "Onnistunut" else "Virhe/Epäonnistunut"}")
                appendLine("Viesti: ${log.message}")
                if (log.balance.isNotBlank()) appendLine("Saldo: ${log.balance}")
                appendLine("\n--- RAAKA API / JSON -LOKI ---")
                if (log.rawDetails.isNotBlank()) {
                    appendLine(log.rawDetails)
                } else if (rawResponse.isNotBlank()) {
                    appendLine(rawResponse)
                } else {
                    appendLine("Ei erillistä raakalokitietoa tallennettuna.")
                }
            }
        }

        AlertDialog(
            onDismissRequest = { selectedLogForDebug = null },
            title = {
                Text(
                    text = "Leimauksen lokitiedot (${log.actionType})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Yksittäisen leimauksen raaka API-vastaus ja lokitiedot:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = detailsText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(detailsText))
                    }
                ) {
                    Text("Kopioi")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogForDebug = null }) {
                    Text("Sulje")
                }
            }
        )
    }

    // Server API Raw Response Dialog (Header button)
    if (showServerDebugDialog) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showServerDebugDialog = false },
            title = {
                Text(
                    text = "Viimeisin Palvelimen API-Vastaus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Alla viimeisimmän palvelinkutsun raaka vastaus ja JSON-rakenne:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = rawResponse.ifBlank { "Ei vielä tehtyjä palvelinhakuja." },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(rawResponse))
                    }
                ) {
                    Text("Kopioi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDebugDialog = false }) {
                    Text("Sulje")
                }
            }
        )
    }
}

