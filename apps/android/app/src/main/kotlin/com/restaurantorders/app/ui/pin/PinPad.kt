package com.restaurantorders.app.ui.pin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.ui.theme.Ink
import com.restaurantorders.app.ui.userMessage
import com.restaurantorders.domain.Permission
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private val KEYS = listOf("7", "8", "9", "4", "5", "6", "1", "2", "3", "", "0", "⌫")

/**
 * Four dots and a keypad laid out like Front's. The 4th digit submits at once; a wrong PIN shakes
 * the dots and clears them. While the pad is locked (too many wrong PINs) it counts down.
 */
@Composable
fun PinPad(app: AppContainer, hint: String, keySize: Dp, onPin: suspend (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var wrong by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    val lockedUntil by app.staff.lockedUntil.collectAsState()
    var secondsLeft by remember { mutableStateOf(0L) }

    LaunchedEffect(lockedUntil) {
        val until = lockedUntil ?: return@LaunchedEffect
        while (true) {
            secondsLeft = Duration.between(Instant.now(), until).seconds.coerceAtLeast(0)
            if (secondsLeft == 0L) break
            delay(500)
        }
        error = null
    }
    LaunchedEffect(wrong) {
        if (wrong == 0) return@LaunchedEffect
        shake.animateTo(0f, keyframes { durationMillis = 360; -14f at 60; 14f at 120; -10f at 180; 10f at 240; 0f at 360 })
    }
    LaunchedEffect(pin) {
        if (pin.length < 4) return@LaunchedEffect
        checking = true
        try {
            onPin(pin)
        } catch (e: Exception) {
            error = e.userMessage()
            wrong++
        } finally {
            pin = ""
            checking = false
        }
    }
    val isLocked = secondsLeft > 0

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(18.dp)
                        .border(2.dp, Ink, CircleShape)
                        .background(if (i < pin.length) Ink else MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Text(
            when {
                isLocked -> "Too many wrong PINs. Try again in $secondsLeft s"
                error != null -> error!!
                checking -> "Checking…"
                else -> hint
            },
            color = if (error != null || isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
                                enabled = !checking && !isLocked,
                                modifier = Modifier.size(keySize),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(key, fontSize = (keySize.value * 0.38f).sp) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Asks for a manager's PIN so the signed-in cashier can do one risky thing (`what`), and hands
 * back the manager who approved it. The tablet stays signed in as the cashier.
 */
@Composable
fun ApprovalDialog(app: AppContainer, permission: Permission, what: String, onApproved: (Staff) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manager approval", style = MaterialTheme.typography.titleLarge)
                Text(what, textAlign = TextAlign.Center)
                PinPad(app, hint = "A manager or owner enters their PIN", keySize = 64.dp) { pin ->
                    onApproved(app.staff.approve(pin, permission))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
