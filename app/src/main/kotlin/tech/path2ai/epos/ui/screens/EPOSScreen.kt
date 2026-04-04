package tech.path2ai.epos.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.path2ai.epos.managers.InventoryManager
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.models.*
import tech.path2ai.epos.terminal.*
import tech.path2ai.epos.ui.theme.*

@Composable
fun EPOSScreen(
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    inventoryManager: InventoryManager,
    onNavigateToSettings: () -> Unit
) {
    var cartItems by remember { mutableStateOf(listOf<CartItem>()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showPaymentSheet by remember { mutableStateOf(false) }

    val connectionState by terminalManager.connectionState.collectAsState()
    val products = inventoryManager.productsIn(selectedCategory)
    val cartTotal = cartItems.sumOf { it.lineTotal }

    Row(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Product Grid (75%) ──
        Column(modifier = Modifier.weight(0.75f).fillMaxHeight()) {
            // Header
            Surface(shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // OrderChampion logo mark — red diamond with white cutout
                    OrderChampionLogoMark()
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "OrderChampion",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "EPOS Solutions",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            lineHeight = 10.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    // Terminal status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    is TerminalConnectionState.Connected -> Color(0xFF4CAF50)
                                    is TerminalConnectionState.Connecting -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }

            // Category chips (below header surface)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                inventoryManager.categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 13.sp) }
                    )
                }
            }

            // Product grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(products) { product ->
                    ProductCard(product = product) {
                        val existing = cartItems.find { it.product.id == product.id }
                        cartItems = if (existing != null) {
                            cartItems.map {
                                if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                            }
                        } else {
                            cartItems + CartItem(product = product, quantity = 1)
                        }
                    }
                }
            }
        }

        // ── Cart (25%) ──
        Surface(
            modifier = Modifier.weight(0.25f).fillMaxHeight(),
            shadowElevation = 4.dp,
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cart header — aligned with product header
                Surface(shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .defaultMinSize(minHeight = 50.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Order", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { cartItems = emptyList() },
                            enabled = cartItems.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear cart",
                                modifier = Modifier.size(18.dp),
                                tint = if (cartItems.isNotEmpty()) Color(0xFFF44336) else Color.LightGray
                            )
                        }
                    }
                }
                HorizontalDivider()

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                if (cartItems.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No items", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(cartItems) { item ->
                            CartRow(
                                item = item,
                                onIncrement = {
                                    cartItems = cartItems.map {
                                        if (it.id == item.id) it.copy(quantity = it.quantity + 1) else it
                                    }
                                },
                                onDecrement = {
                                    if (item.quantity <= 1) {
                                        cartItems = cartItems.filter { it.id != item.id }
                                    } else {
                                        cartItems = cartItems.map {
                                            if (it.id == item.id) it.copy(quantity = it.quantity - 1) else it
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("£%.2f".format(cartTotal / 100.0), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showPaymentSheet = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = cartItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = OCGreen)
                ) {
                    Text("Pay Now", fontWeight = FontWeight.Bold)
                }
                }
            }
        }
    }

    if (showPaymentSheet) {
        PaymentScreen(
            cartItems = cartItems,
            total = cartTotal,
            terminalManager = terminalManager,
            orderManager = orderManager,
            onDismiss = { showPaymentSheet = false },
            onPaymentComplete = {
                cartItems = emptyList()
                showPaymentSheet = false
            }
        )
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(categoryIconColor(product.category).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = productIcon(product.symbolName),
                    contentDescription = null,
                    tint = categoryIconColor(product.category),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(product.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
            Text("£%.2f".format(product.price / 100.0), fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun CartRow(item: CartItem, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = productIcon(item.product.symbolName),
            contentDescription = null,
            tint = categoryIconColor(item.product.category),
            modifier = Modifier.size(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Qty: ${item.quantity}", fontSize = 12.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("£%.2f".format(item.lineTotal / 100.0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = "Remove",
                    tint = OCRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun OrderChampionLogoMark(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Red diamond (26x26 square rotated 45 degrees)
            val diamondSize = 26.dp.toPx()
            rotate(degrees = 45f, pivot = center) {
                drawRect(
                    color = Color(0xFFE84525),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - diamondSize / 2f, cy - diamondSize / 2f),
                    size = androidx.compose.ui.geometry.Size(diamondSize, diamondSize)
                )
            }
            // White cutout (10x10 square rotated 45 degrees)
            val cutoutSize = 10.dp.toPx()
            rotate(degrees = 45f, pivot = center) {
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - cutoutSize / 2f, cy - cutoutSize / 2f),
                    size = androidx.compose.ui.geometry.Size(cutoutSize, cutoutSize)
                )
            }
        }
    }
}

private fun productIcon(symbolName: String): ImageVector = when (symbolName) {
    "local_cafe" -> Icons.Default.LocalCafe
    "ac_unit" -> Icons.Default.AcUnit
    "wine_bar" -> Icons.Default.WineBar
    "water_drop" -> Icons.Default.WaterDrop
    "bubble_chart" -> Icons.Default.BubbleChart
    "bakery_dining" -> Icons.Default.BakeryDining
    "restaurant" -> Icons.Default.Restaurant
    "dining" -> Icons.Default.Dining
    "spa" -> Icons.Default.Spa
    "favorite" -> Icons.Default.Favorite
    "star" -> Icons.Default.Star
    "eco" -> Icons.Default.Eco
    "shopping_bag" -> Icons.Default.ShoppingBag
    "cookie" -> Icons.Default.Cookie
    else -> Icons.Default.ShoppingCart
}
