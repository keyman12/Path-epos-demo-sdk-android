package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.ui.theme.OCGreen

@Composable
fun CashPaymentScreen(
    cartItems: List<CartItem>,
    total: Int,
    orderManager: OrderManager,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var tenderedText by remember { mutableStateOf("") }
    val tenderedPence = ((tenderedText.toDoubleOrNull() ?: 0.0) * 100).toInt()
    val changeDue = tenderedPence - total

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.4f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Cash Payment", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text("Amount Due: £%.2f".format(total / 100.0), fontSize = 16.sp)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = tenderedText,
                    onValueChange = { tenderedText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount Tendered (£)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (tenderedPence > 0 && tenderedPence >= total) {
                    Spacer(Modifier.height(8.dp))
                    Text("Change: £%.2f".format(changeDue / 100.0), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OCGreen)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        orderManager.recordSale(
                            orderReference = orderManager.generateReference(),
                            lineItems = cartItems.map { OrderLineItem(it.product.name, it.quantity, it.product.price) },
                            amountPence = total,
                            currencyCode = "GBP",
                            paymentMethod = PaymentMethod.CASH
                        )
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tenderedPence >= total,
                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                ) {
                    Text("Complete Transaction")
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
