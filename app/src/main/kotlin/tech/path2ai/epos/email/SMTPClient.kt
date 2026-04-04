package tech.path2ai.epos.email

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Writer
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private const val TAG = "SMTPClient"
private const val TIMEOUT_MS = 30_000

/**
 * Minimal SMTP client with STARTTLS support.
 * Mirrors iOS ReceiptEmailSender — same protocol flow, same log format.
 * Runs on Dispatchers.IO; caller should launch from a coroutine scope.
 */
class SMTPClient {

    /**
     * Send a receipt PDF to [to] via SMTP using [config].
     * Throws [IOException] or [IllegalStateException] on failure; logs each step via [onLog].
     */
    suspend fun send(
        config: SMTPConfigData,
        to: String,
        subject: String,
        pdfBytes: ByteArray,
        onLog: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {

        if (!config.isConfigured) {
            throw IllegalStateException(
                "SMTP is not configured. Add server details in Settings → Email Settings."
            )
        }

        val socket = Socket()
        try {
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress(config.host, config.port), TIMEOUT_MS)

            var reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            var out: Writer = socket.outputStream.writer(Charsets.UTF_8)

            // ── Helpers ─────────────────────────────────────────────────────

            /** Read one (possibly multi-line) SMTP response; return the final line. */
            fun readResponse(): String {
                var last = ""
                do {
                    val line = reader.readLine() ?: throw IOException("Connection closed by server")
                    onLog?.invoke("< $line")
                    Log.d(TAG, "< $line")
                    last = line
                    // Multi-line response: code followed by '-', e.g. "250-SIZE 10240000"
                } while (last.length > 3 && last[3] == '-')
                return last
            }

            /** Write a command line (CRLF terminated). Use [logged] to redact credentials. */
            fun cmd(line: String, logged: String = line) {
                onLog?.invoke("> $logged")
                Log.d(TAG, "> $logged")
                out.write("$line\r\n")
                out.flush()
            }

            /** Verify the response starts with [code], throw otherwise. */
            fun expect(code: String): String {
                val r = readResponse()
                if (!r.startsWith(code)) throw IOException("Expected $code, got: $r")
                return r
            }

            // ── SMTP session ─────────────────────────────────────────────────

            // 1. Server greeting
            expect("220")

            // 2. EHLO
            cmd("EHLO path2ai.tech")
            expect("250")

            // 3. STARTTLS
            if (config.useTls) {
                cmd("STARTTLS")
                expect("220")

                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(socket, config.host, config.port, true) as SSLSocket
                sslSocket.startHandshake()
                onLog?.invoke("[TLS handshake OK]")
                Log.d(TAG, "[TLS handshake OK]")

                reader = BufferedReader(InputStreamReader(sslSocket.inputStream, Charsets.UTF_8))
                out = sslSocket.outputStream.writer(Charsets.UTF_8)

                // Re-EHLO after TLS upgrade
                cmd("EHLO path2ai.tech")
                expect("250")
            }

            // 4. AUTH LOGIN
            if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
                cmd("AUTH LOGIN")
                expect("334")
                cmd(Base64.encodeToString(config.username.toByteArray(), Base64.NO_WRAP), "***")
                expect("334")
                cmd(Base64.encodeToString(config.password.toByteArray(), Base64.NO_WRAP), "***")
                expect("235")
            }

            // 5. Envelope
            cmd("MAIL FROM:<${config.fromEmail}>")
            expect("250")
            cmd("RCPT TO:<$to>")
            expect("250")

            // 6. Message data
            cmd("DATA")
            expect("354")

            val boundary = "PathReceipt_${System.currentTimeMillis()}"
            out.write(buildMimeMessage(config, to, subject, pdfBytes, boundary))
            out.write(".\r\n")
            out.flush()
            expect("250")

            // 7. Quit
            cmd("QUIT")

            onLog?.invoke("[SMTP send complete]")
            Log.i(TAG, "Send complete to=$to")

        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // ── MIME message builder ───────────────────────────────────────────────────

    private fun buildMimeMessage(
        config: SMTPConfigData,
        to: String,
        subject: String,
        pdfBytes: ByteArray,
        boundary: String
    ): String {
        val body = listOf(
            "Dear Customer,",
            "",
            "Thank you for visiting Path Cafe. As requested,",
            "please find an email copy of your receipt attached.",
            "",
            "Kind regards,",
            "Path Cafe"
        ).joinToString("\r\n")

        // Base64.DEFAULT wraps at 76 chars — correct for email attachments
        val pdfBase64 = Base64.encodeToString(pdfBytes, Base64.DEFAULT)

        return buildString {
            append("From: ${config.fromName} <${config.fromEmail}>\r\n")
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n")
            append("\r\n")
            // -- Text part
            append("--$boundary\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 7bit\r\n")
            append("\r\n")
            append("$body\r\n")
            append("\r\n")
            // -- PDF part
            append("--$boundary\r\n")
            append("Content-Type: application/pdf; name=\"receipt.pdf\"\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("Content-Disposition: attachment; filename=\"receipt.pdf\"\r\n")
            append("\r\n")
            append("$pdfBase64\r\n")
            append("--$boundary--\r\n")
        }
    }
}
