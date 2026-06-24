package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.compose.ui.window.DialogProperties
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.managers.TabManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCDark
import tech.path2ai.epos.ui.theme.OCGreen
import tech.path2ai.epos.ui.theme.OCRed

@Composable
fun PaymentScreen(
    cartItems: List<CartItem>,
    total: Int,
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    tabManager: TabManager,
    onDismiss: () -> Unit,
    onPaymentComplete: () -> Unit
) {
    val context = LocalContext.current
    var showCardPayment by remember { mutableStateOf(false) }
    var showCashPayment by remember { mutableStateOf(false) }
    var showTabPicker by remember { mutableStateOf(false) }
    val tabs by tabManager.tabs.collectAsState()
    val openTabs = tabs.filter { it.status == TabStatus.OPEN }
    val tabsAvailable = PaymentSettings.isPreAuthAllowed(context) && openTabs.isNotEmpty()

    if (showTabPicker) {
        AddToTabDialog(
            openTabs = openTabs,
            cartTotal = total,
            capPence = PaymentSettings.tabCapPence(context),
            onPick = { tab ->
                tabManager.addItems(tab.id, cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) })
                showTabPicker = false
                onPaymentComplete()
            },
            onDismiss = { showTabPicker = false }
        )
        return
    }

    if (showCardPayment) {
        CardPaymentScreen(
            cartItems = cartItems,
            total = total,
            terminalManager = terminalManager,
            orderManager = orderManager,
            onDismiss = onDismiss,
            onComplete = onPaymentComplete
        )
        return
    }

    if (showCashPayment) {
        CashPaymentScreen(
            cartItems = cartItems,
            total = total,
            orderManager = orderManager,
            onDismiss = onDismiss,
            onComplete = onPaymentComplete
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.5f).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Payment", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text("Total: £%.2f".format(total / 100.0), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Card payment
                OutlinedButton(
                    onClick = { showCardPayment = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Card Payment", fontSize = 16.sp)
                }

                Spacer(Modifier.height(12.dp))

                // Cash payment
                OutlinedButton(
                    onClick = { showCashPayment = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Cash Payment", fontSize = 16.sp)
                }

                if (tabsAvailable) {
                    Spacer(Modifier.height(12.dp))
                    // Add to an open tab — accrues items against the tab's hold,
                    // settled later when the tab is closed. Filled brand-green so
                    // the contextual tender stands out from Card/Cash.
                    Button(
                        onClick = { showTabPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = OCGreen),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Add to Tab", fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Pick which open tab to drop the current cart onto. A tab that would exceed the
 * configured cap once these items are added is shown disabled with the reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToTabDialog(
    openTabs: List<Tab>,
    cartTotal: Int,
    capPence: Int,
    onPick: (Tab) -> Unit,
    onDismiss: () -> Unit
) {
    // Highlight the chosen tab briefly before applying, so the selection is
    // visible rather than the dialog just vanishing.
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        delay(450)
        openTabs.firstOrNull { it.id == id }?.let { onPick(it) }
    }

    fun money(p: Int) = "£%.2f".format(p / 100.0)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.5f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = OCGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Add ${money(cartTotal)} to a tab", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OCDark)
                }
                Spacer(Modifier.height(16.dp))

                openTabs.forEach { tab ->
                    val newTotal = tab.totalPence + cartTotal
                    val overCap = newTotal > capPence
                    val isSelected = selectedId == tab.id
                    val border = when {
                        overCap -> OCRed.copy(alpha = 0.4f)
                        isSelected -> OCGreen
                        else -> OCGreen.copy(alpha = 0.35f)
                    }
                    Surface(
                        onClick = { if (selectedId == null) selectedId = tab.id },
                        enabled = !overCap && selectedId == null,
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) OCGreen.copy(alpha = 0.12f) else Color.White,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, border),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar circle with the customer's initial.
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(OCGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(tab.name.take(1).uppercase(), color = OCGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tab.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OCDark)
                                if (overCap) {
                                    Text("Over the ${money(capPence)} tab limit", fontSize = 13.sp, color = OCRed, fontWeight = FontWeight.Medium)
                                } else {
                                    Text("${money(tab.totalPence)}  →  ${money(newTotal)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OCDark)
                                    Text("hold ${money(tab.preAuthPence)}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = OCGreen, modifier = Modifier.size(28.dp))
                            } else if (!overCap) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, enabled = selectedId == null, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = OCDark)
                }
            }
        }
    }
}
