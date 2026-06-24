package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.path2ai.epos.managers.TabManager
import tech.path2ai.epos.terminal.AppTerminalManager
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.TerminalPreAuthRequest

/**
 * Open a tab: enter a name, pick a pre-auth hold amount, place the hold (card
 * tap). On approval the tab is saved and we return to the Tabs list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OpenTabScreen(
    terminalManager: AppTerminalManager,
    tabManager: TabManager,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val presets = listOf(2500, 5000, 7500, 10000)   // £25 / £50 / £75 / £100

    var name by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(5000) }   // default £50
    var otherSelected by remember { mutableStateOf(false) }
    var otherText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val connected = terminalManager.connectionState.value is TerminalConnectionState.Connected

    fun preAuthPence(): Int? =
        if (otherSelected) otherText.toDoubleOrNull()?.let { Math.round(it * 100).toInt() }
        else selectedPreset

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open tab") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!connected) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Not connected to a terminal — connect in Terminal Settings first.",
                        Modifier.padding(12.dp))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Customer name") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Pre-auth hold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    FilterChip(
                        selected = !otherSelected && selectedPreset == p,
                        onClick = { otherSelected = false; selectedPreset = p },
                        label = { Text("£${p / 100}") }
                    )
                }
                FilterChip(
                    selected = otherSelected,
                    onClick = { otherSelected = true },
                    label = { Text("Other") }
                )
            }
            if (otherSelected) {
                OutlinedTextField(
                    value = otherText,
                    onValueChange = { otherText = it },
                    label = { Text("Hold amount (£)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                enabled = connected && !busy && name.isNotBlank() && (preAuthPence() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val hold = preAuthPence() ?: return@Button
                    busy = true; message = "Placing £${"%.2f".format(hold / 100.0)} hold — present card…"
                    scope.launch {
                        try {
                            val r = terminalManager.submitPreAuth(
                                TerminalPreAuthRequest(hold, "GBP", "tab:${name.trim()}")
                            )
                            if (r.succeeded) {
                                tabManager.openTab(name.trim(), r.holdAmountPence.takeIf { it > 0 } ?: hold, r.terminalReference)
                                onDone()
                            } else {
                                message = "Hold declined: ${r.failureReason}"
                            }
                        } catch (e: Exception) {
                            message = "Error: ${e.message}"
                        } finally { busy = false }
                    }
                }
            ) { Text("Place hold & open tab") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
