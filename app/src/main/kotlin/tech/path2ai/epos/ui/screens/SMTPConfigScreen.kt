package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.path2ai.epos.email.SMTPConfig
import tech.path2ai.epos.email.SMTPConfigData
import tech.path2ai.epos.ui.theme.OCGreen

/**
 * Embeddable SMTP configuration form — mirrors iOS SMTPConfigView.
 * Used in the Settings sidebar "Email Settings" pane.
 */
@Composable
fun SMTPConfigContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saved0 = remember { SMTPConfig.load(context) }

    var host      by remember { mutableStateOf(saved0.host) }
    var portText  by remember { mutableStateOf(saved0.port.toString()) }
    var useTls    by remember { mutableStateOf(saved0.useTls) }
    var username  by remember { mutableStateOf(saved0.username) }
    var password  by remember { mutableStateOf(saved0.password) }
    var fromEmail by remember { mutableStateOf(saved0.fromEmail) }
    var fromName  by remember { mutableStateOf(saved0.fromName) }
    var showPassword by remember { mutableStateOf(false) }
    var saved     by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Server ──────────────────────────────────────────────────────────
        SMTPSection(title = "Server", footer = "Port 587 with STARTTLS (e.g. Fasthosts Livemail: smtp.livemail.co.uk)") {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; saved = false },
                label = { Text("Host") },
                placeholder = { Text("smtp.livemail.co.uk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it; saved = false },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use TLS", fontSize = 14.sp)
                    Switch(
                        checked = useTls,
                        onCheckedChange = { useTls = it; saved = false },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OCGreen,
                            checkedTrackColor = OCGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // ── Authentication ───────────────────────────────────────────────────
        SMTPSection(title = "Authentication") {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; saved = false },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; saved = false },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                }
            )
        }

        // ── From ────────────────────────────────────────────────────────────
        SMTPSection(title = "From") {
            OutlinedTextField(
                value = fromEmail,
                onValueChange = { fromEmail = it; saved = false },
                label = { Text("From email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) }
            )
            OutlinedTextField(
                value = fromName,
                onValueChange = { fromName = it; saved = false },
                label = { Text("From name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
            )
        }

        // ── Save ────────────────────────────────────────────────────────────
        Button(
            onClick = {
                val port = portText.toIntOrNull() ?: 587
                SMTPConfig.save(
                    context,
                    SMTPConfigData(
                        host = host.trim(),
                        port = port,
                        useTls = useTls,
                        username = username.trim(),
                        password = password,
                        fromEmail = fromEmail.trim(),
                        fromName = fromName.trim()
                    )
                )
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
        ) {
            Icon(
                if (saved) Icons.Default.Check else Icons.Default.Save,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (saved) "Saved ✓" else "Save Settings", fontWeight = FontWeight.SemiBold)
        }

        if (host.isBlank()) {
            Text(
                "Enter your SMTP server details above to enable direct email sending from the receipt screen.",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SMTPSection(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.8.sp
            )
            content()
            if (footer != null) {
                Text(footer, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
            }
        }
    }
}
