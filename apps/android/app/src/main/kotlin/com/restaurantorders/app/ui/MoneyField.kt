package com.restaurantorders.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.restaurantorders.domain.parseMoney
import java.util.Locale

/** A dollar amount field that reports cents, or null while the text isn't a price. */
@Composable
fun MoneyField(cents: Int?, onChange: (Int?) -> Unit, label: String, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(cents?.let { String.format(Locale.ROOT, "%d.%02d", it / 100, it % 100) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(parseMoney(it))
        },
        modifier = modifier,
        label = { Text(label) },
        prefix = { Text("$") },
        singleLine = true,
        isError = text.isNotEmpty() && parseMoney(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
