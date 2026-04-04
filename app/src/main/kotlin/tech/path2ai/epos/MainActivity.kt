package tech.path2ai.epos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tech.path2ai.epos.managers.InventoryManager
import tech.path2ai.epos.managers.OrderManager
import tech.path2ai.epos.terminal.AppTerminalManager
import tech.path2ai.epos.terminal.SDKTerminalManager
import tech.path2ai.epos.ui.screens.SplashScreen
import tech.path2ai.epos.ui.theme.OrderchampionEPOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sdkManager = SDKTerminalManager(applicationContext)
        val terminalManager = AppTerminalManager(sdkManager)
        val orderManager = OrderManager(applicationContext)
        val inventoryManager = InventoryManager()

        setContent {
            OrderchampionEPOSTheme {
                SplashScreen(
                    terminalManager = terminalManager,
                    orderManager = orderManager,
                    inventoryManager = inventoryManager
                )
            }
        }
    }
}
