package tech.path2ai.epos.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.path2ai.epos.R
import tech.path2ai.epos.managers.InventoryManager
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.AppTerminalManager

@Composable
fun SplashScreen(
    terminalManager: AppTerminalManager,
    orderManager: OrderManager,
    inventoryManager: InventoryManager
) {
    var showSplash by remember { mutableStateOf(true) }
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.88f,
        animationSpec = tween(700),
        label = "splash_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(700),
        label = "splash_alpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000)
        showSplash = false
    }

    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.path_logo),
                contentDescription = "Path Logo",
                modifier = Modifier
                    .width(240.dp)
                    .scale(scale)
                    .alpha(alpha)
            )
        }
    } else {
        AppNavigation(
            terminalManager = terminalManager,
            orderManager = orderManager,
            inventoryManager = inventoryManager
        )
    }
}
