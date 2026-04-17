package tech.path2ai.epos.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.path2ai.epos.R
import tech.path2ai.epos.email.SMTPClient
import tech.path2ai.epos.email.SMTPConfig
import tech.path2ai.epos.models.FullReceipt
import tech.path2ai.epos.ui.theme.OCGreen
import java.io.File
import java.io.FileOutputStream

/**
 * Full-screen receipt dialog shown after a card payment is approved.
 * Mirrors iOS ReceiptView — authorised banner, receipt content,
 * Email / Print / No Receipt / Done actions.
 */
@Composable
fun ReceiptDialog(
    receipt: FullReceipt,
    onNoReceipt: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEmailSheet by remember { mutableStateOf(false) }
    var showPrintPreview by remember { mutableStateOf(false) }

    if (showEmailSheet) {
        EmailReceiptDialog(
            receipt = receipt,
            onDismiss = { showEmailSheet = false },
            onSent = { showEmailSheet = false }
        )
    }

    if (showPrintPreview) {
        PrintPreviewDialog(
            receipt = receipt,
            onDismiss = { showPrintPreview = false }
        )
    }

    Dialog(
        onDismissRequest = { /* non-dismissable */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Authorised banner ────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OCGreen)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Payment Authorised", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                }

                // ── Scrollable receipt content ───────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    ReceiptContent(
                        receipt = receipt,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    )
                }

                // ── Action buttons ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEmailSheet = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OCGreen),
                            border = BorderStroke(1.dp, OCGreen)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Email receipt", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        OutlinedButton(
                            onClick = { showPrintPreview = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OCGreen),
                            border = BorderStroke(1.dp, OCGreen)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print receipt", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    OutlinedButton(
                        onClick = onNoReceipt,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OCGreen),
                        border = BorderStroke(1.dp, OCGreen)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("No receipt", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }

                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ─── Print preview dialog (with back button) ──────────────────────────────────

@Composable
fun PrintPreviewDialog(
    receipt: FullReceipt,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var printError by remember { mutableStateOf<String?>(null) }
    var printing by remember { mutableStateOf(false) }

    // Render the PDF to a bitmap for the preview
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pdfBytes = ReceiptRenderer.generatePdfBytes(receipt, context)
                val file = File(context.cacheDir, "receipt_preview.pdf")
                file.writeBytes(pdfBytes)
                val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val page = renderer.openPage(0)
                val scale = 3 // render at 3× for crisp preview
                val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                fd.close()
                previewBitmap = bmp
            } catch (_: Exception) { /* preview unavailable; still allow printing */ }
        }
    }

    printError?.let { msg ->
        AlertDialog(
            onDismissRequest = { printError = null },
            title = { Text("Print failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { printError = null }) { Text("OK") } }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header bar with back button ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Print Preview",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                // ── Receipt preview ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Receipt preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(16.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OCGreen)
                        }
                    }
                }

                HorizontalDivider()

                // ── Print / Back buttons ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            printing = true
                            try {
                                sendToPrinter(context, receipt)
                                onDismiss()
                            } catch (e: Exception) {
                                printing = false
                                printError = e.message ?: "Could not open print dialog"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = OCGreen),
                        enabled = !printing
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Email receipt dialog ─────────────────────────────────────────────────────

@Composable
fun EmailReceiptDialog(
    receipt: FullReceipt,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var emailAddress by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { if (isSuccess) onSent() else resultMessage = null },
            title = { Text("Email receipt") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { if (isSuccess) onSent() else resultMessage = null }) { Text("OK") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Email receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the customer's email address:", fontSize = 14.sp, color = Color.Gray)
                OutlinedTextField(
                    value = emailAddress,
                    onValueChange = { emailAddress = it },
                    label = { Text("Customer email") },
                    placeholder = { Text("customer@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isSending,
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) }
                )
                if (isSending) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OCGreen)
                        Spacer(Modifier.width(10.dp))
                        Text("Sending…", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                val config = remember { SMTPConfig.load(context) }
                if (!config.isConfigured) {
                    Text(
                        "⚠ SMTP not configured — go to Settings → Email Settings first.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val addr = emailAddress.trim()
                    if (addr.isEmpty() || isSending) return@TextButton
                    isSending = true
                    scope.launch {
                        try {
                            val config = SMTPConfig.load(context)
                            val pdf = ReceiptRenderer.generatePdfBytes(receipt, context)
                            SMTPClient().send(
                                config = config,
                                to = addr,
                                subject = "Your receipt from ${receipt.merchantName}",
                                pdfBytes = pdf
                            )
                            isSending = false
                            isSuccess = true
                            resultMessage = "Receipt sent to $addr."
                        } catch (e: Exception) {
                            isSending = false
                            isSuccess = false
                            resultMessage = "Failed: ${e.message}"
                        }
                    }
                },
                enabled = emailAddress.trim().isNotEmpty() && !isSending
            ) {
                Text("Send", color = OCGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        }
    )
}

// ─── Send to system printer ───────────────────────────────────────────────────

private fun sendToPrinter(context: Context, receipt: FullReceipt) {
    val pdfBytes = ReceiptRenderer.generatePdfBytes(receipt, context)
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(
        "Receipt — ${receipt.merchantName}",
        object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback?.onLayoutCancelled(); return }
                callback?.onLayoutFinished(
                    PrintDocumentInfo.Builder("receipt.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1).build(), true
                )
            }
            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback?.onWriteCancelled(); return }
                try {
                    FileOutputStream(destination?.fileDescriptor).use { it.write(pdfBytes) }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }, null
    )
}

// ── Receipt content ──────────────────────────────────────────────────────────

@Composable
fun ReceiptContent(
    receipt: FullReceipt,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        // ── Merchant logo ────────────────────────────────────────────────────
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.path_cafe_logo),
            contentDescription = receipt.merchantName,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
            contentScale = ContentScale.Fit
        )

        // Merchant address + order details
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(receipt.merchantAddress, fontSize = 13.sp, color = OCGreen.copy(alpha = 0.9f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Order: ${receipt.orderNumber}", fontSize = 11.sp, color = OCGreen.copy(alpha = 0.8f))
                Text("·", fontSize = 11.sp, color = OCGreen.copy(alpha = 0.8f))
                Text("Till: ${receipt.tillNumber}", fontSize = 11.sp, color = OCGreen.copy(alpha = 0.8f))
                Text("·", fontSize = 11.sp, color = OCGreen.copy(alpha = 0.8f))
                Text(receipt.cashierName, fontSize = 11.sp, color = OCGreen.copy(alpha = 0.8f))
            }
            Text(receipt.orderDate, fontSize = 11.sp, color = Color.Gray)
        }

        ReceiptDivider()

        // Line items
        receipt.lineItems.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${item.quantity} × ${item.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("£${"%.2f".format(item.lineTotal / 100.0)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        ReceiptDivider()

        ReceiptAmountRow("Subtotal", receipt.subtotal, bold = false)
        ReceiptAmountRow("VAT (20%)", receipt.vatAmount, bold = false)
        // Tip row only renders when the customer actually added a tip — keeps
        // the receipt tidy on cash / tipping-off sales.
        if (receipt.tipAmount > 0) {
            ReceiptAmountRow("Tip", receipt.tipAmount, bold = false)
        }
        ReceiptAmountRow("TOTAL", receipt.total, bold = true, color = OCGreen)

        receipt.cardReceiptBlock?.let { card ->
            ReceiptDivider()
            Text("PAYMENT", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp))
            ReceiptCardLine(card.status)
            ReceiptCardLine(card.timestamp)
            ReceiptCardLine(card.txnRef)
            ReceiptCardLine("Terminal ID: ${card.terminalId}")
            ReceiptCardLine("Merchant ID: ${card.merchantId}")
            ReceiptCardLine("Authorization: ${card.authorisationCode}")
            ReceiptCardLine("Verification: ${card.verificationMethod}")
            ReceiptCardLine("AID: ${card.aid}")
            ReceiptCardLine("Entry Mode: ${card.entryMode}")
            ReceiptCardLine("Account: ${card.maskedPan}")
            ReceiptCardLine("Card: ${card.cardScheme}")
        }

        ReceiptDivider()

        receipt.footerLines.forEach { line ->
            Text(line, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ReceiptDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = OCGreen.copy(alpha = 0.35f), thickness = 1.dp)
}

@Composable
private fun ReceiptAmountRow(label: String, amountPence: Int, bold: Boolean, color: Color = LocalContentColor.current) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (bold) 4.dp else 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = if (bold) 15.sp else 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
        Text("£${"%.2f".format(amountPence / 100.0)}", fontSize = if (bold) 15.sp else 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
private fun ReceiptCardLine(text: String) {
    Text(text, fontSize = 11.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 1.dp))
}
