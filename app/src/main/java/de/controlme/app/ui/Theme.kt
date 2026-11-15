package de.controlme.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material You: übernimmt die Farben vom Hintergrundbild des Pixel-Handys. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

val SuccessGreen = Color(0xFF1E8E3E)

/** Großer, gut treffbarer Knopf (mind. 72 dp hoch). */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    danger: Boolean = false,
) {
    val m = modifier.heightIn(min = 72.dp)
    val label: @Composable () -> Unit = {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
    }
    when {
        danger -> Button(
            onClick, m, shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { label() }
        secondary -> FilledTonalButton(onClick, m, shape = MaterialTheme.shapes.extraLarge) { label() }
        else -> Button(onClick, m, shape = MaterialTheme.shapes.extraLarge) { label() }
    }
}
