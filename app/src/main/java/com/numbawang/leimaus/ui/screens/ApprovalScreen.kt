package com.numbawang.leimaus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.numbawang.leimaus.BuildConfig
import com.numbawang.leimaus.approval.ApprovalUiState

@Composable
fun ApprovalScreen(state: ApprovalUiState, onRefresh: () -> Unit, onSelect: (Int) -> Unit, onApprove: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Hyväksy tunnit", style = MaterialTheme.typography.headlineMedium)
        Text("Päättyneet jaksot · ${state.month}")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.periods.size > 1) {
            Text("Valitse hyväksyttävä jakso")
            state.periods.forEach { period ->
                OutlinedButton(onClick = { onSelect(period.id) }, enabled = !state.busy) {
                    Text(period.label + if (period.approved) " · Hyväksytty" else "")
                }
            }
        }
        state.selected?.let { period ->
            Text(period.label, style = MaterialTheme.typography.titleLarge)
            if (period.name.isNotBlank()) Text(period.name)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("tv_tot" to "Toteuma", "tv_luetut" to "Luetut", "tase" to "Tase", "tyopaivat" to "Työpäivät").forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label); Text(period.totals[key] ?: "Ei tietoa")
                        }
                    }
                }
            }
            if (period.approved) {
                Text("Hyväksytty", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                if (period.locked) Text("Jakso on lukittu tai siirtynyt jatkokäsittelyyn.")
                val missing = buildList {
                    if (!period.stateKnown) add("hyväksyntätilan tiedot")
                    if (period.employeeId == null || period.employeeId <= 0) add("jakson henkilötunniste")
                    listOf("tv_tot" to "Toteuma", "tv_luetut" to "Luetut", "tase" to "Tase", "tyopaivat" to "Työpäivät")
                        .filter { it.first !in period.totals }.forEach { add(it.second) }
                }
                if (missing.isNotEmpty()) {
                    Text("Hyväksyntä on estetty. Palvelun vastauksesta puuttuu: ${missing.joinToString(", ")}.")
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString("Numbawang ${BuildConfig.VERSION_NAME}\nmonth=${state.month}\nstage=period_detail\n" +
                            "unknown_state=${if (period.stateKnown) 0 else 1}\n" +
                            "missing_employee=${if (period.employeeId == null || period.employeeId <= 0) 1 else 0}\n" +
                            "missing_totals=" + listOf("tv_tot", "tv_luetut", "tase", "tyopaivat")
                                .filter { it !in period.totals }.joinToString(",")))
                    }) { Text("Kopioi puuttuvien tietojen raportti") }
                }
                Button(onClick = onApprove, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy && !state.requiresRefresh && period.canApprove) {
                    Text("Hyväksy")
                }
            }
        }
        if (!state.busy && state.selected == null && state.error == null) Text("Tälle kuukaudelle ei löytynyt päättyneitä hyväksyntäjaksoja.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Päivitä tiedot") }
    }
}
