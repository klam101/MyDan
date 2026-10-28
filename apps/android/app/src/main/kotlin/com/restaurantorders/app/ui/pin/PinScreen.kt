package com.restaurantorders.app.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.ui.theme.Ink
import com.restaurantorders.app.ui.userMessage

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")

/** The tablet's lock screen: whoever enters their PIN takes the next orders. */
@Composable
fun PinScreen(app: AppContainer, restaurantName: String) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length < 4) return@LaunchedEffect
        checking = true
        try {
            app.staff.signIn(pin)
        } catch (e: Exception) {
            error = e.userMessage()
            pin = ""
        } finally {
            checking = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Keys shrink on short screens (a small tablet held sideways) so all four rows fit.
        val keySize = ((maxHeight - 260.dp) / 4 - 10.dp).coerceIn(48.dp, 80.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(restaurantName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Enter your PIN", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(18.dp)
                            .border(2.dp, Ink, CircleShape)
                            .background(if (i < pin.length) Ink else MaterialTheme.colorScheme.background, CircleShape),
                    )
                }
            }
            Text(
                error ?: if (checking) "Checking…" else "Whoever enters their PIN takes the next orders.",
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KEYS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Box(Modifier.size(keySize))
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        error = null
                                        pin = if (key == "⌫") pin.dropLast(1) else (pin + key).take(4)
                                    },
                                    enabled = !checking,
                                    modifier = Modifier.size(keySize),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text(key, fontSize = (keySize.value * 0.38f).sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
