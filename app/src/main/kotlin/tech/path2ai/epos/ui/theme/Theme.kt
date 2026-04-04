package tech.path2ai.epos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OCGreen = Color(0xFF00A368)
val OCGreenDark = Color(0xFF1EB675)
val OCRed = Color(0xFFE84525)
val OCDark = Color(0xFF1B2A3B)
val CoffeeBrown = Color(0xFF6F4E37)
val CoolBlue = Color(0xFF0277BD)
val EarthyGreen = Color(0xFF558B2F)
val WarmOrange = Color(0xFFE65100)

private val LightColorScheme = lightColorScheme(
    primary = OCGreen,
    onPrimary = Color.White,
    secondary = OCDark,
    onSecondary = Color.White,
    error = OCRed,
    onError = Color.White,
    surface = Color.White,
    onSurface = OCDark,
    background = Color(0xFFF5F5F5),
    onBackground = OCDark
)

fun categoryIconColor(category: String): Color = when (category) {
    "Hot Drinks" -> CoffeeBrown
    "Cold Drinks" -> CoolBlue
    "Food" -> EarthyGreen
    "Snacks" -> WarmOrange
    else -> OCGreen
}

@Composable
fun OrderchampionEPOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
