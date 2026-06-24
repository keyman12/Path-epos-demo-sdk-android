package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tech.path2ai.epos.managers.TabManager
import tech.path2ai.epos.models.Tab
import tech.path2ai.epos.models.TabStatus

/**
 * Lists open bar/café tabs and lets staff open a new one or recall a tab to
 * close it. A tab is a long-lived order backed by a card pre-auth hold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    tabManager: TabManager,
    onBack: () -> Unit,
    onOpenTab: () -> Unit,
    onCloseTab: (String) -> Unit
) {
    val tabs by tabManager.tabs.collectAsState()
    val open = tabs.filter { it.status == TabStatus.OPEN }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenTab,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Open tab") }
            )
        }
    ) { padding ->
        if (open.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No open tabs. Tap “Open tab” to start one.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(open, key = { it.id }) { tab ->
                    TabRow(tab, onClick = { onCloseTab(tab.id) })
                }
            }
        }
    }
}

@Composable
private fun TabRow(tab: Tab, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tab.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${tab.lineItems.size} item(s) · hold ${tab.formattedHold}",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(tab.formattedTotal, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (tab.withinHold) "within hold" else "over hold",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tab.withinHold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
