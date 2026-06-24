package tech.path2ai.epos.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import tech.path2ai.epos.terminal.CustomerDisplaySettings
import tech.path2ai.epos.terminal.TerminalBackend
import tech.path2ai.epos.terminal.TerminalBackendSettings
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.terminal.AppTerminalManager
import tech.path2ai.epos.terminal.PaymentSettings
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

    var backend by remember { mutableStateOf(TerminalBackendSettings.backend(context)) }
    var emulatorHost by remember { mutableStateOf(TerminalBackendSettings.emulatorHost(context)) }
    var verifoneHost by remember { mutableStateOf(TerminalBackendSettings.verifoneHost(context)) }
    var loginUsername by remember { mutableStateOf(TerminalBackendSettings.loginUsername(context)) }
    var loginPassword by remember { mutableStateOf(TerminalBackendSettings.loginPassword(context)) }
    var loginShift by remember { mutableStateOf(TerminalBackendSettings.loginShift(context)) }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var refundPassword by remember { mutableStateOf(TerminalBackendSettings.refundPassword(context)) }
    val needsBle = backend == TerminalBackend.EMULATOR_BLE

    Column(modifier = modifier.padding(16.dp)) {
        // ── Terminal backend (the switch) ───────────────────────────────
        Text("Terminal Backend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val entries = listOf(
                TerminalBackend.EMULATOR_BLE to "Emulator (BLE)",
                TerminalBackend.EMULATOR_WIFI to "Emulator (Wi-Fi)",
                TerminalBackend.VERIFONE to "Verifone (Wi-Fi)"
            )
            entries.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = backend == value,
                    onClick = {
                        if (backend != value) {
                            backend = value
                            TerminalBackendSettings.setBackend(context, value)
                            terminalManager.applyBackend()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size)
                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── Terminal login (shared across all backends) ──────────────────
        // Every backend performs a connect-time login; the emulator is a
        // faithful stand-in for the real terminal. Locked while connected.
        Text("Terminal login", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = loginUsername,
            onValueChange = {
                loginUsername = it
                TerminalBackendSettings.setLoginUsername(context, it)
            },
            label = { Text("Login username") },
            singleLine = true,
            enabled = connectionState !is TerminalConnectionState.Connected,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = loginPassword,
            onValueChange = {
                loginPassword = it
                TerminalBackendSettings.setLoginPassword(context, it)
            },
            label = { Text("Login password") },
            singleLine = true,
            visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                    Icon(
                        imageVector = if (loginPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (loginPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            enabled = connectionState !is TerminalConnectionState.Connected,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = loginShift,
            onValueChange = {
                loginShift = it
                TerminalBackendSettings.setLoginShift(context, it)
            },
            label = { Text("Login shift") },
            singleLine = true,
            enabled = connectionState !is TerminalConnectionState.Connected,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (backend == TerminalBackend.EMULATOR_WIFI) {
            OutlinedTextField(
                value = emulatorHost,
                onValueChange = {
                    emulatorHost = it
                    TerminalBackendSettings.setEmulatorHost(context, it)
                },
                label = { Text("Emulator IP (shown on its welcome screen)") },
                placeholder = { Text("192.168.1.xx") },
                singleLine = true,
                // Lock the host once connected — change it via Disconnect first.
                enabled = connectionState !is TerminalConnectionState.Connected,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Put the emulator in Wi-Fi mode first: Config → Connection on the device. Port 9700.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { terminalManager.applyHostAndConnect() },
                enabled = connectionState !is TerminalConnectionState.Connected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply & Connect")
            }
        }
        if (backend == TerminalBackend.VERIFONE) {
            OutlinedTextField(
                value = verifoneHost,
                onValueChange = {
                    verifoneHost = it
                    TerminalBackendSettings.setVerifoneHost(context, it)
                },
                label = { Text("Terminal IP") },
                singleLine = true,
                // Lock the host once connected — change it via Disconnect first.
                enabled = connectionState !is TerminalConnectionState.Connected,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = refundPassword,
                onValueChange = {
                    refundPassword = it
                    TerminalBackendSettings.setRefundPassword(context, it)
                },
                label = { Text("Refund password (blank on test estates)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Only ONE client can be connected to the terminal at a time — disconnect any harness first.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { terminalManager.applyHostAndConnect() },
                enabled = connectionState !is TerminalConnectionState.Connected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply & Connect")
            }
        }

        Spacer(Modifier.height(16.dp))

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

        if (needsBle && !blePermissionGranted) {
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

        // Scan + device-list UI is only meaningful for BLE. For Wi-Fi/Verifone you've typed the
        // host and connect with "Apply & Connect" above, so hide all of this there.
        if (needsBle) {
        Text(
            when (backend) {
                TerminalBackend.EMULATOR_BLE -> "Path POS Emulator"
                TerminalBackend.EMULATOR_WIFI -> "Path POS Emulator (Wi-Fi)"
                TerminalBackend.VERIFONE -> "Verifone Terminal"
            },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { terminalManager.startScan() },
            modifier = Modifier.fillMaxWidth(),
            enabled = (!needsBle || blePermissionGranted) && !isScanning && connectionState !is TerminalConnectionState.Connected
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (needsBle) "Scan for Terminals" else "Find Terminal")
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
                when (backend) {
                    TerminalBackend.EMULATOR_BLE ->
                        "No terminals found. Ensure the Path POS Emulator is powered on and within Bluetooth range."
                    TerminalBackend.EMULATOR_WIFI ->
                        "Tap \"Find Terminal\" — the emulator must be in Wi-Fi mode on the same network."
                    TerminalBackend.VERIFONE ->
                        "Tap \"Find Terminal\", then Connect — the terminal must be idle on the same network."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        }  // end if (needsBle) — BLE-only scan / device-list UI

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

        // ── Payment Options ─────────────────────────────────────────────
        Text("Payment Options", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        var allowTipping by remember {
            mutableStateOf(PaymentSettings.isTippingAllowed(context))
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow tipping", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Asks the customer to pick a tip (10% / 15% / 20% / No tip) " +
                                "before the card tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = allowTipping,
                        onCheckedChange = {
                            allowTipping = it
                            PaymentSettings.setTippingAllowed(context, it)
                        }
                    )
                }
                var allowPreAuth by remember {
                    mutableStateOf(PaymentSettings.isPreAuthAllowed(context))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow pre-authorization", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Offers Pre-auth (hold a card, adjust, then complete or void). " +
                                "Mirrors the terminal's Merchant.PreAuthEnabled gate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = allowPreAuth,
                        onCheckedChange = {
                            allowPreAuth = it
                            PaymentSettings.setPreAuthAllowed(context, it)
                        }
                    )
                }
                if (allowPreAuth) {
                    var tabCapText by remember {
                        mutableStateOf("%.2f".format(PaymentSettings.tabCapPence(context) / 100.0))
                    }
                    OutlinedTextField(
                        value = tabCapText,
                        onValueChange = { v ->
                            tabCapText = v
                            v.toDoubleOrNull()?.let {
                                PaymentSettings.setTabCapPence(context, Math.round(it * 100).toInt())
                            }
                        },
                        label = { Text("Tab limit (£)") },
                        supportingText = { Text("Max a tab can accrue before more items are blocked.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Customer Display (attract-mode merchant logo) ────────────────
        Text("Customer Display", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        var brandingEnabled by remember { mutableStateOf(CustomerDisplaySettings.isEnabled(context)) }
        var logoCaption by remember { mutableStateOf(CustomerDisplaySettings.caption(context)) }
        // Bumped after upload/reset to force the preview to re-decode the file.
        var logoVersion by remember { mutableStateOf(0) }
        val logoBitmap = remember(logoVersion) {
            val bytes = CustomerDisplaySettings.logoBytes(context)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        val hasCustomLogo = remember(logoVersion) { CustomerDisplaySettings.hasCustomLogo(context) }
        val logoPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val src = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (src != null && CustomerDisplaySettings.saveLogo(context, src) != null) {
                    logoVersion++
                    if (connectionState is TerminalConnectionState.Connected) {
                        terminalManager.applyCustomerDisplayBranding()
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show merchant logo", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Shows your logo on the terminal's customer screen when it connects, " +
                                "and again after every payment (attract mode). Verifone terminal only.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                    }
                    Switch(
                        checked = brandingEnabled,
                        onCheckedChange = {
                            brandingEnabled = it
                            CustomerDisplaySettings.setEnabled(context, it)
                            if (connectionState is TerminalConnectionState.Connected) {
                                terminalManager.applyCustomerDisplayBranding()
                            }
                        }
                    )
                }
                if (brandingEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap,
                                contentDescription = "Customer display logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.padding(12.dp).fillMaxSize()
                            )
                        } else {
                            Text("No image", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(
                            onClick = { logoPicker.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose logo")
                        }
                        if (hasCustomLogo) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    CustomerDisplaySettings.resetLogo(context)
                                    logoVersion++
                                    if (connectionState is TerminalConnectionState.Connected) {
                                        terminalManager.applyCustomerDisplayBranding()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Reset to Path Cafe") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = logoCaption,
                        onValueChange = {
                            logoCaption = it
                            CustomerDisplaySettings.setCaption(context, it)
                        },
                        label = { Text("Caption shown under the logo (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { terminalManager.applyCustomerDisplayBranding() },
                        enabled = connectionState is TerminalConnectionState.Connected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply to terminal now")
                    }
                    Text(
                        "Tip: a simple, high-contrast logo reads best — the customer screen is small and low-resolution.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("About", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ListItem(
            headlineContent = { Text("Transport") },
            trailingContent = {
                Text(
                    when (backend) {
                        TerminalBackend.EMULATOR_BLE -> "Bluetooth Low Energy"
                        TerminalBackend.EMULATOR_WIFI -> "TCP/IP (emulator Wi-Fi mode)"
                        TerminalBackend.VERIFONE -> "TCP/IP (Verifone PSDK)"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
            }
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
