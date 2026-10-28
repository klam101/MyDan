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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.restaurantorders.domain.ChoiceGroup
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.ModifierList
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.money
import com.restaurantorders.domain.pickRule

/**
 * The shared pieces dishes offer, made once and attached to categories or dishes:
 * modification lists (Front's Special tab: Meat, Vegetables…) and choice groups (Front's MOD
 * tab: Comb & Lunch Choice, Something Extra).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModifierListsScreen(app: AppContainer, menu: Menu) {
    var editing by remember { mutableStateOf<ModifierList?>(null) }
    var addingList by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ChoiceGroup?>(null) }
    var addingGroup by remember { mutableStateOf(false) }

    LazyVerticalGrid(GridCells.Adaptive(340.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHead(
                "Modification lists",
                "Kitchen instructions: the cashier taps Add, More, Less, No, Only, Change or Side, then the ingredient. " +
                    "Only Add and More charge the price.",
                "+ New list",
            ) { addingList = true }
        }
        items(menu.modifierLists, key = { it.id }) { list ->
            AttachedCard(
                title = listOfNotNull(list.name, list.altName).joinToString("  "),
                used = usedBy(menu, list.id, { it.modifierListIds }, { it.modifierListIds }),
                onEdit = { editing = list },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.actions.forEach { Text(it.label, color = premodColor(it), fontWeight = FontWeight.ExtraBold) }
                }
                Text(list.modifiers.joinToString(" · ") { m -> m.name + if (m.priceCents > 0) " ${money(m.priceCents)}" else "" })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHead(
                "Choice groups",
                "Priced picks such as the rice that comes with a combination plate. A group can be optional or required, with a most-picks limit.",
                "+ New group",
            ) { addingGroup = true }
        }
        items(menu.choiceGroups, key = { it.id }) { group ->
            AttachedCard(
                title = listOfNotNull(group.name, group.altName).joinToString("  "),
                used = usedBy(menu, group.id, { it.choiceGroupIds }, { it.choiceGroupIds }),
                onEdit = { editingGroup = group },
            ) {
                Text(pickRule(group), fontWeight = FontWeight.Bold)
                Text(group.choices.joinToString(" · ") { c -> c.name + if (c.priceCents > 0) " ${money(c.priceCents)}" else "" })
            }
        }
    }

    if (addingList || editing != null) ListEditor(app, editing, onDone = { addingList = false; editing = null })
    if (addingGroup || editingGroup != null) GroupEditor(app, editingGroup, onDone = { addingGroup = false; editingGroup = null })
}

@Composable
private fun SectionHead(title: String, help: String, add: String, onAdd: () -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(help, color = Muted)
        }
        Button(onClick = onAdd) { Text(add) }
    }
}

/** "Used by Lunch Special, Combination Plates and 2 single dishes". */
private fun usedBy(menu: Menu, id: String, ofCategory: (com.restaurantorders.domain.MenuCategory) -> List<String>, ofDish: (com.restaurantorders.domain.MenuItem) -> List<String>): String {
    val categories = menu.categories.filter { id in ofCategory(it) }.map { it.name }
    val dishes = menu.categories.sumOf { c -> c.items.count { id in ofDish(it) } }
    if (categories.isEmpty() && dishes == 0) return "Not attached to any dish yet"
    return "Used by " + listOfNotNull(
        categories.takeIf { it.isNotEmpty() }?.joinToString(", "),
        if (dishes > 0) "$dishes single dish${if (dishes == 1) "" else "es"}" else null,
    ).joinToString(" and ")
}

@Composable
private fun AttachedCard(title: String, used: String, onEdit: () -> Unit, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onEdit) { Text("Edit") }
            }
            content()
            Text(used, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private class IngredientRow(val id: String?, val name: String, val altName: String, val cents: Int?)

/** Name, Chinese name and price rows, shared by both editors. */
@Composable
private fun PricedRows(rows: List<IngredientRow>, showPrice: Boolean, namePlaceholder: String, onChange: (List<IngredientRow>) -> Unit) {
    rows.forEachIndexed { i, r ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(r.name, { v -> onChange(rows.mapIndexed { j, x -> if (j == i) IngredientRow(x.id, v, x.altName, x.cents) else x }) }, Modifier.weight(2f), singleLine = true, label = { Text(namePlaceholder) })
            OutlinedTextField(r.altName, { v -> onChange(rows.mapIndexed { j, x -> if (j == i) IngredientRow(x.id, x.name, v, x.cents) else x }) }, Modifier.weight(1.5f), singleLine = true, label = { Text("Chinese") })
            if (showPrice) MoneyField(r.cents, { c -> onChange(rows.mapIndexed { j, x -> if (j == i) IngredientRow(x.id, x.name, x.altName, c) else x }) }, "Price", Modifier.width(140.dp))
            TextButton(onClick = { onChange(rows.filterIndexed { j, _ -> j != i }) }) { Text("Remove") }
        }
    }
    TextButton(onClick = { onChange(rows + IngredientRow(null, "", "", 0)) }) { Text("+ Add a row") }
}

private fun List<IngredientRow>.toInputs(): List<ModifierInput> {
    val filled = filter { it.name.isNotBlank() }
    if (filled.any { it.cents == null }) throw ActionException("Fix the prices marked in red")
    return filled.map { ModifierInput(it.id, it.name, it.altName, it.cents ?: 0) }
}

@Composable
private fun EditorFrame(title: String, error: String?, busy: Boolean, saveLabel: String, onDelete: (() -> Unit)?, onSave: () -> Unit, onDone: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp).widthIn(max = 760.dp).heightIn(max = 800.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                    error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    onDelete?.let { TextButton(onClick = it) { Text("Delete", color = Red) } }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDone) { Text("Cancel") }
                    Button(enabled = !busy, onClick = onSave) { Text(saveLabel) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListEditor(app: AppContainer, list: ModifierList?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    var altName by remember { mutableStateOf(list?.altName.orEmpty()) }
    var actions by remember { mutableStateOf(list?.actions ?: listOf(PreModifier.ADD, PreModifier.EXTRA, PreModifier.NO)) }
    var rows by remember { mutableStateOf(list?.modifiers?.map { IngredientRow(it.id, it.name, it.altName.orEmpty(), it.priceCents) } ?: listOf(IngredientRow(null, "", "", 0))) }
    var confirmDelete by remember { mutableStateOf(false) }
    val action = rememberAction()

    EditorFrame(
        title = if (list == null) "New modification list" else "Change ${list.name}",
        error = action.error,
        busy = action.busy,
        saveLabel = if (list == null) "Create list" else "Save",
        onDelete = list?.let { { confirmDelete = true } },
        onSave = { action.run { app.menuEditor.saveModifierList(list?.id, name, actions, rows.toInputs(), altName); onDone() } },
        onDone = onDone,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.weight(2f), singleLine = true, label = { Text("List name, e.g. Vegetables") })
            OutlinedTextField(altName, { altName = it }, Modifier.weight(1f), singleLine = true, label = { Text("Chinese") })
        }
        Text("CASHIER CAN PICK", style = MaterialTheme.typography.labelLarge, color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreModifier.BAR.forEach { a ->
                val on = a in actions
                val color = premodColor(a)
                OutlinedButton(
                    onClick = { actions = if (on) actions - a else actions + a },
                    border = BorderStroke(2.dp, color),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) color else Color.White, contentColor = if (on) Color.White else color),
                ) { Text(a.button, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Text("Add and More charge the price below. The others are free.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("INGREDIENTS", style = MaterialTheme.typography.labelLarge, color = Muted)
        PricedRows(rows, showPrice = actions.any { it.charged }, namePlaceholder = "Name, e.g. Onion") { rows = it }
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

@Composable
private fun GroupEditor(app: AppContainer, group: ChoiceGroup?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var altName by remember { mutableStateOf(group?.altName.orEmpty()) }
    var min by remember { mutableStateOf((group?.min ?: 0).toString()) }
    var max by remember { mutableStateOf((group?.max ?: 1).toString()) }
    var rows by remember { mutableStateOf(group?.choices?.map { IngredientRow(it.id, it.name, it.altName.orEmpty(), it.priceCents) } ?: listOf(IngredientRow(null, "", "", 0))) }
    var confirmDelete by remember { mutableStateOf(false) }
    val action = rememberAction()

    EditorFrame(
        title = if (group == null) "New choice group" else "Change ${group.name}",
        error = action.error,
        busy = action.busy,
        saveLabel = if (group == null) "Create group" else "Save",
        onDelete = group?.let { { confirmDelete = true } },
        onSave = {
            action.run {
                val lo = min.toIntOrNull() ?: throw ActionException("Fewest picks must be a number")
                val hi = max.toIntOrNull() ?: throw ActionException("Most picks must be a number")
                app.menuEditor.saveChoiceGroup(group?.id, name, altName, lo, hi, rows.toInputs())
                onDone()
            }
        },
        onDone = onDone,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.weight(2f), singleLine = true, label = { Text("Group name, e.g. Comb & Lunch Choice") })
            OutlinedTextField(altName, { altName = it }, Modifier.weight(1f), singleLine = true, label = { Text("Chinese") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(min, { min = it.filter(Char::isDigit).take(2) }, Modifier.width(150.dp), singleLine = true, label = { Text("Fewest picks") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(max, { max = it.filter(Char::isDigit).take(2) }, Modifier.width(150.dp), singleLine = true, label = { Text("Most picks") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("0 fewest means the cashier can skip it.", color = Muted)
        }
        Text("CHOICES", style = MaterialTheme.typography.labelLarge, color = Muted)
        PricedRows(rows, showPrice = true, namePlaceholder = "Name, e.g. w. Pork Lo Mein") { rows = it }
    }

    if (confirmDelete && group != null) {
        ConfirmDialog(
            title = "Delete ${group.name}?",
            text = "It is removed from every category and dish that uses it. Past orders keep their choices.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; action.run { app.menuEditor.removeChoiceGroup(group.id); onDone() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
