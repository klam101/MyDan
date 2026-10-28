package com.restaurantorders.app.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.ModifierInput
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.MoneyField
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.premodColor
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.ModifierList
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.money

/** Shared lists of modifications (Vegetables, Sauce & spice…), attached to categories or dishes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModifierListsScreen(app: AppContainer, menu: Menu) {
    var editing by remember { mutableStateOf<ModifierList?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A list lets the cashier change a dish: pick NO, ADD, EXTRA, LIGHT, ON SIDE or SUB, then the ingredient. " +
                    "Attach lists to a whole category on the Dishes tab, or to single dishes.",
                color = Muted, modifier = Modifier.weight(1f),
            )
            Button(onClick = { adding = true }) { Text("+ New list") }
        }
        LazyVerticalGrid(GridCells.Adaptive(340.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(menu.modifierLists, key = { it.id }) { list ->
                val categories = menu.categories.filter { list.id in it.modifierListIds }.map { it.name }
                val dishes = menu.categories.sumOf { c -> c.items.count { list.id in it.modifierListIds } }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { editing = list }) { Text("Edit") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.actions.forEach { Text(it.label, color = premodColor(it), fontWeight = FontWeight.ExtraBold) }
                        }
                        Text(list.modifiers.joinToString(" · ") { m -> m.name + if (m.priceCents > 0) " ${money(m.priceCents)}" else "" })
                        Text(
                            if (categories.isEmpty() && dishes == 0) "Not attached to any dish yet"
                            else "Used by " + listOfNotNull(categories.takeIf { it.isNotEmpty() }?.joinToString(", "), if (dishes > 0) "$dishes single dish${if (dishes == 1) "" else "es"}" else null).joinToString(" and "),
                            color = Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (adding || editing != null) ListEditor(app, editing, onDone = { adding = false; editing = null })
}

private class IngredientRow(val id: String?, val name: String, val altName: String, val cents: Int?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListEditor(app: AppContainer, list: ModifierList?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    var actions by remember { mutableStateOf(list?.actions ?: listOf(PreModifier.NO, PreModifier.EXTRA)) }
    var rows by remember { mutableStateOf(list?.modifiers?.map { IngredientRow(it.id, it.name, it.altName.orEmpty(), it.priceCents) } ?: listOf(IngredientRow(null, "", "", 0))) }
    var confirmDelete by remember { mutableStateOf(false) }
    val action = rememberAction()
    val charges = actions.any { it.charged }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp).widthIn(max = 760.dp).heightIn(max = 800.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(if (list == null) "New modification list" else "Change ${list.name}", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("List name, e.g. Vegetables") })
                    Text("CASHIER CAN PICK", style = MaterialTheme.typography.labelLarge, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PreModifier.entries.forEach { a ->
                            val on = a in actions
                            val color = premodColor(a)
                            OutlinedButton(
                                onClick = { actions = if (on) actions - a else actions + a },
                                border = BorderStroke(2.dp, color),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) color else Color.White, contentColor = if (on) Color.White else color),
                            ) { Text(a.label, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                    Text("ADD, EXTRA and SUB charge the price below. NO, LIGHT and ON SIDE are free.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("INGREDIENTS", style = MaterialTheme.typography.labelLarge, color = Muted)
                    rows.forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(r.name, { v -> rows = rows.mapIndexed { j, x -> if (j == i) IngredientRow(x.id, v, x.altName, x.cents) else x } }, Modifier.weight(2f), singleLine = true, label = { Text("Name, e.g. Onion") })
                            OutlinedTextField(r.altName, { v -> rows = rows.mapIndexed { j, x -> if (j == i) IngredientRow(x.id, x.name, v, x.cents) else x } }, Modifier.weight(1.5f), singleLine = true, label = { Text("Chinese") })
                            if (charges) MoneyField(r.cents, { c -> rows = rows.mapIndexed { j, x -> if (j == i) IngredientRow(x.id, x.name, x.altName, c) else x } }, "Price", Modifier.width(140.dp))
                            TextButton(onClick = { rows = rows.filterIndexed { j, _ -> j != i } }) { Text("Remove") }
                        }
                    }
                    TextButton(onClick = { rows = rows + IngredientRow(null, "", "", 0) }) { Text("+ Ingredient") }
                    action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (list != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete list", color = Red) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDone) { Text("Cancel") }
                    Button(
                        enabled = !action.busy,
                        onClick = {
                            action.run {
                                val filled = rows.filter { it.name.isNotBlank() }
                                if (filled.any { it.cents == null }) throw ActionException("Fix the prices marked in red")
                                app.menuEditor.saveModifierList(list?.id, name, actions, filled.map { ModifierInput(it.id, it.name, it.altName, it.cents ?: 0) })
                                onDone()
                            }
                        },
                    ) { Text(if (list == null) "Create list" else "Save") }
                }
            }
        }
    }

    if (confirmDelete && list != null) {
        ConfirmDialog(
            title = "Delete ${list.name}?",
            text = "It is removed from every category and dish that uses it. Past orders keep their modifications.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; action.run { app.menuEditor.removeModifierList(list.id); onDone() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
