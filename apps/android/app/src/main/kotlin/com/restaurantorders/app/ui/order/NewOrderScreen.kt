package com.restaurantorders.app.ui.order

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.data.enabledOrderTypes
import com.restaurantorders.app.data.requiresCustomer
import com.restaurantorders.app.data.discountPresets
import com.restaurantorders.app.data.typeColor
import com.restaurantorders.app.data.typeName
import com.restaurantorders.app.data.zone
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.app.printing.PrinterState
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.pin.ApprovalDialog
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.theme.Line
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Rail
import com.restaurantorders.app.ui.theme.RailActive
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.Selected
import com.restaurantorders.app.ui.theme.Warn
import com.restaurantorders.app.ui.theme.WarnSoft
import com.restaurantorders.app.ui.theme.premodColor
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.OrderLine
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.Totals
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.choiceGroupsForItem
import com.restaurantorders.domain.describeDiscount
import com.restaurantorders.domain.describeModifier
import com.restaurantorders.domain.displayNames
import com.restaurantorders.domain.findItem
import com.restaurantorders.domain.fromPriceLabel
import com.restaurantorders.domain.itemCount
import com.restaurantorders.domain.linePrice
import com.restaurantorders.domain.menuUnitPrice
import com.restaurantorders.domain.money
import com.restaurantorders.domain.searchMenu
import com.restaurantorders.domain.totals
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val PICKUP_MINUTES = listOf(10, 15, 20, 30, 45)
private val CategoryOn = Color(0xFF2563EB)

/** A cart line as it will be saved, or why it can't be (e.g. the dish sold out meanwhile). */
private class Preview(val key: Int, val input: NewOrderLine, val line: OrderLine?, val error: String?)

/** A risky action waiting for a manager's PIN; `then` runs with the manager's id. */
private class Ask(val permission: Permission, val what: String, val then: (String) -> Unit)

/** Which box is open over the order screen. */
private sealed interface Open {
    data class Spec(val item: MenuItem) : Open
    data class Mods(val key: Int, val special: Boolean) : Open
    data class Qty(val key: Int) : Open
    data class Price(val key: Int) : Open
    data class DishNote(val key: Int) : Open
    data object Discount : Open
    data object OpenItem : Open
    data object OrderNote : Open
}

/**
 * Taking an order, laid out like Front: the cart on the left with its tool buttons, the dishes in
 * the middle and the categories on the right. Tap a dish to add it (sizes first); select a cart
 * line and tap MOD for choices and kitchen instructions. When `cart.editing` is set, Send saves
 * the change and prints CHANGED.
 */
@Composable
fun NewOrderScreen(app: AppContainer, settings: SettingsEntity, staff: Staff, cart: CartState, onChangeSaved: () -> Unit) {
    val menu by app.menu.watch().collectAsState(initial = null)
    val current = menu
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading the menu…") }
        return
    }
    val language = staff.menuLanguage
    var open by remember { mutableStateOf<Open?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var sent by remember { mutableStateOf<BannerMessage?>(null) }
    // Cleared after each dish is added, so the next number can be typed right away.
    var search by remember { mutableStateOf("") }
    val action = rememberAction()

    // The order types this restaurant uses; a type turned off in Settings falls back to the first.
    val types = settings.enabledOrderTypes
    if (cart.editing == null && types.none { it.type == cart.type } && types.isNotEmpty()) cart.type = types.first().type

    /** Runs `then` now if this person may, or after a manager approves with their PIN (A4). */
    fun guarded(permission: Permission, what: String, then: () -> Unit) {
        if (staff.can(permission) || permission in cart.approvedBy) then()
        else ask = Ask(permission, what) { managerId -> cart.approve(permission, managerId); then() }
    }

    fun added(input: NewOrderLine, item: MenuItem) {
        cart.add(input)
        search = ""
        sent = null
        // A required choice group (e.g. "Pick 1") must be answered before Send, so open it now.
        val (_, category) = findItem(current, item.id) ?: return
        if (choiceGroupsForItem(item, category, current.choiceGroups).any { it.min > 0 }) {
            cart.selected?.let { open = Open.Mods(it, special = false) }
        }
    }

    fun tap(item: MenuItem) {
        if (item.optionGroups.isNotEmpty()) open = Open.Spec(item) else added(NewOrderLine(item.id, 1), item)
    }

    fun send() {
        action.run {
            val input = cart.toInput()
            val editing = cart.editing
            val missing = app.orders.approvalsNeeded(editing?.order?.id, input).filter { it.permission !in cart.approvedBy }
            if (missing.isNotEmpty()) {
                val first = missing.first()
                ask = Ask(first.permission, missing.filter { it.permission == first.permission }.joinToString("\n") { it.what }) { id ->
                    cart.approve(first.permission, id)
                    send()
                }
                return@run
            }
            val (view, job) = if (editing != null) app.orders.update(editing.order.id, input) else app.orders.create(input)
            cart.clear()
            sent = sentMessage(view.order.number, editing != null, PrintStatus.valueOf(job.status), job.error)
            if (editing != null) onChangeSaved()
        }
    }

    val previews = cart.lines.map { c ->
        runCatching { buildLine(current, c.input) { "preview" } }.fold(
            onSuccess = { Preview(c.key, c.input, it, null) },
            onFailure = { Preview(c.key, c.input, null, it.message) },
        )
    }
    val sum = runCatching { totals(previews.mapNotNull { it.line }, settings.taxRate, cart.discount) }
    val selected = previews.find { it.key == cart.selected }
    val printer by app.printer.health.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // A red line while the kitchen printer can't be reached (H1); orders still save.
        if (printer.state == PrinterState.OFFLINE) {
            Text(
                "Kitchen printer can't be reached" + (printer.lastContact?.let { " (last answered ${CLOCK.format(it.atZone(settings.zone))})" } ?: "") +
                    ". Orders still save; tell the kitchen and reprint from Orders.",
                color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(Red).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Row(Modifier.fillMaxSize()) {
            CartPane(
                settings, cart, previews, sum, language, sent, action.error, action.busy,
                modifier = Modifier.width(380.dp).fillMaxHeight(),
                onSelect = { p ->
                    val dish = p.line?.takeIf { !it.open }
                    if (cart.selected == p.key && dish != null) open = Open.Mods(p.key, special = true) else cart.selected = p.key
                },
                onSend = ::send,
                onClear = { cart.clear(); sent = null; if (cart.editing != null) onChangeSaved() },
                onCancelChange = { cart.clear(); onChangeSaved() },
                onOrderNote = { open = Open.OrderNote },
            )
            VerticalDivider()
            ToolColumn(
                hasLine = selected != null,
                canMod = selected?.line?.open == false,
                onPlus = { selected?.let { cart.changeQuantity(it.key, 1) } },
                onMinus = { selected?.let { cart.changeQuantity(it.key, -1) } },
                onQty = { selected?.let { open = Open.Qty(it.key) } },
                onVoid = { selected?.let { cart.remove(it.key) } },
                onMod = { selected?.let { open = Open.Mods(it.key, special = false) } },
                onPrice = { selected?.let { p -> guarded(Permission.PRICE, "Change the price of ${p.line?.name ?: "this dish"}") { open = Open.Price(p.key) } } },
                onDiscount = { guarded(Permission.DISCOUNT, "Give a discount on this order") { open = Open.Discount } },
                onUp = { selected?.let { cart.move(it.key, -1) } },
                onDown = { selected?.let { cart.move(it.key, 1) } },
                onDishNote = { selected?.let { open = Open.DishNote(it.key) } },
                onOrderNote = { open = Open.OrderNote },
            )
            VerticalDivider()
            MenuPane(
                app, current, language, search, { search = it },
                modifier = Modifier.weight(1f),
                onTap = ::tap,
                onOpenItem = { open = Open.OpenItem },
                onLanguage = { lang -> action.run { app.staff.setMenuLanguage(lang) } },
            )
        }
    }

    when (val o = open) {
        is Open.Spec -> SpecDialog(o.item, language, onPicked = { ids -> open = null; added(NewOrderLine(o.item.id, 1, ids), o.item) }, onDismiss = { open = null })
        is Open.Mods -> {
            val line = cart.lines.find { it.key == o.key }
            val found = line?.let { findItem(current, it.input.menuItemId) }
            if (line == null || found == null) {
                open = null
            } else {
                ModifierScreen(
                    current, found.first, found.second, line.input, language, o.special,
                    onSave = { input -> cart.replace(o.key, input); open = null },
                    onDismiss = { open = null },
                )
            }
        }
        is Open.Qty -> {
            val line = cart.lines.find { it.key == o.key }
            if (line == null) open = null
            else QuantityDialog(previews.find { it.key == o.key }?.line?.name.orEmpty(), line.input.quantity, onSet = { cart.setQuantity(o.key, it); open = null }, onDismiss = { open = null })
        }
        is Open.Price -> {
            val p = previews.find { it.key == o.key }
            val l = p?.line
            if (p == null || l == null) open = null
            else PriceDialog(l.name, menuUnitPrice(l), p.input.unitPriceCents, onSet = { cents -> cart.replace(o.key, p.input.copy(unitPriceCents = cents)); open = null }, onDismiss = { open = null })
        }
        is Open.DishNote -> {
            val line = cart.lines.find { it.key == o.key }
            if (line == null) open = null
            else NoteDialog("Note for this dish", line.input.note.orEmpty(), onSet = { cart.replace(o.key, line.input.copy(note = it.ifEmpty { null })); open = null }, onDismiss = { open = null })
        }
        Open.Discount -> DiscountDialog(settings.discountPresets, cart.discount, onSet = { cart.discount = it; open = null }, onDismiss = { open = null })
        Open.OpenItem -> OpenItemDialog(
            onAdd = { item ->
                open = null
                val add = { cart.add(NewOrderLine("", 1, openItem = item)); sent = null }
                if (item.priceCents > settings.openItemLimitCents) guarded(Permission.PRICE, "Ring up ${item.name} at ${money(item.priceCents)}", add) else add()
            },
            onDismiss = { open = null },
        )
        Open.OrderNote -> NoteDialog("Note for the whole order", cart.note, onSet = { cart.note = it; open = null }, onDismiss = { open = null })
        null -> {}
    }

    ask?.let { a ->
        ApprovalDialog(app, a.permission, a.what, onApproved = { manager -> ask = null; a.then(manager.id) }, onDismiss = { ask = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartPane(
    settings: SettingsEntity,
    cart: CartState,
    previews: List<Preview>,
    sum: Result<Totals>,
    language: TicketLanguage,
    sent: BannerMessage?,
    error: String?,
    busy: Boolean,
    modifier: Modifier,
    onSelect: (Preview) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onCancelChange: () -> Unit,
    onOrderNote: () -> Unit,
) {
    val editing = cart.editing
    val blocked = previews.any { it.error != null } || sum.isFailure
    var swap by remember { mutableStateOf(false) }
    val types = settings.enabledOrderTypes

    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column {
            // The order type, with Front's swap button (B7).
            Row(
                Modifier.fillMaxWidth().background(Color(settings.typeColor(cart.type))).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(settings.typeName(cart.type), color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { swap = true }, enabled = types.size > 1) { Text("⇄ Change", color = Color.White, fontWeight = FontWeight.Bold) }
                    DropdownMenu(swap, onDismissRequest = { swap = false }) {
                        types.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { cart.type = t.type; swap = false }) }
                    }
                }
            }
            editing?.let {
                Row(Modifier.fillMaxWidth().background(WarnSoft).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Changing order #${it.order.number}: a CHANGED ticket will print", color = Warn, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancelChange) { Text("Stop") }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Optional unless the owner made this order type require it (Settings → Ordering).
                val required = settings.requiresCustomer(cart.type)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(cart.customerName, { cart.customerName = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(if (required) "Name (required)" else "Name") })
                    OutlinedTextField(
                        cart.customerPhone, { cart.customerPhone = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
                if (cart.type.takesPickupTime) {
                    val pickup = cart.pickupAt
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
                        Text("Ready " + (pickup?.let { CLOCK.format(it.atZone(settings.zone)) } ?: "ASAP"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, end = 4.dp))
                        FilterChip(selected = pickup == null, onClick = { cart.pickupAt = null }, label = { Text("ASAP") })
                        PICKUP_MINUTES.forEach { m ->
                            FilterChip(selected = false, onClick = { cart.pickupAt = Instant.now().plus(Duration.ofMinutes(m.toLong())) }, label = { Text("$m") })
                        }
                    }
                }
            }
            HorizontalDivider()

            LazyColumn(Modifier.weight(1f)) {
                if (previews.isEmpty()) {
                    item { Text("No item selected", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp)) }
                }
                items(previews, key = { it.key }) { p -> CartLineRow(p, p.key == cart.selected, language) { onSelect(p) } }
            }

            HorizontalDivider()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val t = sum.getOrNull()
                Row {
                    Amount("Subtotal", money(t?.subtotalCents ?: 0), Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    Amount("Discount", "-" + money(t?.discountCents ?: 0), Modifier.weight(1f))
                }
                Row {
                    Text(cart.discount?.let { describeDiscount(it) } ?: "", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    Amount("Tax", money(t?.taxCents ?: 0), Modifier.weight(1f))
                }
                sum.exceptionOrNull()?.let { Text(it.message ?: "", color = Red, fontWeight = FontWeight.SemiBold) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOrderNote) { Text(if (cart.note.isEmpty()) "✎ Order note" else "✎ “${cart.note}”", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.weight(1f))
                    val count = itemCount(previews.mapNotNull { it.line })
                    Text("$count item${if (count == 1) "" else "s"}  ", color = Muted)
                    Text(money(t?.totalCents ?: 0), color = Red, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                sent?.let { Banner(it) }
                error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !busy && cart.lines.isNotEmpty() && editing == null,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Clear") }
                    Button(
                        onClick = onSend,
                        enabled = !busy && cart.lines.isNotEmpty() && !blocked,
                        modifier = Modifier.weight(2f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                    ) {
                        Text(
                            when {
                                busy -> "Sending…"
                                editing != null -> "Save & print CHANGED"
                                else -> "Send"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Amount(label: String, value: String, modifier: Modifier) {
    Row(modifier) {
        Text(label, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(value)
    }
}

@Composable
private fun CartLineRow(p: Preview, selected: Boolean, language: TicketLanguage, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) Selected else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${p.input.quantity}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            val line = p.line
            if (line == null) {
                Text("Can't be sent", fontWeight = FontWeight.SemiBold)
                Text(p.error ?: "", color = Red, style = MaterialTheme.typography.bodySmall)
            } else {
                val (name, alt) = displayNames(line.name, line.altName, language)
                val code = if (line.code.isNotEmpty()) "${line.code}. " else if (line.open) "Open: " else ""
                Text(code + name, fontWeight = FontWeight.SemiBold)
                alt?.let { Text(code + it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                line.options.filter { !it.inName }.takeIf { it.isNotEmpty() }?.let { shown -> Text(shown.joinToString(" · ") { it.name }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall) }
                line.choices.forEach { c ->
                    Text(c.name + if (c.priceCents > 0) "  +${money(c.priceCents)}" else "", style = MaterialTheme.typography.bodySmall)
                }
                line.modifiers.forEach { m ->
                    Text(
                        describeModifier(m.action, m.name) + if (m.priceCents > 0) "  +${money(m.priceCents)}" else "",
                        color = premodColor(m.action), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                    )
                }
                line.note?.let { Text("“$it”", color = Warn, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            p.line?.let { l ->
                Text(money(linePrice(l)))
                if (l.overrideCents != null) StruckPrice(menuUnitPrice(l) * l.quantity)
            }
        }
    }
}

/**
 * Front's tool column, kept short: the everyday buttons, with Price, Discount, Up/Down and notes
 * behind More (the backlog's "simpler than Front" idea).
 */
@Composable
private fun ToolColumn(
    hasLine: Boolean,
    canMod: Boolean,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onQty: () -> Unit,
    onVoid: () -> Unit,
    onMod: () -> Unit,
    onPrice: () -> Unit,
    onDiscount: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDishNote: () -> Unit,
    onOrderNote: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    Column(Modifier.width(88.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        Tool("+", "Add", hasLine, onPlus)
        Tool("−", "Reduce", hasLine, onMinus)
        Tool("123", "QTY", hasLine, onQty)
        Tool("✕", "Void", hasLine, onVoid)
        Tool("≡", "MOD", canMod, onMod)
        Spacer(Modifier.weight(1f))
        Box {
            Tool("⋯", "More", true) { more = true }
            DropdownMenu(more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text("Price") }, enabled = hasLine, onClick = { more = false; onPrice() })
                DropdownMenuItem(text = { Text("Discount") }, onClick = { more = false; onDiscount() })
                DropdownMenuItem(text = { Text("Move up") }, enabled = hasLine, onClick = { more = false; onUp() })
                DropdownMenuItem(text = { Text("Move down") }, enabled = hasLine, onClick = { more = false; onDown() })
                DropdownMenuItem(text = { Text("Note for this dish") }, enabled = hasLine, onClick = { more = false; onDishNote() })
                DropdownMenuItem(text = { Text("Note for the order") }, onClick = { more = false; onOrderNote() })
            }
        }
    }
}

@Composable
private fun Tool(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (enabled) MaterialTheme.colorScheme.onSurface else Line
        Text(icon, color = color, style = MaterialTheme.typography.titleLarge)
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
    HorizontalDivider()
}

@Composable
private fun MenuPane(
    app: AppContainer,
    menu: Menu,
    language: TicketLanguage,
    search: String,
    onSearch: (String) -> Unit,
    modifier: Modifier,
    onTap: (MenuItem) -> Unit,
    onOpenItem: () -> Unit,
    onLanguage: (TicketLanguage) -> Unit,
) {
    var categoryId by remember { mutableStateOf(menu.categories.firstOrNull()?.id) }
    val category = menu.categories.find { it.id == categoryId } ?: menu.categories.firstOrNull()
    val searching = search.isNotBlank()
    val items = if (searching) searchMenu(menu, search) else category?.items.orEmpty()

    Row(modifier.fillMaxHeight()) {
        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    search, onSearch, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search: number or name, e.g. L6, lo mein, 撈麵") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedButton(onClick = { onLanguage(if (language == TicketLanguage.ZH) TicketLanguage.EN else TicketLanguage.ZH) }, modifier = Modifier.height(56.dp)) {
                    Text(if (language == TicketLanguage.ZH) "中文" else "EN", style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = onOpenItem, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) { Text("Open Item") }
            }
            if (searching) {
                Text(if (items.isEmpty()) "No dish matches “$search”" else "Matches for “$search”", style = MaterialTheme.typography.titleLarge)
            } else if (category != null) {
                val (name, alt) = displayNames(category.name, category.altName, language)
                Column {
                    Text(name + (alt?.let { " $it" } ?: ""), style = MaterialTheme.typography.titleLarge)
                    category.note?.let { Text(it, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item -> DishTile(item, language) { onTap(item) } }
            }
        }
        // Front's category buttons, two across.
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.width(236.dp).fillMaxHeight().background(Rail).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(menu.categories, key = { it.id }) { c ->
                val on = !searching && c.id == category?.id
                val (name, alt) = displayNames(c.name, c.altName, language)
                Column(
                    Modifier
                        .heightIn(min = 72.dp)
                        .background(if (on) CategoryOn else RailActive, RoundedCornerShape(6.dp))
                        .clickable { categoryId = c.id; onSearch("") }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(name, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    alt?.let { Text(it, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun DishTile(item: MenuItem, language: TicketLanguage, onClick: () -> Unit) {
    val (name, alt) = displayNames(item.name, item.altName, language)
    val code = if (item.code.isNotEmpty()) "${item.code}. " else ""
    Column(
        Modifier
            .heightIn(min = 118.dp)
            .background(if (item.available) Color.White else MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable(enabled = item.available, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row {
            Text(code + name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = if (item.available) MaterialTheme.colorScheme.onSurface else Muted)
            if (item.spicy) Box(Modifier.padding(start = 4.dp, top = 4.dp).size(8.dp).background(Red, CircleShape))
        }
        alt?.let { Text(code + it, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.weight(1f))
        Text(if (item.available) fromPriceLabel(item) else "Sold out", color = if (item.available) Red else Muted, fontWeight = FontWeight.SemiBold)
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
