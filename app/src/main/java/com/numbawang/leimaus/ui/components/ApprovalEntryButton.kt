package com.numbawang.leimaus.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.numbawang.leimaus.approval.ApprovalEntryState

@Composable
fun ApprovalEntryButton(state: ApprovalEntryState, onClick: () -> Unit) {
    if (state == ApprovalEntryState.Pending) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text("Hyväksy edellisen kuukauden tunnit")
        }
    }
}
