package tech.path2ai.epos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.path2ai.epos.terminal.AppTerminalManager
import tech.path2ai.epos.terminal.LogEntry
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.ui.theme.OCGreen
import java.text.SimpleDateFormat
import java.util.*

/**
 * Embeddable diagnostics content — mirrors iOS DeveloperDiagnosticsView.
 * Sections: Versions, Connection, Transaction Status, Support Bundle, Last Error, Logs.
 */
@Composable
fun DiagnosticsContent(
    terminalManager: AppTerminalManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by terminalManager.connectionState.collectAsState()
    val isBluetoothOn by terminalManager.isBluetoothPoweredOn.collectAsState()
    val lastError by terminalManager.lastError.collectAsState()
    val logEntries by terminalManager.logEntries.collectAsState()

    val scope = rememberCoroutineScope()
    var queryReqIdOverride by remember { mutableStateOf("") }
    var copyFeedback by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Logs") },
            text = { Text("Are you sure you want to clear all diagnostic logs?") },
            confirmButton = {
                TextButton(onClick = {
                    terminalManager.clearLogs()
                    showClearConfirm = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Versions ──────────────────────────────────────────────────────────
        item {
            DiagnosticsSection(title = "Versions") {
                DiagnosticsRow("Integration", terminalManager.adapterName)
                DiagnosticsRow("SDK", terminalManager.sdkVersion ?: "—")
                DiagnosticsRow("Protocol", terminalManager.protocolVersion ?: "—")
            }
        }

        // ── Connection ────────────────────────────────────────────────────────
        item {
            DiagnosticsSection(title = "Connection") {
                val stateLabel = when (val cs = connectionState) {
                    is TerminalConnectionState.Disconnected -> "Disconnected"
                    is TerminalConnectionState.Connecting -> "Connecting…"
                    is TerminalConnectionState.Connected -> "Connected"
                    is TerminalConnectionState.Unavailable -> "Unavailable: ${cs.reason}"
                }
                DiagnosticsRow(
                    "State",
                    stateLabel,
                    valueColor = when (connectionState) {
                        is TerminalConnectionState.Connected -> OCGreen
                        is TerminalConnectionState.Connecting -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                DiagnosticsRow(
                    "Ready",
                    if (terminalManager.isReady) "Yes" else "No",
                    valueColor = if (terminalManager.isReady) OCGreen else MaterialTheme.colorScheme.error
                )
                DiagnosticsRow(
                    "Bluetooth",
                    if (isBluetoothOn) "On" else "Off",
                    valueColor = if (isBluetoothOn) OCGreen else MaterialTheme.colorScheme.error
                )
            }
        }

        // ── Transaction Status ────────────────────────────────────────────────
        item {
            DiagnosticsSection(title = "Transaction Status (Debug)") {
                val lastReqId = terminalManager.lastWireRequestId
                if (lastReqId != null) {
                    DiagnosticsRow("Last req_id", lastReqId, mono = true)
                } else {
                    Text(
                        "No req_id yet — run a Sale or Refund first.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = queryReqIdOverride,
                    onValueChange = { queryReqIdOverride = it },
                    label = { Text("Override req_id (optional)", fontSize = 12.sp) },
                    placeholder = { Text("Leave blank to use last", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val rid = queryReqIdOverride.trim().ifEmpty { null }
                        scope.launch { terminalManager.queryTransactionStatus(rid) }
                    },
                    enabled = terminalManager.isReady,
                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Query transaction status")
                }

                if (!terminalManager.isReady) {
                    Text(
                        "Connect to terminal first.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ── Support Bundle ────────────────────────────────────────────────────
        item {
            DiagnosticsSection(title = "Support Bundle") {
                Text(
                    "Redacted JSON for support. No full card numbers.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val json = terminalManager.getSupportBundle()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Support Bundle", json))
                        copyFeedback = true
                        scope.launch {
                            kotlinx.coroutines.delay(1500)
                            copyFeedback = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (copyFeedback) "Copied to clipboard ✓" else "Copy support bundle (JSON)")
                }
            }
        }

        // ── Last Error ────────────────────────────────────────────────────────
        val errorText = lastError
        if (errorText != null) {
            item {
                DiagnosticsSection(title = "Last Error") {
                    Text(
                        errorText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // ── Logs ──────────────────────────────────────────────────────────────
        item {
            DiagnosticsSection(title = "Logs (${logEntries.size})") {
                // Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val allLogs = terminalManager.getLogsForCopy()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Logs", allLogs))
                        },
                        enabled = logEntries.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy all", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        enabled = logEntries.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear logs", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (logEntries.isEmpty()) {
                    Text("No logs yet.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text(
                        "Tap and hold a line to select and copy.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        // Log entries — rendered as individual items for performance
        if (logEntries.isNotEmpty()) {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            items(logEntries.reversed()) { entry ->
                SelectionContainer {
                    Text(
                        "${fmt.format(Date(entry.dateMillis))}  ${entry.text}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 1.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperDiagnosticsScreen(
    terminalManager: AppTerminalManager,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        DiagnosticsContent(
            terminalManager = terminalManager,
            modifier = Modifier.padding(padding)
        )
    }
}

// ─── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun DiagnosticsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun DiagnosticsRow(
    label: String,
    value: String,
    valueColor: Color = LocalContentColor.current,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            fontSize = 13.sp,
            color = valueColor,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.6f)
        )
    }
}
