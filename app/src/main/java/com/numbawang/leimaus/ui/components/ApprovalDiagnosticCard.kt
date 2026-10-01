package com.numbawang.leimaus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.numbawang.leimaus.approval.ApprovalDiagnostic
import com.numbawang.leimaus.approval.ApprovalEntryState

@Composable
fun ApprovalDiagnosticCard(state: ApprovalEntryState, diagnostic: ApprovalDiagnostic,
    onRefresh: () -> Unit, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tuntien hyväksynnän vianmääritys", style = MaterialTheme.typography.titleMedium)
            Text(diagnostic.message)
            Text("Tarkistus vain lukee tietoja. Se ei hyväksy tunteja.")
            if (state == ApprovalEntryState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onRefresh, enabled = state != ApprovalEntryState.Loading) { Text("Tarkista hyväksyntätila") }
            TextButton(onClick = onCopy, enabled = diagnostic.report.isNotBlank() && state != ApprovalEntryState.Loading) {
                Text("Kopioi vianmääritystiedot")
            }
            Text("Kopio sisältää vain version, kohdekuukauden, tilakoodit ja jaksojen lukumäärät. Ei tunnuksia tai tuntimääriä.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
