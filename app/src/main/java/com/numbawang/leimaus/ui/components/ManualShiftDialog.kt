package com.numbawang.leimaus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.numbawang.leimaus.data.network.ManualShift
import com.numbawang.leimaus.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ManualShiftDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    var date by rememberSaveable { mutableStateOf(
        SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Europe/Helsinki")
        }.format(Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki")).apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }.time)
    ) }
    var start by rememberSaveable { mutableStateOf(settings.morningReminderTime) }
    var end by rememberSaveable { mutableStateOf(settings.eveningReminderTime) }
    var breakText by rememberSaveable { mutableStateOf(settings.lunchBreakMinutes.toString()) }
    var nextDay by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val validation = runCatching { ManualShift.parse(date, start, end, breakText, nextDay) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Lisää unohtunut työvuoro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (settings.isDemoMode) "Demotila: vuoro tallennetaan vain tähän sovellukseen."
                    else "Kirjaa toteutunut työvuoro Tuntivelhoon. Käytössä ovat oma oletustyöpisteesi ja työn laatu. Ajat ovat Suomen aikaa.")
                OutlinedTextField(date, { date = it; error = null }, label = { Text("Päivä (pp.kk.vvvv)") },
                    singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it; error = null }, label = { Text("Alku (tt:mm)") },
                        singleLine = true, enabled = !loading, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it; error = null }, label = { Text("Loppu (tt:mm)") },
                        singleLine = true, enabled = !loading, modifier = Modifier.weight(1f))
                }
                Row {
                    Checkbox(nextDay, { nextDay = it; error = null }, enabled = !loading)
                    Text("Vuoro päättyi seuraavana päivänä", modifier = Modifier.padding(top = 12.dp))
                }
                OutlinedTextField(breakText, { breakText = it; error = null }, label = { Text("Palkaton tauko (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
                validation.getOrNull()?.let { shift ->
                    val minutes = (shift.endSeconds - shift.startSeconds) / 60 - shift.breakMinutes
                    Text("Työaika tauon jälkeen: ${minutes / 60} h ${minutes % 60} min")
                }
                (error ?: validation.exceptionOrNull()?.message)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = !loading && validation.isSuccess, onClick = {
                error = null
                validation.getOrNull()?.let { shift ->
                    viewModel.addManualShift(shift) { result ->
                        if (result.isError) error = result.text else onDismiss()
                    }
                }
            }) { Text(if (loading) "Tallennetaan…" else "Tallenna työvuoro") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Peruuta") } }
    )
}
