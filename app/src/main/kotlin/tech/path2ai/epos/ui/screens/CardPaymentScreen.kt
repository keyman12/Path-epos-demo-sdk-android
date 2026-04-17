package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private enum class CardPaymentState {
    CHECKING_TERMINAL,
    WAITING_FOR_CARD,
    PROCESSING,
    APPROVED,
    DECLINED,
    /** Customer walked away from the tip prompt — offer Try Again / Cancel. */
    CUSTOMER_TIMEOUT,
    ERROR
}

@Composable
fun CardPaymentScreen(
    cartItems: List<CartItem>,
    total: Int,
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var state by remember { mutableStateOf(CardPaymentState.CHECKING_TERMINAL) }
    var errorMessage by remember { mutableStateOf("") }
    var saleResponse by remember { mutableStateOf<TerminalSaleResponse?>(null) }
    var fullReceipt by remember { mutableStateOf<FullReceipt?>(null) }
    var showReceipt by remember { mutableStateOf(false) }
    // Bumped when the cashier hits "Try Again" after a customer timeout —
    // keyed on LaunchedEffect so it re-runs the whole flow.
    var attemptToken by remember { mutableStateOf(0) }
    val connectionState by terminalManager.connectionState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(attemptToken) {
        try {
            if (connectionState !is TerminalConnectionState.Connected) {
                state = CardPaymentState.CHECKING_TERMINAL
                terminalManager.connect()
                terminalManager.connectionState.first {
                    it is TerminalConnectionState.Connected
                }
            }
            state = CardPaymentState.WAITING_FOR_CARD
            state = CardPaymentState.PROCESSING
            val promptForTip = PaymentSettings.isTippingAllowed(context)
            val request = TerminalSaleRequest(
                orderReference = orderManager.generateReference(),
                amountPence = total,
                currencyCode = "GBP",
                lineItems = cartItems.map {
                    TerminalLineItem(it.product.name, it.quantity, it.product.price)
                },
                operatorId = "till-01",
                promptForTip = promptForTip
            )
            val response = terminalManager.submitSale(request)
            saleResponse = response
            when {
                response.authorised -> {
                    state = CardPaymentState.APPROVED
                    val tip = response.tipAmountPence
                    val totalCharged = response.totalAmountPence.takeIf { it > 0 } ?: total
                    orderManager.recordSale(
                        orderReference = request.orderReference,
                        lineItems = cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) },
                        amountPence = totalCharged,
                        currencyCode = "GBP",
                        paymentMethod = PaymentMethod.CARD,
                        cardLastFour = response.maskedPan?.takeLast(4),
                        cardScheme = response.cardScheme,
                        terminalReference = response.terminalReference,
                        authCode = response.authorisationCode,
                        baseAmountPence = response.baseAmountPence.takeIf { it > 0 } ?: total,
                        tipAmountPence = tip.takeIf { it > 0 }
                    )
                    // Build the receipt and show it after a short delay (like iOS)
                    fullReceipt = buildReceipt(cartItems, total, request.orderReference, response)
                    delay(300)
                    showReceipt = true
                }
                response.customerTimedOut -> {
                    // Recoverable — don't record a declined sale; let the cashier retry.
                    state = CardPaymentState.CUSTOMER_TIMEOUT
                }
                else -> {
                    errorMessage = response.failureReason ?: "Card declined"
                    state = CardPaymentState.DECLINED
                    orderManager.recordDeclinedSale(
                        orderReference = request.orderReference,
                        lineItems = cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) },
                        amountPence = total,
                        currencyCode = "GBP",
                        paymentMethod = PaymentMethod.CARD
                    )
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Terminal error"
            state = CardPaymentState.ERROR
        }
    }

    // Show receipt dialog when approved and receipt is ready
    if (showReceipt) {
        val receipt = fullReceipt
        if (receipt != null) {
            ReceiptDialog(
                receipt = receipt,
                onNoReceipt = onComplete,
                onDone = onComplete
            )
            return
        }
    }

    Dialog(
        onDismissRequest = {
            if (state == CardPaymentState.APPROVED ||
                state == CardPaymentState.ERROR ||
                state == CardPaymentState.DECLINED) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.46f),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Header ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Card Payment",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = OCGreen
                    )
                    // Show X cancel only while in progress
                    if (state != CardPaymentState.APPROVED) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color.Gray
                            )
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }

                // ── Amount ──────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Amount Due",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Text(
                        "£%.2f".format(total / 100.0),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = OCGreen
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

                            CardPaymentState.CHECKING_TERMINAL,
                            CardPaymentState.WAITING_FOR_CARD -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(44.dp),
                                    color = OCGreen,
                                    strokeWidth = 3.dp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Connecting to Terminal",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Please wait…",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }

                            CardPaymentState.PROCESSING -> {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = OCGreen
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Processing Payment",
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
                                    color = OCGreen,
                                    strokeWidth = 2.5.dp
                                )
                            }

                            CardPaymentState.APPROVED -> {
                                val resp = saleResponse
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = OCGreen
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Payment Approved",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = OCGreen
                                )
                                if (resp != null) {
                                    Spacer(Modifier.height(8.dp))
                                    val cardLine = listOfNotNull(
                                        resp.cardScheme,
                                        resp.maskedPan
                                    ).joinToString(" ")
                                    if (cardLine.isNotBlank()) {
                                        Text(
                                            cardLine,
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    resp.authorisationCode?.let { auth ->
                                        Text(
                                            "Auth: $auth",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Loading receipt…",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = OCGreen,
                                    strokeWidth = 2.dp
                                )
                            }

                            CardPaymentState.DECLINED -> {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Payment Declined",
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

                            CardPaymentState.CUSTOMER_TIMEOUT -> {
                                Icon(
                                    Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = Color(0xFFFF9800)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Customer Didn't Respond",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "The customer didn't pick a tip option in time. " +
                                        "You can try the sale again or cancel and return to the cart.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = { attemptToken += 1 },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                                ) {
                                    Text("Try Again", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                            }

                            CardPaymentState.ERROR -> {
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

/** Build a [FullReceipt] from the cart + sale response data. */
private fun buildReceipt(
    cartItems: List<CartItem>,
    total: Int,
    orderRef: String,
    response: TerminalSaleResponse
): FullReceipt {
    // Use the breakdown from the terminal when present; fall back to the
    // cart total. Base amount drives the VAT calculation (tip sits on top
    // and is not VATable). Total drives the bottom-line figure so the
    // receipt matches what was actually charged to the card.
    val baseFromTerminal = response.baseAmountPence.takeIf { it > 0 }
    val totalFromTerminal = response.totalAmountPence.takeIf { it > 0 } ?: total
    val tip = response.tipAmountPence

    val cartBase = baseFromTerminal ?: total
    val subtotal = (cartBase / 1.2).roundToInt()
    val vatAmount = cartBase - subtotal

    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK)

    return FullReceipt(
        merchantName = "Path Café",
        merchantAddress = "1 Tech Street, London EC1A 1BB",
        orderNumber = orderRef,
        tillNumber = "01",
        cashierName = "Cashier",
        orderDate = dateFormat.format(Date()),
        lineItems = cartItems.map {
            ReceiptLineItem(it.product.name, it.quantity, it.product.price)
        },
        subtotal = subtotal,
        vatAmount = vatAmount,
        total = totalFromTerminal,
        currency = "GBP",
        cardReceiptBlock = response.cardReceiptData?.let { CardReceiptBlock.from(it) },
        tipAmount = tip
    )
}
