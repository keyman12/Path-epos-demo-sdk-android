package tech.path2ai.epos.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.AppTerminalManager
import tech.path2ai.epos.ui.theme.OCGreen

/**
 * Embeddable content — used by both [TerminalSettingsScreen] and the Settings master-detail pane.
 */
@Composable
fun TerminalSettingsContent(
    terminalManager: AppTerminalManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by terminalManager.connectionState.collectAsState()
    val discoveredDevices by terminalManager.discoveredDevices.collectAsState()
    val isScanning by terminalManager.isScanning.collectAsState()

    var blePermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        blePermissionGranted = permissions.values.all { it }
    }

    Column(modifier = modifier.padding(16.dp)) {
        // Status card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                is TerminalConnectionState.Connected -> OCGreen.copy(alpha = 0.15f)
                                is TerminalConnectionState.Connecting -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                else -> Color(0xFFF44336).copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            is TerminalConnectionState.Connected -> Icons.Default.CheckCircle
                            is TerminalConnectionState.Connecting -> Icons.Default.Sync
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            is TerminalConnectionState.Connected -> OCGreen
                            is TerminalConnectionState.Connecting -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        when (connectionState) {
                            is TerminalConnectionState.Connected -> "Terminal Connected"
                            is TerminalConnectionState.Connecting -> "Connecting…"
                            else -> if (isScanning) "Scanning…" else "No Terminal Connected"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when (connectionState) {
                            is TerminalConnectionState.Connected -> "Ready to process payments"
                            is TerminalConnectionState.Connecting -> "Connecting to the selected terminal"
                            is TerminalConnectionState.Unavailable -> (connectionState as TerminalConnectionState.Unavailable).reason
                            else -> if (isScanning) "Searching for Path terminals…" else "Tap \"Scan for Terminals\" to find your device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (connectionState is TerminalConnectionState.Connecting || isScanning) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!blePermissionGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Bluetooth Permission Required", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text("Grant Bluetooth permission to scan for payment terminals.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ))
                        }
                    }) { Text("Grant Permission") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("Path POS Emulator", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { terminalManager.startScan() },
            modifier = Modifier.fillMaxWidth(),
            enabled = blePermissionGranted && !isScanning && connectionState !is TerminalConnectionState.Connected
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan for Terminals")
            if (isScanning) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (discoveredDevices.isNotEmpty()) {
            discoveredDevices.forEach { device ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium)
                            Text(device.id, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Button(
                            onClick = { terminalManager.connect(device) },
                            enabled = connectionState !is TerminalConnectionState.Connecting &&
                                      connectionState !is TerminalConnectionState.Connected,
                            colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                        ) { Text("Connect") }
                    }
                }
            }
        } else if (!isScanning && connectionState !is TerminalConnectionState.Connected) {
            Text(
                "No terminals found. Ensure the Path POS Emulator is powered on and within Bluetooth range.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (connectionState is TerminalConnectionState.Connected) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { terminalManager.disconnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("About", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ListItem(
            headlineContent = { Text("Transport") },
            trailingContent = { Text("Bluetooth Low Energy", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        )
        ListItem(
            headlineContent = { Text("Adapter") },
            trailingContent = { Text(terminalManager.adapterName, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsScreen(
    terminalManager: AppTerminalManager,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        TerminalSettingsContent(terminalManager, modifier = Modifier.padding(padding))
    }
}
