package com.kartikeya.vcradio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RadioBlack = Color(0xFF080807)
val RadioInk = Color(0xFFF0E8DB)
val RadioDim = Color(0xFFA9A196)
val RadioAmber = Color(0xFFE0A95B)

private val RadioScheme = darkColorScheme(
    primary = RadioAmber,
    onPrimary = RadioBlack,
    background = RadioBlack,
    onBackground = RadioInk,
    surface = RadioBlack,
    onSurface = RadioInk,
)

@Composable
fun ViceCityRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RadioScheme, content = content)
}
