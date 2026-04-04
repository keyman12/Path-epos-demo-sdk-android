package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen
import tech.path2ai.epos.ui.theme.OCRed
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Embeddable content — used by both [OrderHistoryScreen] and the Settings master-detail pane.
 */
@Composable
fun OrderHistoryContent(
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    modifier: Modifier = Modifier,
    showClearAll: Boolean = true
) {
    val orders by orderManager.orders.collectAsState()
    var refundOrder by remember { mutableStateOf<CompletedOrder?>(null) }
    var receiptToShow by remember { mutableStateOf<FullReceipt?>(null) }
    var loadingReceiptForId by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK) }
    val scope = rememberCoroutineScope()

    refundOrder?.let { order ->
        RefundPaymentScreen(
            order = order,
            terminalManager = terminalManager,
            orderManager = orderManager,
            onDismiss = { refundOrder = null }
        )
    }

    receiptToShow?.let { receipt ->
        ReceiptDialog(
            receipt = receipt,
            onNoReceipt = { receiptToShow = null },
            onDone = { receiptToShow = null }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showClearAll && orders.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { orderManager.clearHistory() }) {
                    Text("Clear All", color = OCRed)
                }
            }
        }
        if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    Text("No orders yet", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(orders) { order ->
                    val canShowReceipt = order.paymentMethod == PaymentMethod.CARD &&
                            order.terminalReference != null
                    val isLoadingReceipt = loadingReceiptForId == order.id

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ── Order info ────────────────────────────────────
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.orderReference, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge(order.status, order.orderType)
                                }
                                Text(
                                    dateFormat.format(Date(order.date)),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                order.cardDisplay?.let {
                                    Text(it, fontSize = 12.sp, color = Color.Gray)
                                }
                            }

                            // ── Amount + action buttons ───────────────────────
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    order.formattedAmount,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                if (canShowReceipt || order.canRefund) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                                        // Receipt button (card orders with a terminal reference)
                                        if (canShowReceipt) {
                                            if (isLoadingReceipt) {
                                                Box(
                                                    modifier = Modifier
                                                        .height(32.dp)
                                                        .padding(horizontal = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = OCGreen
                                                    )
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = {
                                                        val txnRef = order.terminalReference ?: return@OutlinedButton
                                                        scope.launch {
                                                            loadingReceiptForId = order.id
                                                            try {
                                                                val receiptData = terminalManager.getReceiptData(txnRef)
                                                                val cardBlock = receiptData?.customerReceipt?.let { f ->
                                                                    CardReceiptBlock(
                                                                        status = f.status,
                                                                        timestamp = f.timestamp,
                                                                        txnRef = f.txnRef,
                                                                        terminalId = f.terminalId,
                                                                        merchantId = f.merchantId,
                                                                        authorisationCode = f.authCode,
                                                                        verificationMethod = f.verification,
                                                                        aid = f.aid,
                                                                        entryMode = f.entryMode,
                                                                        maskedPan = f.maskedPan,
                                                                        cardScheme = f.cardScheme
                                                                    )
                                                                }
                                                                receiptToShow = buildHistoryReceipt(order, cardBlock, dateFormat)
                                                            } catch (e: Exception) {
                                                                // Show receipt without card block as fallback
                                                                receiptToShow = buildHistoryReceipt(order, null, dateFormat)
                                                            } finally {
                                                                loadingReceiptForId = null
                                                            }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(32.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OCGreen),
                                                    border = BorderStroke(1.dp, OCGreen)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Receipt,
                                                        contentDescription = "Receipt",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Receipt", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }

                                        // Refund button (completed sales only)
                                        if (order.canRefund) {
                                            OutlinedButton(
                                                onClick = { refundOrder = order },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OCRed),
                                                border = BorderStroke(1.dp, OCRed)
                                            ) {
                                                Icon(
                                                    Icons.Default.Replay,
                                                    contentDescription = "Refund",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Refund", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Build a [FullReceipt] from an order history entry. Uses a single line item (iOS pattern). */
private fun buildHistoryReceipt(
    order: CompletedOrder,
    cardBlock: CardReceiptBlock?,
    dateFormat: SimpleDateFormat
): FullReceipt {
    val itemName = if (order.orderType == OrderType.REFUND) "Card refund" else "Card payment"
    val subtotal = (order.amountPence / 1.2).roundToInt()
    val vatAmount = order.amountPence - subtotal
    return FullReceipt(
        merchantName = "Path Café",
        merchantAddress = "1 Tech Street, London EC1A 1BB",
        orderNumber = order.orderReference,
        tillNumber = "01",
        cashierName = "Cashier",
        orderDate = dateFormat.format(Date(order.date)),
        lineItems = listOf(ReceiptLineItem(itemName, 1, order.amountPence)),
        subtotal = subtotal,
        vatAmount = vatAmount,
        total = order.amountPence,
        currency = order.currencyCode,
        cardReceiptBlock = cardBlock
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    onBack: () -> Unit
) {
    val orders by orderManager.orders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (orders.isNotEmpty()) {
                        TextButton(onClick = { orderManager.clearHistory() }) {
                            Text("Clear All", color = OCRed)
                        }
                    }
                }
            )
        }
    ) { padding ->
        OrderHistoryContent(
            terminalManager = terminalManager,
            orderManager = orderManager,
            modifier = Modifier.padding(padding),
            showClearAll = false
        )
    }
}

@Composable
private fun StatusBadge(status: OrderStatus, orderType: OrderType) {
    val (text, color) = when {
        orderType == OrderType.REFUND -> "REFUND" to Color(0xFF9C27B0)
        status == OrderStatus.COMPLETED -> "COMPLETED" to OCGreen
        status == OrderStatus.DECLINED -> "DECLINED" to OCRed
        status == OrderStatus.REFUNDED -> "REFUNDED" to Color(0xFFFF9800)
        else -> "" to Color.Gray
    }
    if (text.isNotEmpty()) {
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
