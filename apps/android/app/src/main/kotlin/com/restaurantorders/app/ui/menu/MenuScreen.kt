package com.restaurantorders.app.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.theme.Ink
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.RedSoft
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuCategory
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.priceLabel

/**
 * Cashiers mark dishes sold out and back on. Managers and owners also change dishes,
 * categories and the shared modification lists (second tab).
 */
@Composable
fun MenuScreen(app: AppContainer, staff: Staff) {
    val menu by app.menu.watch().collectAsState(initial = null)
    val current = menu
    var listsTab by rememberSaveable { mutableStateOf(false) }
    val editor = staff.can(Permission.MENU)
    if (current == null) return

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (editor) {
            SingleChoiceSegmentedButtonRow(Modifier.width(420.dp)) {
                SegmentedButton(selected = !listsTab, onClick = { listsTab = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Dishes") }
                SegmentedButton(selected = listsTab, onClick = { listsTab = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Modifier lists") }
            }
        } else {
            Banner(BannerMessage(BannerKind.INFO, "You can mark dishes sold out. Only a manager or owner can change dishes and prices."))
        }
        if (listsTab && editor) ModifierListsScreen(app, current) else Dishes(app, current, editor)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dishes(app: AppContainer, menu: Menu, editor: Boolean) {
    var categoryId by rememberSaveable { mutableStateOf(menu.categories.firstOrNull()?.id) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<MenuItem?, String>?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    val action = rememberAction()
    val category = menu.categories.find { it.id == categoryId } ?: menu.categories.firstOrNull()
    val q = search.trim().lowercase()
    val items = if (q.isEmpty()) category?.items.orEmpty() else menu.categories.flatMap { it.items }.filter { it.code.lowercase() == q || it.name.lowercase().contains(q) }
    val soldOut = menu.categories.flatMap { it.items }.filter { !it.available }

    if (soldOut.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().background(RedSoft, RoundedCornerShape(12.dp)).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Sold out now:", color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
            soldOut.forEach { d ->
                OutlinedButton(onClick = { action.run { app.menu.setAvailable(d.id, true) } }) { Text("${d.code} ${d.name} · back on".trim()) }
            }
        }
    }
    action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(search, { search = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Find a dish by number or name") })
        if (editor && category != null) Button(onClick = { editing = null to category.id }) { Text("+ Dish") }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (q.isEmpty()) {
            LazyColumn(Modifier.width(230.dp).fillMaxHeight()) {
                items(menu.categories, key = { it.id }) { c ->
                    val active = c.id == category?.id
                    TextButton(onClick = { categoryId = c.id }, modifier = Modifier.fillMaxWidth().background(if (active) Ink else MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))) {
                        Text(c.name, color = if (active) MaterialTheme.colorScheme.onSecondary else Ink, modifier = Modifier.weight(1f))
                        Text("${c.items.size}", color = if (active) MaterialTheme.colorScheme.onSecondary else Muted)
                    }
                }
                if (editor) item { TextButton(onClick = { addingCategory = true }) { Text("+ Category") } }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (q.isEmpty() && category != null && editor) item(key = "category-" + category.id) { CategoryEditor(app, menu, category) }
            if (items.isEmpty()) item { Text(if (q.isEmpty()) "No dishes in this category yet" else "No dish matches “$search”", color = Muted, modifier = Modifier.padding(16.dp)) }
            items(items, key = { it.id }) { d ->
                DishRow(menu, d, editor, onAvailable = { on -> action.run { app.menu.setAvailable(d.id, on) } }, onEdit = { editing = d to d.categoryId })
            }
        }
    }

    editing?.let { (dish, catId) -> DishEditor(app, menu, dish, catId, onDone = { editing = null }) }
    if (addingCategory) AddCategoryDialog(app, onDone = { id -> addingCategory = false; id?.let { categoryId = it } })
}

@Composable
private fun DishRow(menu: Menu, d: MenuItem, editor: Boolean, onAvailable: (Boolean) -> Unit, onEdit: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(d.code, color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        d.name, fontWeight = FontWeight.SemiBold,
                        textDecoration = if (d.available) null else TextDecoration.LineThrough,
                        color = if (d.available) Ink else Muted,
                    )
                    if (d.spicy) Text(" ★", color = Red)
                    d.altName?.let { Text("  $it", color = Muted) }
                }
                val extras = listOfNotNull(
                    d.optionGroups.find { it.name == MenuOptionGroup.CHOICE }?.options?.joinToString(" or ") { it.name },
                    d.modifierListIds.mapNotNull { id -> menu.modifierLists.find { it.id == id }?.name }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "+ "),
                )
                if (extras.isNotEmpty()) Text(extras.joinToString("  "), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(priceLabel(d), color = Muted)
            Switch(d.available, onAvailable)
            Text(if (d.available) "On menu" else "Sold out", modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
            if (editor) OutlinedButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditor(app: AppContainer, menu: Menu, category: MenuCategory) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    var note by remember(category.id) { mutableStateOf(category.note.orEmpty()) }
    var listIds by remember(category.id) { mutableStateOf(category.modifierListIds) }
    var saved by remember(category.id) { mutableStateOf(false) }
    val action = rememberAction()
    val dirty = name != category.name || note != category.note.orEmpty() || listIds != category.modifierListIds

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; saved = false }, Modifier.width(240.dp), singleLine = true, label = { Text("Category") })
                OutlinedTextField(note, { note = it; saved = false }, Modifier.weight(1f), singleLine = true, label = { Text("Note shown to the cashier, e.g. With white rice.") })
            }
            Text("Every dish here offers:", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                menu.modifierLists.forEach { l ->
                    val on = l.id in listIds
                    FilterChip(selected = on, onClick = { listIds = if (on) listIds - l.id else listIds + l.id; saved = false }, label = { Text(l.name) })
                }
            }
            action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category.items.isEmpty()) TextButton(onClick = { action.run { app.menuEditor.removeCategory(category.id) } }) { Text("Remove category", color = Red) }
                Spacer(Modifier.weight(1f))
                if (saved && !dirty) Text("Saved ✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(
                    enabled = dirty && !action.busy,
                    onClick = { action.run { app.menuEditor.saveCategory(category.id, name, note, listIds); saved = true } },
                ) { Text("Save category") }
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(app: AppContainer, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    val action = rememberAction()
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
                action.error?.let { Text(it, color = Red) }
            }
        },
        confirmButton = { Button(onClick = { action.run { onDone(app.menuEditor.addCategory(name).id) } }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

