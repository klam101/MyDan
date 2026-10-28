// The small boxes the order screen's tool buttons open: quantity keypad, price, discount,
// open item and order note.
package com.restaurantorders.app.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurantorders.app.ui.MoneyField
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.domain.Discount
import com.restaurantorders.domain.OpenItem
import com.restaurantorders.domain.describeDiscount
import com.restaurantorders.domain.money

@Composable
fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange(quantity - 1) }, shape = RoundedCornerShape(10.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$quantity", Modifier.widthIn(min = 36.dp).padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(quantity + 1) }, shape = RoundedCornerShape(10.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/** QTY: type how many, 1 to 99 (backlog B3). */
@Composable
fun QuantityDialog(name: String, current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How many $name?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text.ifEmpty { "$current" }, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (text.isEmpty()) Muted else MaterialTheme.colorScheme.onSurface)
                listOf("789", "456", "123", "C0⌫").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    text = when (key) {
                                        'C' -> ""
                                        '⌫' -> text.dropLast(1)
                                        else -> (text + key).trimStart('0').take(2)
                                    }
                                },
                                modifier = Modifier.size(72.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) { Text("$key", fontSize = 26.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = value != null && value in 1..99, onClick = { onSet(value!!) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** PRICE: a new price for one of this dish (backlog B4). */
@Composable
fun PriceDialog(name: String, menuCents: Int, currentOverride: Int?, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
    var cents by remember { mutableStateOf(currentOverride) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price for $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Menu price: ${money(menuCents)} each", color = Muted)
                MoneyField(cents, { cents = it }, "New price, each")
            }
        },
        confirmButton = { Button(enabled = cents != null && cents!! >= 0, onClick = { onSet(cents) }) { Text("Set price") } },
        dismissButton = {
            Row {
                if (currentOverride != null) TextButton(onClick = { onSet(null) }) { Text("Back to menu price") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Discount: a preset from Settings, or a typed $ or % (backlog B5). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscountDialog(presets: List<Discount>, current: Discount?, onSet: (Discount?) -> Unit, onDismiss: () -> Unit) {
    var percent by remember { mutableStateOf(current?.percent != null || current == null) }
    var text by remember { mutableStateOf(current?.percent?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var cents by remember { mutableStateOf(current?.amountCents) }
    var label by remember { mutableStateOf(current?.label.orEmpty()) }
    val typed = if (percent) text.toDoubleOrNull()?.let { Discount(label.trim(), percent = it) } else cents?.let { Discount(label.trim(), amountCents = it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discount") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { p -> FilterChip(selected = p == current, onClick = { onSet(p) }, label = { Text(describeDiscount(p)) }) }
                    }
                    Text("or type one:", color = Muted)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = percent, onClick = { percent = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("%") }
                    SegmentedButton(selected = !percent, onClick = { percent = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("$") }
                }
                if (percent) {
                    OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true, label = { Text("Percent off") }, suffix = { Text("%") })
                } else {
                    MoneyField(cents, { cents = it }, "Amount off")
                }
                OutlinedTextField(label, { label = it.take(30) }, singleLine = true, label = { Text("Why (optional), e.g. family") })
                Text("Comes off before tax.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = typed != null, onClick = { onSet(typed) }) { Text("Apply") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null) }) { Text("Remove discount", color = Red) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Open Item: something not on the menu, with a typed name and price (backlog B2). */
@Composable
fun OpenItemDialog(onAdd: (OpenItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    var cents by remember { mutableStateOf<Int?>(null) }
    val ok = name.isNotBlank() && (cents ?: 0) > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name, e.g. Extra sauce cup") })
                OutlinedTextField(alt, { alt = it.take(40) }, singleLine = true, label = { Text("Chinese name (optional)") })
                MoneyField(cents, { cents = it }, "Price")
            }
        },
        confirmButton = { Button(enabled = ok, onClick = { onAdd(OpenItem(name.trim(), alt.trim().ifEmpty { null }, cents!!)) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A note for one dish or the whole order. */
@Composable
fun NoteDialog(title: String, current: String, onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Note for the kitchen") }) },
        confirmButton = { Button(onClick = { onSet(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "$9.50" struck through before the manager's price, so everyone sees it was changed. */
@Composable
fun StruckPrice(cents: Int) {
    Text(money(cents), color = Muted, textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
}
