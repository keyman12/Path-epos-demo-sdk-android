package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.OCGreen

@Composable
fun PaymentScreen(
    cartItems: List<CartItem>,
    total: Int,
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    onDismiss: () -> Unit,
    onPaymentComplete: () -> Unit
) {
    var showCardPayment by remember { mutableStateOf(false) }
    var showCashPayment by remember { mutableStateOf(false) }

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

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}
