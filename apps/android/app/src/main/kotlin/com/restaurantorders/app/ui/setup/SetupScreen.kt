package com.restaurantorders.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.rememberAction
import java.time.ZoneId

/** First run on a new tablet: the restaurant and its owner. The starter menu is loaded too. */
@Composable
fun SetupScreen(app: AppContainer) {
    var name by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("8.875") }
    var timezone by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var owner by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val action = rememberAction()

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        OutlinedCard(Modifier.widthIn(max = 520.dp)) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Set up this tablet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Everything stays on this tablet. The menu starts from the printed menu; change it any time under Menu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Restaurant name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        tax, { tax = it }, Modifier.weight(1f), label = { Text("Sales tax %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(timezone, { timezone = it }, Modifier.weight(2f), label = { Text("Time zone") }, singleLine = true)
                }
                OutlinedTextField(owner, { owner = it }, Modifier.fillMaxWidth(), label = { Text("Owner's name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PinField(pin, { pin = it }, "Owner's 4-digit PIN", Modifier.weight(1f))
                    PinField(pin2, { pin2 = it }, "PIN again", Modifier.weight(1f))
                }
                action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
                Button(
                    onClick = {
                        action.run {
                            val rate = tax.toDoubleOrNull() ?: throw ActionException("Sales tax must be a number, e.g. 8.875")
                            if (pin != pin2) throw ActionException("The two PINs don't match")
                            app.setUp(SettingsEntity(restaurantName = name, taxRate = rate / 100, timezone = timezone.trim()), owner, pin)
                        }
                    },
                    enabled = !action.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (action.busy) "Setting up…" else "Set up restaurant") }
            }
        }
    }
}

@Composable
fun PinField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
