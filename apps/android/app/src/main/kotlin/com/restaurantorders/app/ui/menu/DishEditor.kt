package com.restaurantorders.app.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.DishInput
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.MoneyField
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.MenuOptionGroup

private class SizeRow(val name: String, val cents: Int?)

/** Adds or changes a dish: number, names, price or sizes, protein choices and modification lists. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DishEditor(app: AppContainer, menu: Menu, dish: MenuItem?, categoryId: String, onDone: () -> Unit) {
    val sizeGroup = dish?.optionGroups?.find { it.name == MenuOptionGroup.SIZE }
    var code by remember { mutableStateOf(dish?.code.orEmpty()) }
    var name by remember { mutableStateOf(dish?.name.orEmpty()) }
    var altName by remember { mutableStateOf(dish?.altName.orEmpty()) }
    var category by remember { mutableStateOf(dish?.categoryId ?: categoryId) }
    var price by remember { mutableStateOf(if (dish != null && sizeGroup == null) dish.priceCents else null) }
    var sizes by remember { mutableStateOf(sizeGroup?.options?.map { SizeRow(it.name, dish.priceCents + it.extraCents) } ?: emptyList()) }
    var choices by remember { mutableStateOf(dish?.optionGroups?.find { it.name == MenuOptionGroup.CHOICE }?.options?.joinToString(", ") { it.name }.orEmpty()) }
    var spicy by remember { mutableStateOf(dish?.spicy ?: false) }
    var listIds by remember { mutableStateOf(dish?.modifierListIds ?: emptyList()) }
    var groupIds by remember { mutableStateOf(dish?.choiceGroupIds ?: emptyList()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val action = rememberAction()
    val categoryLists = menu.categories.find { it.id == category }?.modifierListIds.orEmpty()
    val categoryGroups = menu.categories.find { it.id == category }?.choiceGroupIds.orEmpty()

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp).widthIn(max = 820.dp).heightIn(max = 800.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(if (dish == null) "Add a dish" else "Change ${dish.name}", style = MaterialTheme.typography.titleLarge)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            code, { code = it }, Modifier.width(140.dp), label = { Text("Menu number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        )
                        OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(altName, { altName = it }, Modifier.weight(1f), label = { Text("Chinese name") }, singleLine = true)
                        Column {
                            OutlinedButton(onClick = { categoryMenu = true }) { Text("Category: ${menu.categories.find { it.id == category }?.name}") }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                menu.categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c.id; categoryMenu = false }) }
                            }
                        }
                    }

                    Text("PRICE", style = MaterialTheme.typography.labelLarge, color = Muted)
                    if (sizes.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MoneyField(price, { price = it }, "Price", Modifier.width(160.dp))
                            TextButton(onClick = { sizes = listOf(SizeRow("Pt", price), SizeRow("Qt", null)) }) { Text("+ Sizes (Pt / Qt…)") }
                        }
                    } else {
                        sizes.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    s.name, { v -> sizes = sizes.mapIndexed { j, r -> if (j == i) SizeRow(v, r.cents) else r } },
                                    Modifier.width(220.dp), label = { Text("Size") }, singleLine = true,
                                )
                                MoneyField(s.cents, { c -> sizes = sizes.mapIndexed { j, r -> if (j == i) SizeRow(r.name, c) else r } }, "Price", Modifier.width(160.dp))
                                TextButton(onClick = { sizes = if (sizes.size > 2) sizes.filterIndexed { j, _ -> j != i } else emptyList() }) { Text("Remove") }
                            }
                        }
                        TextButton(onClick = { sizes = sizes + SizeRow("", null) }) { Text("+ Size") }
                    }

                    OutlinedTextField(
                        choices, { choices = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Protein or style choice, e.g. Chicken, Roast Pork (empty if none)") },
                    )

                    Text("MODIFICATION LISTS", style = MaterialTheme.typography.labelLarge, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        menu.modifierLists.forEach { l ->
                            val fromCategory = l.id in categoryLists
                            val on = fromCategory || l.id in listIds
                            FilterChip(
                                selected = on,
                                enabled = !fromCategory,
                                onClick = { listIds = if (on) listIds - l.id else listIds + l.id },
                                label = { Text(l.name + if (fromCategory) " (whole category)" else "") },
                            )
                        }
                    }
                    if (menu.choiceGroups.isNotEmpty()) {
                        Text("CHOICE GROUPS", style = MaterialTheme.typography.labelLarge, color = Muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            menu.choiceGroups.forEach { g ->
                                val fromCategory = g.id in categoryGroups
                                val on = fromCategory || g.id in groupIds
                                FilterChip(
                                    selected = on,
                                    enabled = !fromCategory,
                                    onClick = { groupIds = if (on) groupIds - g.id else groupIds + g.id },
                                    label = { Text(g.name + if (fromCategory) " (whole category)" else "") },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(spicy, { spicy = it })
                        Text("Hot & spicy (★ on the menu)")
                    }
                    action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (dish != null) {
                        TextButton(onClick = { confirmRemove = true }) { Text("Remove dish", color = Red) }
                    }
                    TextButton(onClick = onDone) { Text("Cancel") }
                    Button(
                        enabled = !action.busy,
                        onClick = {
                            action.run {
                                if (sizes.any { it.cents == null }) throw ActionException("Every size needs a price")
                                if (sizes.isEmpty() && price == null) throw ActionException("Enter a price")
                                app.menuEditor.saveDish(
                                    dish?.id,
                                    DishInput(
                                        categoryId = category,
                                        code = code,
                                        name = name,
                                        altName = altName,
                                        priceCents = if (sizes.isEmpty()) price else null,
                                        sizes = sizes.map { it.name to it.cents!! },
                                        choices = choices.split(","),
                                        spicy = spicy,
                                        modifierListIds = listIds,
                                        choiceGroupIds = groupIds,
                                    ),
                                )
                                onDone()
                            }
                        },
                    ) { Text(if (dish == null) "Add dish" else "Save") }
                }
            }
        }
    }

    if (confirmRemove && dish != null) {
        ConfirmDialog(
            title = "Remove ${dish.name}?",
            text = "It leaves the menu. Past orders keep it.",
            confirm = "Remove",
            onConfirm = { confirmRemove = false; action.run { app.menuEditor.removeDish(dish.id); onDone() } },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
    )
}
