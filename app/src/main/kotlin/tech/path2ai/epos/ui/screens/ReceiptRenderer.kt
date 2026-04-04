package tech.path2ai.epos.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import tech.path2ai.epos.R
import tech.path2ai.epos.models.FullReceipt
import java.io.ByteArrayOutputStream

/**
 * Generates PDF bytes and plain text from a [FullReceipt].
 * Mirrors iOS ReceiptRenderer — 80mm receipt width, merchant logo at top.
 */
object ReceiptRenderer {

    /** ~80mm at 72 dpi */
    private const val PAGE_WIDTH = 226
    private const val MARGIN = 12f
    private const val FONT_SIZE = 9f
    private const val HEADER_FONT_SIZE = 11f
    private const val LINE_HEIGHT = 13f
    private const val LOGO_HEIGHT = 44f   // pts — same visual weight as iOS
    private val PATH_GREEN = Color.rgb(59, 159, 64)

    // ─── Plain text ────────────────────────────────────────────────────────────

    fun generatePlainText(receipt: FullReceipt): String {
        val w = 34
        val sb = StringBuilder()
        sb.appendLine(receipt.merchantName)
        sb.appendLine(receipt.merchantAddress)
        sb.appendLine("Order: ${receipt.orderNumber}  Till: ${receipt.tillNumber}  ${receipt.cashierName}")
        sb.appendLine(receipt.orderDate)
        sb.appendLine("-".repeat(w))
        for (item in receipt.lineItems) {
            sb.appendLine(priceRow("${item.quantity} x ${item.name}", "£${"%.2f".format(item.lineTotal / 100.0)}", w))
        }
        sb.appendLine("-".repeat(w))
        sb.appendLine(priceRow("Subtotal:", "£${"%.2f".format(receipt.subtotal / 100.0)}", w))
        sb.appendLine(priceRow("VAT (20%):", "£${"%.2f".format(receipt.vatAmount / 100.0)}", w))
        sb.appendLine(priceRow("TOTAL:", "£${"%.2f".format(receipt.total / 100.0)}", w))
        receipt.cardReceiptBlock?.let { card ->
            sb.appendLine("=".repeat(w))
            sb.appendLine("PAYMENT")
            sb.appendLine(card.status); sb.appendLine(card.timestamp); sb.appendLine(card.txnRef)
            sb.appendLine("Terminal ID: ${card.terminalId}"); sb.appendLine("Merchant ID: ${card.merchantId}")
            sb.appendLine("Authorization: ${card.authorisationCode}"); sb.appendLine("Verification: ${card.verificationMethod}")
            sb.appendLine("AID: ${card.aid}"); sb.appendLine("Entry Mode: ${card.entryMode}")
            sb.appendLine("Account: ${card.maskedPan}"); sb.appendLine("Card: ${card.cardScheme}")
        }
        sb.appendLine("-".repeat(w))
        for (line in receipt.footerLines) sb.appendLine(line)
        return sb.toString()
    }

    private fun priceRow(label: String, price: String, width: Int): String {
        val spaces = width - label.length - price.length
        return if (spaces > 0) label + " ".repeat(spaces) + price else "$label $price"
    }

    // ─── PDF ──────────────────────────────────────────────────────────────────

    fun generatePdfBytes(receipt: FullReceipt, context: Context): ByteArray {
        // Load logo bitmap (null-safe — receipt still renders without it)
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.path_cafe_logo)
        }.getOrNull()

        val lines = buildReceiptLines(receipt)

        // Page height: logo block + line content + margins
        val logoBlockHeight = if (logoBitmap != null) LOGO_HEIGHT + 8f else 0f
        val pageHeight = (MARGIN + logoBlockHeight + lines.size * LINE_HEIGHT + MARGIN + 8).toInt()

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create()
        val page = document.startPage(pageInfo)

        drawPage(page.canvas, lines, logoBitmap, logoBlockHeight)

        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun drawPage(
        canvas: Canvas,
        lines: List<ReceiptLine>,
        logoBitmap: android.graphics.Bitmap?,
        logoBlockHeight: Float
    ) {
        val paint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }

        var y = MARGIN

        // ── Logo ──────────────────────────────────────────────────────────────
        if (logoBitmap != null) {
            // Centre the logo horizontally, preserve aspect ratio within LOGO_HEIGHT
            val aspectRatio = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
            val logoW = (LOGO_HEIGHT * aspectRatio).coerceAtMost(PAGE_WIDTH - MARGIN * 2)
            val logoX = (PAGE_WIDTH - logoW) / 2f
            val destRect = RectF(logoX, y, logoX + logoW, y + LOGO_HEIGHT)
            canvas.drawBitmap(logoBitmap, null, destRect, Paint().apply { isAntiAlias = true; isFilterBitmap = true })
            y += LOGO_HEIGHT + 8f
        }

        // ── Text lines ────────────────────────────────────────────────────────
        y += FONT_SIZE   // first baseline

        for (line in lines) {
            when (line.style) {
                ReceiptLineStyle.HEADER -> paint.apply {
                    isFakeBoldText = true; textSize = HEADER_FONT_SIZE; color = PATH_GREEN
                }
                ReceiptLineStyle.SUBHEADER -> paint.apply {
                    isFakeBoldText = true; textSize = FONT_SIZE; color = PATH_GREEN
                }
                ReceiptLineStyle.TOTAL -> paint.apply {
                    isFakeBoldText = true; textSize = FONT_SIZE; color = PATH_GREEN
                }
                ReceiptLineStyle.NORMAL -> paint.apply {
                    isFakeBoldText = false; textSize = FONT_SIZE; color = Color.BLACK
                }
                ReceiptLineStyle.GRAY -> paint.apply {
                    isFakeBoldText = false; textSize = FONT_SIZE; color = Color.GRAY
                }
                ReceiptLineStyle.SEPARATOR -> paint.apply {
                    isFakeBoldText = false; textSize = FONT_SIZE; color = Color.LTGRAY
                }
            }

            canvas.drawText(line.text, MARGIN, y, paint)
            line.rightText?.let { rt ->
                val rx = PAGE_WIDTH - MARGIN - paint.measureText(rt)
                canvas.drawText(rt, rx, y, paint)
            }
            y += LINE_HEIGHT
        }
    }

    // ─── Line model ───────────────────────────────────────────────────────────

    private enum class ReceiptLineStyle { HEADER, SUBHEADER, NORMAL, GRAY, TOTAL, SEPARATOR }

    private data class ReceiptLine(
        val text: String,
        val rightText: String? = null,
        val style: ReceiptLineStyle = ReceiptLineStyle.NORMAL
    )

    private fun buildReceiptLines(receipt: FullReceipt): List<ReceiptLine> {
        val lines = mutableListOf<ReceiptLine>()

        lines += ReceiptLine(receipt.merchantAddress, style = ReceiptLineStyle.SUBHEADER)
        lines += ReceiptLine("Order: ${receipt.orderNumber}", style = ReceiptLineStyle.GRAY)
        lines += ReceiptLine("Till: ${receipt.tillNumber}  ${receipt.cashierName}", style = ReceiptLineStyle.GRAY)
        lines += ReceiptLine(receipt.orderDate, style = ReceiptLineStyle.GRAY)
        lines += ReceiptLine("-".repeat(32), style = ReceiptLineStyle.SEPARATOR)

        for (item in receipt.lineItems) {
            lines += ReceiptLine("${item.quantity} x ${item.name}", rightText = "£${"%.2f".format(item.lineTotal / 100.0)}")
        }

        lines += ReceiptLine("-".repeat(32), style = ReceiptLineStyle.SEPARATOR)
        lines += ReceiptLine("Subtotal", rightText = "£${"%.2f".format(receipt.subtotal / 100.0)}", style = ReceiptLineStyle.GRAY)
        lines += ReceiptLine("VAT (20%)", rightText = "£${"%.2f".format(receipt.vatAmount / 100.0)}", style = ReceiptLineStyle.GRAY)
        lines += ReceiptLine("TOTAL", rightText = "£${"%.2f".format(receipt.total / 100.0)}", style = ReceiptLineStyle.TOTAL)

        receipt.cardReceiptBlock?.let { card ->
            lines += ReceiptLine("=".repeat(32), style = ReceiptLineStyle.SEPARATOR)
            lines += ReceiptLine("PAYMENT", style = ReceiptLineStyle.SUBHEADER)
            lines += ReceiptLine(card.status, style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine(card.timestamp, style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine(card.txnRef, style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Terminal: ${card.terminalId}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Merchant: ${card.merchantId}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Auth: ${card.authorisationCode}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Verification: ${card.verificationMethod}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("AID: ${card.aid}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Entry: ${card.entryMode}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Account: ${card.maskedPan}", style = ReceiptLineStyle.GRAY)
            lines += ReceiptLine("Card: ${card.cardScheme}", style = ReceiptLineStyle.GRAY)
        }

        lines += ReceiptLine("-".repeat(32), style = ReceiptLineStyle.SEPARATOR)
        for (line in receipt.footerLines) lines += ReceiptLine(line, style = ReceiptLineStyle.GRAY)

        return lines
    }
}
