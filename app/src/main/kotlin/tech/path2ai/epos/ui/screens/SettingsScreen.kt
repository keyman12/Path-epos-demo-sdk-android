package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.AppTerminalManager
import tech.path2ai.epos.terminal.TerminalConnectionState
import tech.path2ai.epos.ui.theme.OCGreen

private enum class SettingsSection {
    TERMINAL, ORDERS, DIAGNOSTICS, EMAIL_CONFIG, ABOUT
}

@Composable
fun SettingsScreen(
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    onBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,  // retained for nav-graph compat
    onNavigateToHistory: () -> Unit    // retained for nav-graph compat
) {
    val connectionState by terminalManager.connectionState.collectAsState()
    val orders by orderManager.orders.collectAsState()
    var selectedSection by remember { mutableStateOf(SettingsSection.TERMINAL) }

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Left sidebar ─────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Terminal nav item — with connection status dot badge
                SettingsNavItem(
                    icon = Icons.Default.CreditCard,
                    label = "Payment Terminal",
                    selected = selectedSection == SettingsSection.TERMINAL,
                    onClick = { selectedSection = SettingsSection.TERMINAL },
                    badge = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        is TerminalConnectionState.Connected -> OCGreen
                                        is TerminalConnectionState.Connecting -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                        )
                    }
                )

                // Orders nav item — with count badge
                SettingsNavItem(
                    icon = Icons.Default.History,
                    label = "Order History",
                    selected = selectedSection == SettingsSection.ORDERS,
                    onClick = { selectedSection = SettingsSection.ORDERS },
                    badge = if (orders.isNotEmpty()) {
                        {
                            Text(
                                "${orders.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else null
                )

                // Diagnostics nav item
                SettingsNavItem(
                    icon = Icons.Default.BugReport,
                    label = "Diagnostics",
                    selected = selectedSection == SettingsSection.DIAGNOSTICS,
                    onClick = { selectedSection = SettingsSection.DIAGNOSTICS }
                )

                // Email Settings nav item
                SettingsNavItem(
                    icon = Icons.Default.Email,
                    label = "Email Settings",
                    selected = selectedSection == SettingsSection.EMAIL_CONFIG,
                    onClick = { selectedSection = SettingsSection.EMAIL_CONFIG }
                )

                // About nav item
                SettingsNavItem(
                    icon = Icons.Default.Info,
                    label = "About",
                    selected = selectedSection == SettingsSection.ABOUT,
                    onClick = { selectedSection = SettingsSection.ABOUT }
                )
            }
        }

        // ── Vertical divider ─────────────────────────────────────────────────
        VerticalDivider()

        // ── Right detail pane ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            when (selectedSection) {
                SettingsSection.TERMINAL -> {
                    TerminalSettingsContent(
                        terminalManager = terminalManager,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
                SettingsSection.ORDERS -> {
                    OrderHistoryContent(
                        terminalManager = terminalManager,
                        orderManager = orderManager,
                        showClearAll = true
                    )
                }
                SettingsSection.DIAGNOSTICS -> {
                    DiagnosticsContent(terminalManager = terminalManager)
                }
                SettingsSection.EMAIL_CONFIG -> {
                    SMTPConfigContent()
                }
                SettingsSection.ABOUT -> {
                    AboutDetailContent(terminalManager = terminalManager, orderManager = orderManager)
                }
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        badge = badge,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun AboutDetailContent(
    terminalManager: AppTerminalManager,
    orderManager: OrderManager
) {
    val orders by orderManager.orders.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("App") },
            supportingContent = { Text("OrderChampion EPOS") },
            leadingContent = { Icon(Icons.Default.Storefront, contentDescription = null) },
            trailingContent = { Text("Demo", color = Color.Gray, fontSize = 13.sp) }
        )
        ListItem(
            headlineContent = { Text("Version") },
            leadingContent = { Icon(Icons.Default.NewReleases, contentDescription = null) },
            trailingContent = { Text("1.0.0", color = Color.Gray, fontSize = 13.sp) }
        )
        ListItem(
            headlineContent = { Text("Terminal Layer") },
            leadingContent = { Icon(Icons.Default.BluetoothConnected, contentDescription = null) },
            trailingContent = {
                Text(
                    terminalManager.adapterName,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        )
        ListItem(
            headlineContent = { Text("Total Orders") },
            leadingContent = { Icon(Icons.Default.Receipt, contentDescription = null) },
            trailingContent = {
                Text(
                    "${orders.size}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Text(
            "Path Terminal SDK",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Payment processing powered by the Path Terminal SDK with BLE emulator adapter.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            lineHeight = 18.sp
        )
    }
}
