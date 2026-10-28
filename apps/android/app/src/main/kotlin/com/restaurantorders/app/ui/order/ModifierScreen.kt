package com.restaurantorders.app.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.restaurantorders.app.ui.theme.Ink
import com.restaurantorders.app.ui.theme.Line
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Rail
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.Selected
import com.restaurantorders.app.ui.theme.premodColor
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuCategory
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.ModifierPick
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.choiceGroupsForItem
import com.restaurantorders.domain.describeModifier
import com.restaurantorders.domain.displayNames
import com.restaurantorders.domain.linePrice
import com.restaurantorders.domain.listsForItem
import com.restaurantorders.domain.money
import com.restaurantorders.domain.pickRule
import com.restaurantorders.domain.toggleChoice
import com.restaurantorders.domain.togglePick
import kotlinx.coroutines.launch

/** One tile on either tab: what it shows and what tapping it does. */
private class Tile(val name: String, val alt: String?, val price: Int, val picked: Boolean, val pickedLabel: String?, val color: Color, val enabled: Boolean, val onTap: () -> Unit)

private class Section(val title: String, val rule: String?, val tiles: List<Tile>)

/**
 * Front's modifier screen for one cart line. The MOD tab holds the dish's size and choice groups
 * ("Comb & Lunch Choice (Optional 1)"); the Special tab holds kitchen instructions: tap Add,
 * More, Less, No, Only, Change or Side, then the ingredient. A list down the side jumps to each
 * group. Confirm saves the line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModifierScreen(
    menu: Menu,
    item: MenuItem,
    category: MenuCategory,
    initial: NewOrderLine,
    language: TicketLanguage,
    startOnSpecial: Boolean,
    onSave: (NewOrderLine) -> Unit,
    onDismiss: () -> Unit,
) {
    val lists = remember(item, category, menu) { listsForItem(item, category, menu.modifierLists) }
    val groups = remember(item, category, menu) { choiceGroupsForItem(item, category, menu.choiceGroups) }
    val actions = PreModifier.BAR.filter { a -> lists.any { a in it.actions } }
    var optionIds by remember { mutableStateOf(initial.optionIds) }
    var choiceIds by remember { mutableStateOf(initial.choiceIds) }
    var picks by remember { mutableStateOf(initial.modifiers) }
    var quantity by remember { mutableStateOf(initial.quantity) }
    var note by remember { mutableStateOf(initial.note.orEmpty()) }
    var special by remember { mutableStateOf(startOnSpecial && lists.isNotEmpty() || (groups.isEmpty() && item.optionGroups.isEmpty())) }
    var action by remember { mutableStateOf(actions.firstOrNull() ?: PreModifier.ADD) }
    var writing by remember { mutableStateOf(note.isNotEmpty()) }

    val draft = initial.copy(quantity = quantity, optionIds = optionIds, choiceIds = choiceIds, modifiers = picks, note = note.ifBlank { null })
    val built = runCatching { buildLine(menu, draft) { "preview" } }
    val (title, subtitle) = displayNames(item.name, item.altName, language)

    fun names(name: String, alt: String?) = displayNames(name, alt, language)

    val modSections = item.optionGroups.map { g ->
        Section(
            if (g.name == MenuOptionGroup.CHOICE) "Choose one" else g.name,
            "Pick 1",
            g.options.map { o ->
                val (n, a) = names(o.name, o.altName)
                Tile(n, a, item.priceCents + o.extraCents, o.id in optionIds, null, Ink, true) {
                    optionIds = optionIds.filter { id -> g.options.none { it.id == id } } + o.id
                }
            },
        )
    } + groups.map { g ->
        val (n, a) = names(g.name, g.altName)
        Section(
            listOfNotNull(n, a).joinToString(" "),
            pickRule(g),
            g.choices.map { c ->
                val (cn, ca) = names(c.name, c.altName)
                Tile(cn, ca, c.priceCents, c.id in choiceIds, null, Ink, true) { choiceIds = toggleChoice(choiceIds, g, c.id) }
            },
        )
    }
    val specialSections = lists.map { list ->
        val allowed = action in list.actions
        val (n, a) = names(list.name, list.altName)
        Section(
            listOfNotNull(n, a).joinToString(" "),
            if (allowed) null else "not with ${action.button}",
            list.modifiers.map { m ->
                val picked = picks.find { it.modifierId == m.id }
                val (mn, ma) = names(m.name, m.altName)
                Tile(
                    mn, ma, if (action.charged) m.priceCents else 0, picked != null, picked?.action?.label,
                    picked?.let { premodColor(it.action) } ?: Ink, allowed || picked != null,
                ) { picks = togglePick(picks, ModifierPick(action, m.id)) }
            },
        )
    }
    val sections = if (special) specialSections else modSections

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxSize()) {
                // Left: the dish, how many, what's picked, the running price and Confirm.
                Column(Modifier.width(340.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((if (item.code.isNotEmpty()) "${item.code}. " else "") + title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text((if (item.code.isNotEmpty()) "${item.code}. " else "") + it, color = Muted, style = MaterialTheme.typography.titleMedium) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantity", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        QuantityStepper(quantity) { quantity = it.coerceIn(1, 99) }
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val line = built.getOrNull()
                        if (line != null) {
                            items(line.options.size) { i -> Text(line.options[i].name, fontWeight = FontWeight.SemiBold) }
                            items(line.choices.size) { i ->
                                val c = line.choices[i]
                                Row { Text(c.name, Modifier.weight(1f)); if (c.priceCents > 0) Text("+${money(c.priceCents)}") }
                            }
                            items(line.modifiers.size) { i ->
                                val m = line.modifiers[i]
                                Row(Modifier.clickable { picks = picks.filter { it.modifierId != m.modifierId } }) {
                                    Text(describeModifier(m.action, m.name) + "  ×", Modifier.weight(1f), color = premodColor(m.action), fontWeight = FontWeight.Bold)
                                    if (m.priceCents > 0) Text("+${money(m.priceCents)}")
                                }
                            }
                            line.note?.let { n -> item { Text("“$n”", color = Muted) } }
                        }
                    }
                    built.exceptionOrNull()?.let { Text(it.message ?: "", color = Red, fontWeight = FontWeight.SemiBold) }
                    Row {
                        Text("Item total:", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(built.getOrNull()?.let { money(linePrice(it)) } ?: "—", color = Red, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSave(draft) },
                        enabled = built.isSuccess,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rail),
                    ) { Text("Confirm", style = MaterialTheme.typography.titleLarge) }
                }

                Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SingleChoiceSegmentedButtonRow(Modifier.width(360.dp)) {
                            SegmentedButton(selected = !special, onClick = { special = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("MOD") }
                            SegmentedButton(selected = special, onClick = { special = true }, enabled = lists.isNotEmpty(), shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Special") }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Close", style = MaterialTheme.typography.titleMedium) }
                    }
                    if (special) {
                        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            actions.forEach { a ->
                                val on = a == action
                                val color = premodColor(a)
                                OutlinedButton(
                                    onClick = { action = a },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    border = BorderStroke(2.dp, if (on) color else Line),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) color else Color.White, contentColor = if (on) Color.White else color),
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text(a.button, fontWeight = FontWeight.ExtraBold) }
                            }
                            TextButton(onClick = { writing = !writing }) { Text("✎ Note", style = MaterialTheme.typography.titleMedium) }
                        }
                        if (writing) {
                            OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Anything else for the kitchen") })
                        }
                    }
                    if (sections.isEmpty()) {
                        Text(if (special) "No kitchen instructions are set up for this dish." else "This dish has no sizes or choices. Use Special for kitchen instructions.", color = Muted)
                    }
                    SectionList(sections)
                }
            }
        }
    }
}

/** The groups as tile grids, with Front's jump list down the right side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionList(sections: List<Section>) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(Modifier.weight(1f), state = state, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(sections) { _, s ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        s.rule?.let { Text("  ($it)", color = Muted) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.tiles.forEach { TileBox(it) }
                    }
                }
            }
        }
        if (sections.size > 1) {
            Column(Modifier.width(130.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEachIndexed { i, s ->
                    Text(
                        s.title,
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).clickable { scope.launch { state.animateScrollToItem(i) } }.padding(10.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileBox(t: Tile) {
    Box(
        Modifier
            .size(width = 150.dp, height = 92.dp)
            .background(if (t.picked) Selected else Color.White, RoundedCornerShape(10.dp))
            .border(if (t.picked) 2.dp else 1.dp, if (t.picked) t.color else Line, RoundedCornerShape(10.dp))
            .clickable(enabled = t.enabled, onClick = t.onTap)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                (t.pickedLabel?.let { "$it " } ?: "") + t.name,
                color = if (t.enabled) t.color else Muted,
                fontWeight = if (t.picked) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            t.alt?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            Spacer(Modifier.weight(1f))
            if (t.price > 0) Text(money(t.price), color = Red, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Front's "Select Spec" box: one tap on a size or choice adds the dish (several groups go in turn). */
@Composable
fun SpecDialog(item: MenuItem, language: TicketLanguage, onPicked: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(listOf<String>()) }
    val group = item.optionGroups.firstOrNull { g -> g.options.none { it.id in picked } } ?: return
    val (title, _) = displayNames(item.name, item.altName, language)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Select ${if (group.name == MenuOptionGroup.CHOICE) "choice" else group.name.lowercase()}", style = MaterialTheme.typography.titleLarge)
                        Text((if (item.code.isNotEmpty()) "${item.code}. " else "") + title, color = Muted)
                    }
                    TextButton(onClick = onDismiss) { Text("✕", style = MaterialTheme.typography.titleLarge) }
                }
                val rows = group.options.chunked(2)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows.size) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows[r].forEach { o ->
                                val (name, alt) = displayNames(o.name, o.altName, language)
                                Column(
                                    Modifier
                                        .width(240.dp)
                                        .border(1.dp, Line, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val next = picked + o.id
                                            if (item.optionGroups.all { g -> g.options.any { it.id in next } }) onPicked(next) else picked = next
                                        }
                                        .padding(14.dp),
                                ) {
                                    Text(name, style = MaterialTheme.typography.titleMedium)
                                    alt?.let { Text(it, color = Muted) }
                                    // Sizes show the full price, as on Front; choices only add their extra.
                                    val price = if (group.name == MenuOptionGroup.SIZE) item.priceCents + o.extraCents else o.extraCents
                                    if (group.name == MenuOptionGroup.SIZE || price > 0) Text(money(price), color = Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
