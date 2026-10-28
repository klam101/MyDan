// Settings pieces for ordering: order types, discount presets, void reasons, printer status.
package com.restaurantorders.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.data.OrderTypeSetting
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.zone
import com.restaurantorders.app.printing.PrinterHealth
import com.restaurantorders.app.printing.PrinterState
import com.restaurantorders.app.ui.MoneyField
import com.restaurantorders.app.ui.orders.clock
import com.restaurantorders.app.ui.theme.Ink
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Ok
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.domain.Discount
import com.restaurantorders.domain.describeDiscount

private val COLORS = listOf(0xFF16A34A, 0xFF2563EB, 0xFF9333EA, 0xFFEA580C, 0xFFDC2626, 0xFF0891B2)

/** Each order type: on or off, the name staff and tickets use, and its colour (backlog B7). */
@Composable
fun OrderTypesEditor(types: List<OrderTypeSetting>, onChange: (List<OrderTypeSetting>) -> Unit) {
    Text("Order types", style = MaterialTheme.typography.titleSmall)
    types.forEachIndexed { i, t ->
        fun update(next: OrderTypeSetting) = onChange(types.mapIndexed { j, x -> if (j == i) next else x })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(t.enabled, { update(t.copy(enabled = it)) })
            OutlinedTextField(t.name, { update(t.copy(name = it.take(20))) }, Modifier.width(200.dp), singleLine = true, label = { Text(t.type.defaultName) })
            COLORS.forEach { c ->
                Box(
                    Modifier.size(28.dp).background(Color(c), CircleShape)
                        .border(3.dp, if (c == t.color) Ink else Color.Transparent, CircleShape)
                        .clickable { update(t.copy(color = c)) },
                )
            }
            if (t.type.needsCustomer) Text("needs a name or phone", color = Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscountsEditor(discounts: List<Discount>, onChange: (List<Discount>) -> Unit) {
    var percent by remember { mutableStateOf(true) }
    var number by remember { mutableStateOf("") }
    var cents by remember { mutableStateOf<Int?>(null) }
    var label by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        discounts.forEach { d -> InputChip(selected = false, onClick = { onChange(discounts - d) }, label = { Text("${describeDiscount(d)}  ×") }) }
        if (discounts.isEmpty()) Text("None yet", color = Muted)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.width(120.dp)) {
            SegmentedButton(selected = percent, onClick = { percent = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("%") }
            SegmentedButton(selected = !percent, onClick = { percent = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("$") }
        }
        if (percent) {
            OutlinedTextField(number, { number = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.width(120.dp), singleLine = true, label = { Text("Percent") })
        } else {
            MoneyField(cents, { cents = it }, "Amount", Modifier.width(140.dp))
        }
        OutlinedTextField(label, { label = it.take(20) }, Modifier.width(160.dp), singleLine = true, label = { Text("Name, e.g. family") })
        val next = if (percent) number.toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }?.let { Discount(label.trim(), percent = it) }
        else cents?.takeIf { it > 0 }?.let { Discount(label.trim(), amountCents = it) }
        TextButton(enabled = next != null, onClick = { onChange(discounts + next!!); number = ""; cents = null; label = "" }) { Text("+ Add") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordsEditor(words: List<String>, placeholder: String, onChange: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        words.forEach { w -> InputChip(selected = false, onClick = { onChange(words - w) }, label = { Text("$w  ×") }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, { text = it.take(40) }, Modifier.width(320.dp), singleLine = true, label = { Text(placeholder) })
        TextButton(enabled = text.isNotBlank(), onClick = { onChange(words + text.trim()); text = "" }) { Text("+ Add") }
    }
}

/** "● Printer online, last contact 10:52 AM" with a Check now button (backlog H1). */
@Composable
fun PrinterStatusLine(health: PrinterHealth, settings: SettingsEntity, onCheck: () -> Unit) {
    val last = health.lastContact?.let { " · last answered ${clock(it, settings.zone)}" } ?: ""
    val (color, text) = when (health.state) {
        PrinterState.NOT_SET_UP -> Muted to "No printer set up"
        PrinterState.CHECKING -> Muted to "Checking the printer…"
        PrinterState.ONLINE -> Ok to "Printer online$last"
        PrinterState.OFFLINE -> Red to "Printer can't be reached$last"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("● $text", color = color, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onCheck) { Text("Check now") }
    }
    if (health.state == PrinterState.OFFLINE) health.problem?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
}
