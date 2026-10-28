package com.restaurantorders.app.ui.order

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.zone
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.OkSoft
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.Warn
import com.restaurantorders.app.ui.theme.WarnSoft
import com.restaurantorders.app.ui.theme.premodColor
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.OrderLine
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.describeModifier
import com.restaurantorders.domain.findItem
import com.restaurantorders.domain.linePrice
import com.restaurantorders.domain.money
import com.restaurantorders.domain.priceLabel
import com.restaurantorders.domain.totals
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val PICKUP_MINUTES = listOf(10, 15, 20, 30, 45, 60)

/** A cart line as it will be saved, or why it can't be (e.g. the dish sold out meanwhile). */
private class Preview(val key: Int, val input: NewOrderLine, val line: OrderLine?, val error: String?)

/**
 * Taking an order: the menu on the left, the order on the right. Port of the web app's
 * OrderScreen.tsx. When `cart.editing` is set, Send saves the change and prints CHANGED.
 */
@Composable
fun NewOrderScreen(app: AppContainer, settings: SettingsEntity, cart: CartState, onChangeSaved: () -> Unit) {
    val menu by app.menu.watch().collectAsState(initial = null)
    val current = menu
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading the menu…") }
        return
    }
    var sheet by remember { mutableStateOf<Pair<MenuItem, Int?>?>(null) }
    var sent by remember { mutableStateOf<BannerMessage?>(null) }
    // Cleared after each dish is added, so the next number can be typed right away.
    var search by remember { mutableStateOf("") }

    fun tap(item: MenuItem) {
        sent = null
        if (item.optionGroups.isNotEmpty()) sheet = item to null
        else {
            cart.add(NewOrderLine(item.id, 1))
            search = ""
        }
    }

    Row(Modifier.fillMaxSize()) {
        MenuPane(current, search, { search = it }, Modifier.weight(1f), onTap = ::tap)
        VerticalDivider()
        CartPane(
            app, settings, current, cart, sent,
            modifier = Modifier.width(420.dp).fillMaxHeight(),
            onModify = { key -> cart.lines.find { it.key == key }?.let { l -> findItem(current, l.input.menuItemId)?.let { sheet = it.first to key } } },
            onSent = { sent = it },
            onChangeSaved = onChangeSaved,
        )
    }

    sheet?.let { (item, key) ->
        val (dish, category) = findItem(current, item.id) ?: return@let
        val existing = key?.let { k -> cart.lines.find { it.key == k } }
        DishSheet(
            menu = current,
            item = dish,
            category = category,
            initial = existing?.input,
            onSave = { input ->
                if (existing != null) cart.replace(existing.key, input) else cart.add(input).also { search = "" }
                sheet = null
            },
            onRemove = existing?.let { { cart.remove(it.key); sheet = null } },
            onDismiss = { sheet = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuPane(menu: Menu, search: String, onSearch: (String) -> Unit, modifier: Modifier, onTap: (MenuItem) -> Unit) {
    var categoryId by remember { mutableStateOf(menu.categories.firstOrNull()?.id) }
    val category = menu.categories.find { it.id == categoryId } ?: menu.categories.firstOrNull()
    val query = search.trim().lowercase()
    val items = if (query.isEmpty()) category?.items.orEmpty() else menu.categories.flatMap { it.items }.filter {
        it.code.lowercase() == query || (query.length > 1 && it.name.lowercase().contains(query))
    }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            search, onSearch, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Menu number or name, e.g. L6 or lo mein") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        if (query.isEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                menu.categories.forEach { c ->
                    FilterChip(selected = c.id == category?.id, onClick = { categoryId = c.id }, label = { Text(c.name) })
                }
            }
            category?.note?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
        if (query.isNotEmpty() && items.isEmpty()) Text("No dish matches “$search”", color = Muted)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item -> DishTile(item) { onTap(item) } }
        }
    }
}

@Composable
private fun DishTile(item: MenuItem, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        enabled = item.available,
        modifier = Modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(item.code, color = Red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (item.spicy) Text("★", color = Red)
            }
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            item.altName?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
            Text(if (item.available) priceLabel(item) else "Sold out", color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartPane(
    app: AppContainer,
    settings: SettingsEntity,
    menu: Menu,
    cart: CartState,
    sent: BannerMessage?,
    modifier: Modifier,
    onModify: (Int) -> Unit,
    onSent: (BannerMessage?) -> Unit,
    onChangeSaved: () -> Unit,
) {
    val previews = cart.lines.map { c ->
        runCatching { buildLine(menu, c.input) { "preview" } }.fold(
            onSuccess = { Preview(c.key, c.input, it, null) },
            onFailure = { Preview(c.key, c.input, null, it.message) },
        )
    }
    val sum = totals(previews.mapNotNull { it.line }, settings.taxRate)
    val blocked = previews.any { it.error != null }
    val action = rememberAction()
    val editing = cart.editing

    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            editing?.let {
                Text(
                    "Changing order #${it.order.number} · a CHANGED ticket will print",
                    color = Warn, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(WarnSoft, RoundedCornerShape(10.dp)).padding(10.dp),
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(OrderType.WALK_IN to "Walk-in", OrderType.PHONE to "Phone").forEachIndexed { i, (type, label) ->
                    SegmentedButton(
                        selected = cart.type == type,
                        onClick = { cart.type = type },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cart.customerName, { cart.customerName = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Name") })
                OutlinedTextField(
                    cart.customerPhone, { cart.customerPhone = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            if (cart.type == OrderType.PHONE) {
                val pickup = cart.pickupAt
                Text(
                    "Pickup: " + (pickup?.let { CLOCK.format(it.atZone(settings.zone)) } ?: "as soon as ready"),
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = pickup == null, onClick = { cart.pickupAt = null }, label = { Text("ASAP") })
                    PICKUP_MINUTES.forEach { m ->
                        FilterChip(selected = false, onClick = { cart.pickupAt = Instant.now().plus(Duration.ofMinutes(m.toLong())) }, label = { Text("$m min") })
                    }
                }
            }
            HorizontalDivider()

            LazyColumn(Modifier.weight(1f)) {
                if (previews.isEmpty()) item { Text("Tap dishes to add them", color = Muted, modifier = Modifier.padding(vertical = 24.dp)) }
                items(previews, key = { it.key }) { p -> CartLineRow(p, highlighted = p.key == cart.lastAdded, cart, onModify) }
            }

            OutlinedTextField(cart.note, { cart.note = it.take(300) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Note for the whole order") })
            Totals("Subtotal", sum.subtotalCents)
            Totals("Tax", sum.taxCents)
            Totals("Total", sum.totalCents, big = true)
            sent?.let { Banner(it) }
            action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { cart.clear(); onSent(null); if (editing != null) onChangeSaved() },
                    enabled = !action.busy && (cart.lines.isNotEmpty() || editing != null),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(if (editing != null) "Cancel change" else "Clear") }
                Button(
                    onClick = {
                        action.run {
                            val input = cart.toInput()
                            val (view, job) = if (editing != null) app.orders.update(editing.order.id, input) else app.orders.create(input)
                            cart.clear()
                            onSent(sentMessage(view.order.number, editing != null, PrintStatus.valueOf(job.status), job.error))
                            if (editing != null) onChangeSaved()
                        }
                    },
                    enabled = !action.busy && cart.lines.isNotEmpty() && !blocked,
                    modifier = Modifier.weight(2f).height(56.dp),
                ) {
                    Text(
                        when {
                            action.busy -> "Sending…"
                            editing != null -> "Save & print CHANGED"
                            else -> "Send to kitchen"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(p: Preview, highlighted: Boolean, cart: CartState, onModify: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (highlighted) OkSoft else MaterialTheme.colorScheme.surface).padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuantityStepper(p.input.quantity, onChange = { cart.changeQuantity(p.key, it - p.input.quantity) })
            Column(Modifier.weight(1f).clickable(enabled = p.line != null) { onModify(p.key) }.padding(top = 6.dp)) {
                val line = p.line
                if (line == null) {
                    Text("Unavailable dish", fontWeight = FontWeight.SemiBold)
                    Text(p.error ?: "", color = Red, style = MaterialTheme.typography.bodySmall)
                } else {
                    Row {
                        if (line.code.isNotEmpty()) Text("${line.code} ", color = Red, fontWeight = FontWeight.Bold)
                        Text(line.name)
                    }
                    if (line.options.isNotEmpty()) Text(line.options.joinToString(" · ") { it.name }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    line.modifiers.forEach { m ->
                        Text(
                            describeModifier(m.action, m.name) + if (m.priceCents > 0) "  +${money(m.priceCents)}" else "",
                            color = premodColor(m.action), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    line.note?.let { Text("“$it”", color = Warn, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text(p.line?.let { money(linePrice(it)) } ?: "", modifier = Modifier.padding(top = 6.dp))
        }
        if (p.line != null) {
            TextButton(onClick = { onModify(p.key) }, modifier = Modifier.padding(start = 120.dp)) { Text("Modify dish ›") }
        } else {
            TextButton(onClick = { cart.remove(p.key) }, modifier = Modifier.padding(start = 120.dp)) { Text("Remove") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun Totals(label: String, cents: Int, big: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(money(cents), fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
    }
}

private fun sentMessage(number: Int, changed: Boolean, status: PrintStatus, error: String?): BannerMessage {
    val what = if (changed) "Change to order #$number saved" else "Order #$number sent"
    return when (status) {
        PrintStatus.PRINTED -> BannerMessage(BannerKind.OK, "$what. Ticket printed ✓")
        PrintStatus.NO_PRINTER -> BannerMessage(BannerKind.WARN, "$what. No printer is set up, so tell the kitchen.")
        else -> BannerMessage(BannerKind.ERROR, "$what, but the ticket did NOT print. ${error.orEmpty()} Reprint it from Orders.")
    }
}

