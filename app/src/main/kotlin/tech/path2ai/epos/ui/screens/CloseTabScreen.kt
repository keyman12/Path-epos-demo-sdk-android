package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.PointOfSale
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.managers.TabManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Recall a tab and settle it. Reconciliation:
 *   hold already released -> standard sale (a retry after a prior over-close void)
 *   bill == 0             -> void (release), nothing charged
 *   bill <= hold          -> complete (capture) the pre-auth for the actual bill
 *   bill  > hold          -> void the hold + run a standard card sale for the bill
 * A charge (capture or sale) routes through the terminal and back, then the
 * receipt screen is shown so the customer can choose a receipt. The settlement is
 * recorded as a CompletedOrder (tab name + how it settled) for Order History.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseTabScreen(
    tabId: String,
    terminalManager: AppTerminalManager,
    tabManager: TabManager,
    orderManager: OrderManager,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val tabs by tabManager.tabs.collectAsState()
    val tab = tabs.firstOrNull { it.id == tabId }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var receiptToShow by remember { mutableStateOf<FullReceipt?>(null) }
    var releasedMessage by remember { mutableStateOf<String?>(null) }

    fun money(p: Int) = "£%.2f".format(p / 100.0)

    // Receipt screen — customer elects to take a receipt (or not). Either way we're done.
    receiptToShow?.let { receipt ->
        ReceiptDialog(receipt = receipt, onNoReceipt = onDone, onDone = onDone)
        return
    }
    // Released / nothing-charged confirmation.
    releasedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Tab closed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = onDone) { Text("Done") } }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab?.let { "Close ${it.name}'s tab" } ?: "Close tab") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (tab == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tab not found (already closed?)", color = Color.Gray)
            }
            return@Scaffold
        }

        val bill = tab.totalPence
        val hold = tab.preAuthPence

        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Hold", Modifier.weight(1f), color = Color.Gray)
                        Text(money(hold))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Bill total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(money(bill), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    val plan = when {
                        tab.holdReleased -> "Hold already released — will charge a new card sale."
                        bill == 0 -> "Nothing on the tab — the hold will be released."
                        bill <= hold -> "Within the hold — will capture ${money(bill)} and release the rest."
                        else -> "Over the hold — will release it and take a new card sale for ${money(bill)}."
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(plan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tab.lineItems.isEmpty()) item { Text("No items added.", color = Color.Gray) }
                items(tab.lineItems) { li ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${li.quantity}× ${li.name}", Modifier.weight(1f))
                        Text(money(li.lineTotal))
                    }
                }
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = OCGreen),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            when (val r = settle(tab.id, terminalManager, tabManager, orderManager)) {
                                is SettleResult.Charged -> {
                                    receiptToShow = buildTabReceipt(
                                        terminalManager, tabManager.tabById(tab.id) ?: tab,
                                        r.reference, r.typeLabel
                                    )
                                }
                                is SettleResult.Released -> releasedMessage = r.message
                                is SettleResult.Failed -> message = r.message
                            }
                        } catch (e: Exception) {
                            message = "Error: ${e.message}"
                        } finally { busy = false }
                    }
                }
            ) {
                Icon(Icons.Default.PointOfSale, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Close & settle", fontSize = 16.sp)
            }
        }
    }
}

private sealed interface SettleResult {
    data class Charged(val reference: String?, val typeLabel: String) : SettleResult
    data class Released(val message: String) : SettleResult
    data class Failed(val message: String) : SettleResult
}

/** Run the reconciliation against the live tab, record + close on success. */
private suspend fun settle(
    tabId: String,
    terminalManager: AppTerminalManager,
    tabManager: TabManager,
    orderManager: OrderManager
): SettleResult {
    val tab = tabManager.tabById(tabId) ?: return SettleResult.Failed("Tab not found.")
    val bill = tab.totalPence
    val hold = tab.preAuthPence
    val ref = tab.terminalReference
    val orderRef = orderManager.generateReference()

    fun record(reference: String?, kind: String) = orderManager.recordSale(
        orderReference = orderRef,
        lineItems = tab.lineItems,
        amountPence = bill,
        currencyCode = tab.currencyCode,
        paymentMethod = PaymentMethod.CARD,
        terminalReference = reference,
        tabName = tab.name,
        settlementKind = kind
    )

    suspend fun standardSale(): SettleResult {
        val resp = terminalManager.submitSale(
            TerminalSaleRequest(
                orderReference = orderRef,
                amountPence = bill,
                currencyCode = tab.currencyCode,
                lineItems = tab.lineItems.map { TerminalLineItem(it.name, it.quantity, it.unitPrice) },
                operatorId = "till-01",
                promptForTip = false
            )
        )
        if (!resp.authorised) return SettleResult.Failed("Card sale failed: ${resp.failureReason}")
        record(resp.terminalReference, "Sale (over hold)")
        tabManager.closeTab(tabId, orderRef)
        return SettleResult.Charged(resp.terminalReference, "Sale (tab settlement)")
    }

    return when {
        tab.holdReleased -> standardSale()
        bill == 0 -> {
            val v = terminalManager.submitVoidPreAuth(TerminalPreAuthVoidRequest(ref ?: "", "tab:${tab.name}"))
            if (v.succeeded) { tabManager.closeTab(tabId, null); SettleResult.Released("Hold released — nothing charged.") }
            else SettleResult.Failed("Couldn't release the hold: ${v.failureReason}")
        }
        bill <= hold -> {
            val c = terminalManager.submitCompletePreAuth(
                TerminalPreAuthCompleteRequest(ref ?: "", bill, tab.currencyCode, "tab:${tab.name}")
            )
            if (!c.succeeded) SettleResult.Failed("Capture failed: ${c.failureReason}")
            else { record(c.terminalReference, "Pre-auth completion"); tabManager.closeTab(tabId, orderRef); SettleResult.Charged(c.terminalReference, "Pre-auth completion") }
        }
        else -> {
            val v = terminalManager.submitVoidPreAuth(TerminalPreAuthVoidRequest(ref ?: "", "tab:${tab.name}"))
            if (!v.succeeded) return SettleResult.Failed("Couldn't release the hold to re-charge: ${v.failureReason}")
            tabManager.markHoldReleased(tabId)   // hold gone — a retry must skip the void
            standardSale()
        }
    }
}

/** Build the settlement receipt, fetching the card block from the terminal. */
private suspend fun buildTabReceipt(
    terminalManager: AppTerminalManager,
    tab: Tab,
    reference: String?,
    typeLabel: String
): FullReceipt {
    val cardBlock = reference?.let { r ->
        try {
            terminalManager.getReceiptData(r)?.customerReceipt?.let { f ->
                CardReceiptBlock(
                    status = f.status, timestamp = f.timestamp, txnRef = f.txnRef,
                    terminalId = f.terminalId, merchantId = f.merchantId,
                    authorisationCode = f.authCode, verificationMethod = f.verification,
                    aid = f.aid, entryMode = f.entryMode, maskedPan = f.maskedPan, cardScheme = f.cardScheme
                )
            }
        } catch (_: Exception) { null }
    }
    val bill = tab.totalPence
    val subtotal = (bill / 1.2).roundToInt()
    val vat = bill - subtotal
    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK)
    return FullReceipt(
        merchantName = "Path Café",
        merchantAddress = "1 Tech Street, London EC1A 1BB",
        orderNumber = tab.closeOrderReference ?: tab.id.take(8),
        tillNumber = "01",
        cashierName = "Cashier",
        orderDate = dateFormat.format(Date()),
        lineItems = tab.lineItems.map { ReceiptLineItem(it.name, it.quantity, it.unitPrice) },
        subtotal = subtotal,
        vatAmount = vat,
        total = bill,
        currency = tab.currencyCode,
        cardReceiptBlock = cardBlock,
        transactionTypeLabel = typeLabel,
        tabName = tab.name
    )
}
