package com.restaurantorders.app.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.restaurantorders.app.ui.theme.Ink
import com.restaurantorders.app.ui.theme.Line
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.premodColor
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuCategory
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.ModifierPick
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.describeModifier
import com.restaurantorders.domain.linePrice
import com.restaurantorders.domain.listsForItem
import com.restaurantorders.domain.money
import com.restaurantorders.domain.togglePick

/**
 * Picks a dish's size and protein, and its modifications: choose NO / ADD / EXTRA / LIGHT /
 * ON SIDE / SUB, then tap ingredients. A dish with no modification lists goes into the order as
 * soon as its required picks are made, so a sized dish still takes two taps. Port of the web
 * app's ItemSheet.tsx.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DishSheet(
    menu: Menu,
    item: MenuItem,
    category: MenuCategory,
    initial: NewOrderLine?,
    onSave: (NewOrderLine) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val lists = remember(item, category, menu) { listsForItem(item, category, menu.modifierLists) }
    val actions = PreModifier.entries.filter { a -> lists.any { a in it.actions } }
    var optionIds by remember { mutableStateOf(initial?.optionIds ?: emptyList()) }
    var picks by remember { mutableStateOf(initial?.modifiers ?: emptyList()) }
    var action by remember { mutableStateOf(actions.firstOrNull() ?: PreModifier.NO) }
    var quantity by remember { mutableStateOf(initial?.quantity ?: 1) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    val draft = NewOrderLine(item.id, quantity, optionIds, picks, note, initial?.lineId)
    val missing = item.optionGroups.filter { g -> g.options.none { it.id in optionIds } }
    val price = if (missing.isEmpty()) runCatching { linePrice(buildLine(menu, draft) { "preview" }) }.getOrNull() else null

    fun pickOption(group: MenuOptionGroup, id: String) {
        val next = optionIds.filter { o -> group.options.none { it.id == o } } + id
        optionIds = next
        val complete = item.optionGroups.all { g -> g.options.any { it.id in next } }
        if (complete && lists.isEmpty() && initial == null) onSave(draft.copy(optionIds = next))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).widthIn(max = if (lists.isEmpty()) 560.dp else 820.dp).heightIn(max = 760.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.code.isNotEmpty()) Text("${item.code} ", color = Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                    item.altName?.let { Text("  $it", color = Muted) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item.optionGroups.forEach { group ->
                        Section(if (group.name == MenuOptionGroup.CHOICE) "Choose one" else group.name) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.options.forEach { o ->
                                    val selected = o.id in optionIds
                                    Choice(selected = selected, onClick = { pickOption(group, o.id) }) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(o.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                            if (group.name == MenuOptionGroup.SIZE) {
                                                Text(money(item.priceCents + o.extraCents), color = if (selected) Color(0xFFD1D5DB) else Muted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (lists.isNotEmpty()) {
                        Section("Modifications") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                actions.forEach { a ->
                                    val color = premodColor(a)
                                    val on = a == action
                                    OutlinedButton(
                                        onClick = { action = a },
                                        border = BorderStroke(2.dp, color),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (on) color else Color.White,
                                            contentColor = if (on) Color.White else color,
                                        ),
                                    ) { Text(a.label, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            lists.forEach { list ->
                                val allowed = action in list.actions
                                Text(
                                    list.name + if (allowed) "" else "  ·  not with ${action.label}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (allowed) Ink else Muted,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    list.modifiers.forEach { m ->
                                        val picked = picks.find { it.modifierId == m.id }
                                        val charge = action.charged && m.priceCents > 0 && picked == null
                                        val color = picked?.let { premodColor(it.action) } ?: Ink
                                        OutlinedButton(
                                            onClick = { picks = togglePick(picks, ModifierPick(action, m.id)) },
                                            enabled = allowed || picked != null,
                                            border = BorderStroke(if (picked != null) 2.dp else 1.dp, if (picked != null) color else Line),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text(
                                                (picked?.let { "${it.action.label} " } ?: "") + m.name + if (charge) "  +${money(m.priceCents)}" else "",
                                                fontWeight = if (picked != null) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                            if (picks.isNotEmpty()) {
                                val names = lists.flatMap { it.modifiers }.associate { it.id to it.name }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    picks.forEach { p ->
                                        TextButton(onClick = { picks = picks - p }) {
                                            Text("${describeModifier(p.action, names[p.modifierId] ?: "?")}  ×", color = premodColor(p.action), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            note, { note = it.take(200) }, Modifier.weight(1f), singleLine = true,
                            label = { Text("Note for the kitchen (anything not listed above)") },
                        )
                        QuantityStepper(quantity, onChange = { quantity = it.coerceIn(1, 99) })
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    onRemove?.let { OutlinedButton(onClick = it, modifier = Modifier.height(52.dp)) { Text("Remove", color = Red) } }
                    Button(
                        onClick = { onSave(draft) },
                        enabled = missing.isEmpty() && price != null,
                        modifier = Modifier.height(52.dp).widthIn(min = 280.dp),
                    ) {
                        Text(
                            when {
                                missing.isNotEmpty() -> "Pick " + missing.joinToString(" and ") { if (it.name == MenuOptionGroup.CHOICE) "a choice" else it.name.lowercase() }
                                initial != null -> "Update · ${money(price ?: 0)}"
                                else -> "Add to order · ${money(price ?: 0)}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Muted)
        content()
    }
}

@Composable
private fun Choice(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Ink else Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Ink else Color.White, contentColor = if (selected) Color.White else Ink),
        modifier = Modifier.widthIn(min = 130.dp).heightIn(min = 64.dp),
    ) { content() }
}

@Composable
fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange(quantity - 1) }, shape = RoundedCornerShape(10.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$quantity", Modifier.widthIn(min = 36.dp).padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(quantity + 1) }, shape = RoundedCornerShape(10.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
