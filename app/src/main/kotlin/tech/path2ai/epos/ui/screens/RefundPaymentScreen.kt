package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.CompletedOrder
import tech.path2ai.epos.models.OrderLineItem
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen

private enum class RefundState { ITEM_SELECTION, CONNECTING, PROCESSING, APPROVED, FAILED, ERROR }

// Refund accent colour — purple, distinct from green (sale) and red (error)
private val RefundColor = Color(0xFF7C3AED)

// "Already refunded" tag colour, matching the order-history REFUNDED badge.
private val RefundedTagColor = Color(0xFFFF9800)

@Composable
fun RefundPaymentScreen(
    order: CompletedOrder,
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    onDismiss: () -> Unit
) {
    // Start on item selection: the cashier picks which lines from the original
    // sale to refund. The refund value is the sum of the selected lines.
    var state by remember { mutableStateOf(RefundState.ITEM_SELECTION) }
    var errorMessage by remember { mutableStateOf("") }
    var refundRef by remember { mutableStateOf<String?>(null) }
    // Line indices (into order.lineItems) the cashier has ticked for this refund.
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    // Captured at confirm time so the terminal call + recording are stable.
    var refundPence by remember { mutableStateOf(0) }
    var refundTipPence by remember { mutableStateOf(0) }
    // Bumped when the cashier confirms — keys the LaunchedEffect.
    var startToken by remember { mutableStateOf(0) }
    val connectionState by terminalManager.connectionState.collectAsState()

    // Live totals, recomputed as the selection changes.
    val unrefunded = order.unrefundedLineIndices
    val selectedValue = selectedIndices.sumOf { order.lineItems.getOrNull(it)?.lineTotal ?: 0 }
    // Clearing refund = this pass selects every remaining line, so the whole sale
    // is now refunded. Only then do we also return the customer's tip, so the sum
    // of all partial refunds reconciles to the original total.
    val isClearingRefund = selectedIndices.isNotEmpty() && unrefunded.all { it in selectedIndices }
    val tipToRefund = if (isClearingRefund) (order.tipAmountPence ?: 0) else 0
    val liveRefundPence = selectedValue + tipToRefund

    // Closing mid-refund must tell the terminal to cancel, or the emulator
    // sits waiting for the card until its own timeout (same as the sale flow).
    val cancelAndDismiss = {
        if (state == RefundState.CONNECTING || state == RefundState.PROCESSING) {
            terminalManager.cancelCurrentOperation()
        }
        onDismiss()
    }

    LaunchedEffect(startToken) {
        if (startToken == 0) return@LaunchedEffect  // still on the item-selection step
        try {
            if (connectionState !is TerminalConnectionState.Connected) {
                state = RefundState.CONNECTING
                terminalManager.connect()
                terminalManager.connectionState.first { it is TerminalConnectionState.Connected }
            }

            val termRef = order.terminalReference
            if (termRef == null) {
                errorMessage = "No terminal reference for this order."
                state = RefundState.ERROR
                return@LaunchedEffect
            }

            state = RefundState.PROCESSING
            val request = TerminalRefundRequest(
                originalTerminalReference = termRef,
                amountPence = refundPence,
                currencyCode = order.currencyCode,
                orderReference = orderManager.generateReference()
            )
            val response = terminalManager.submitRefund(request)
            if (response.succeeded) {
                refundRef = response.refundReference
                orderManager.recordItemRefund(
                    originalOrder = order,
                    refundReference = response.refundReference,
                    refundedIndices = selectedIndices.toList().sorted(),
                    tipRefundPence = refundTipPence
                )
                state = RefundState.APPROVED
            } else {
                errorMessage = response.failureReason ?: "Refund declined"
                state = RefundState.FAILED
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Terminal error"
            state = RefundState.ERROR
        }
    }

    Dialog(
        onDismissRequest = { if (state != RefundState.CONNECTING && state != RefundState.PROCESSING) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.46f),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Header ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Refund",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = RefundColor
                    )
                    if (state != RefundState.APPROVED) {
                        IconButton(onClick = cancelAndDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray)
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }

                // ── Amount ───────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Refund Amount", fontSize = 13.sp, color = Color.Gray)
                    val headerPence = if (state == RefundState.ITEM_SELECTION) liveRefundPence else refundPence
                    Text(
                        "£%.2f".format(headerPence / 100.0),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = RefundColor
                    )
                    Text(
                        order.orderReference,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // ── Status card ──────────────────────────────────────────────
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (state) {

                            RefundState.ITEM_SELECTION -> {
                                Text(
                                    "Select the items to refund.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    order.lineItems.forEachIndexed { index, item ->
                                        RefundItemRow(
                                            item = item,
                                            alreadyRefunded = index in order.refundedLineIndices,
                                            selected = index in selectedIndices,
                                            onToggle = {
                                                selectedIndices =
                                                    if (index in selectedIndices) selectedIndices - index
                                                    else selectedIndices + index
                                            }
                                        )
                                    }
                                }
                                if (tipToRefund > 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tip (final refund)", fontSize = 13.sp, color = Color.Gray)
                                        Text(
                                            "£%.2f".format(tipToRefund / 100.0),
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        refundPence = liveRefundPence
                                        refundTipPence = tipToRefund
                                        state = RefundState.CONNECTING
                                        startToken += 1
                                    },
                                    enabled = selectedIndices.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = RefundColor)
                                ) {
                                    Text(
                                        "Refund £%.2f".format(liveRefundPence / 100.0),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            RefundState.CONNECTING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(44.dp),
                                    color = RefundColor,
                                    strokeWidth = 3.dp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Connecting to Terminal",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text("Please wait…", fontSize = 13.sp, color = Color.Gray)
                            }

                            RefundState.PROCESSING -> {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = RefundColor
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Processing Refund",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Present card on the terminal",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = RefundColor,
                                    strokeWidth = 2.5.dp
                                )
                            }

                            RefundState.APPROVED -> {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = OCGreen
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Refund Approved",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = OCGreen
                                )
                                refundRef?.let { ref ->
                                    Spacer(Modifier.height(6.dp))
                                    Text("Ref: $ref", fontSize = 12.sp, color = Color.Gray)
                                }
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                            }

                            RefundState.FAILED -> {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Refund Failed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    errorMessage,
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                            }

                            RefundState.ERROR -> {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = Color(0xFFFF9800)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Terminal Error",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    errorMessage,
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * One selectable line in the refund picker. Already-refunded lines are struck
 * through, greyed and locked (you can't refund the same line twice).
 */
@Composable
private fun RefundItemRow(
    item: OrderLineItem,
    alreadyRefunded: Boolean,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val decoration = if (alreadyRefunded) TextDecoration.LineThrough else TextDecoration.None
    val textColor = if (alreadyRefunded) Color.Gray else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (alreadyRefunded) Modifier else Modifier.clickable { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected && !alreadyRefunded,
            onCheckedChange = if (alreadyRefunded) null else { _ -> onToggle() },
            enabled = !alreadyRefunded,
            colors = CheckboxDefaults.colors(checkedColor = RefundColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                textDecoration = decoration
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%d × £%.2f".format(item.quantity, item.unitPrice / 100.0),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textDecoration = decoration
                )
                if (alreadyRefunded) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Refunded",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RefundedTagColor
                    )
                }
            }
        }
        Text(
            "£%.2f".format(item.lineTotal / 100.0),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textDecoration = decoration
        )
    }
}
