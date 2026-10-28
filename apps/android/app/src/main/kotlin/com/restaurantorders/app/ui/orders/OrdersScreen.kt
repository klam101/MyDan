package com.restaurantorders.app.ui.orders

import androidx.compose.ui.draw.alpha
import com.restaurantorders.app.data.typeName
import com.restaurantorders.app.data.voidReasons
import com.restaurantorders.app.ui.order.StruckPrice
import com.restaurantorders.app.ui.pin.ApprovalDialog
import com.restaurantorders.domain.describeDiscount
import com.restaurantorders.domain.menuUnitPrice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.AppJson
import com.restaurantorders.app.data.OrderView
import com.restaurantorders.app.data.PrintJobEntity
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.zone
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.TicketPreview
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.theme.Info
import com.restaurantorders.app.ui.theme.InfoSoft
import com.restaurantorders.app.ui.theme.Line
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Ok
import com.restaurantorders.app.ui.theme.OkSoft
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.RedSoft
import com.restaurantorders.app.ui.theme.Warn
import com.restaurantorders.app.ui.theme.WarnSoft
import com.restaurantorders.app.ui.theme.premodColor
import com.restaurantorders.domain.Order
import com.restaurantorders.domain.OrderStatus
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.domain.describeModifier
import com.restaurantorders.domain.linePrice
import com.restaurantorders.domain.money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
fun clock(at: Instant, zone: ZoneId): String = CLOCK.format(at.atZone(zone))

private val STATUS_LABEL = mapOf(
    OrderStatus.OPEN to "Cooking",
    OrderStatus.READY to "Ready",
    OrderStatus.PICKED_UP to "Picked up",
    OrderStatus.CANCELLED to "Voided",
)

@Composable
fun StatusBadge(status: OrderStatus) {
    val (fg, bg) = when (status) {
        OrderStatus.OPEN -> Warn to WarnSoft
        OrderStatus.READY -> Ok to OkSoft
        OrderStatus.PICKED_UP -> Muted to Color(0xFFEEF0F3)
        OrderStatus.CANCELLED -> Red to RedSoft
    }
    Text(
        STATUS_LABEL.getValue(status),
        color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private fun customerLine(order: Order, settings: SettingsEntity) =
    listOfNotNull(settings.typeName(order.type), order.customerName, order.customerPhone).joinToString(" · ")

/** Today's orders on the left, the chosen one on the right. Port of OrdersScreen + OrderDetail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(app: AppContainer, settings: SettingsEntity, onChange: (OrderView) -> Unit) {
    val orders by app.orders.watchToday().collectAsState(initial = emptyList())
    val ticketStatus by app.orders.watchLatestTicketStatus().collectAsState(initial = emptyMap())
    var filter by remember { mutableStateOf<OrderStatus?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val action = rememberAction()

    val q = search.trim().lowercase().removePrefix("#")
    val digits = q.filter(Char::isDigit)
    val shown = orders.filter { v ->
        val o = v.order
        (filter == null || o.status == filter) && (
            q.isEmpty() || o.number.toString() == q ||
                o.customerName?.lowercase()?.contains(q) == true ||
                (digits.length >= 3 && o.customerPhone?.filter(Char::isDigit)?.contains(digits) == true)
            )
    }
    val counted = orders.filter { it.order.status != OrderStatus.CANCELLED }
    val selected = orders.find { it.order.id == selectedId }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's orders", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "${counted.size} order${if (counted.size == 1) "" else "s"} ·${orders.count { it.order.status == OrderStatus.OPEN }} cooking · " +
                        "${money(counted.sumOf { it.order.totalCents })} incl. tax",
                    color = Muted,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf(null) + OrderStatus.entries).forEach { s ->
                    val count = if (s == null) orders.size else orders.count { it.order.status == s }
                    FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text("${s?.let(STATUS_LABEL::getValue) ?: "All"}  $count") })
                }
            }
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Order #, name or phone") })
            action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
            if (shown.isEmpty()) Text("No orders here yet", color = Muted, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.order.id }) { v ->
                    OrderCard(
                        v, settings, ticketStatus[v.order.id], selected = v.order.id == selectedId,
                        onClick = { selectedId = v.order.id },
                        onNext = { next -> action.run { app.orders.setStatus(v.order.id, next) } },
                    )
                }
            }
            // Front's totals strip: count and amount for each status in the list shown (E3).
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                OrderStatus.entries.forEach { s ->
                    val these = shown.filter { it.order.status == s }
                    Text("${these.size} ${STATUS_LABEL.getValue(s)}, ${money(these.sumOf { it.order.totalCents })}", color = if (these.isEmpty()) Muted else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        VerticalDivider()
        Box(Modifier.width(520.dp).fillMaxHeight()) {
            if (selected == null) {
                Text("Tap an order to see it, reprint it or change it.", color = Muted, modifier = Modifier.align(Alignment.Center))
            } else {
                OrderDetail(app, settings, selected, onChange)
            }
        }
    }
}

@Composable
private fun OrderCard(v: OrderView, settings: SettingsEntity, ticket: String?, selected: Boolean, onClick: () -> Unit, onNext: (OrderStatus) -> Unit) {
    val o = v.order
    val zone = settings.zone
    val next = when (o.status) {
        OrderStatus.OPEN -> OrderStatus.READY to "Mark ready"
        OrderStatus.READY -> OrderStatus.PICKED_UP to "Picked up"
        else -> null
    }
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Red else Line),
        // Voided orders stay in the list, greyed out (E5).
        modifier = Modifier.fillMaxWidth().alpha(if (o.status == OrderStatus.CANCELLED) 0.55f else 1f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${o.number}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    StatusBadge(o.status)
                    if (o.version > 1) Tag("Changed", Warn, WarnSoft)
                    if (ticket == PrintStatus.FAILED.name) Tag("Ticket failed", Red, RedSoft)
                    Spacer(Modifier.weight(1f))
                    Text(clock(o.createdAt, zone), color = Muted)
                }
                Text(customerLine(o, settings) + (o.pickupAt?.let { " · pickup ${clock(it, zone)}" } ?: ""), color = Muted)
                Text(o.lines.joinToString("   ") { "${it.quantity}× ${it.code.ifEmpty { it.name }}" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(o.totalCents), fontWeight = FontWeight.Bold)
                next?.let { (status, label) -> OutlinedButton(onClick = { onNext(status) }) { Text(label) } }
            }
        }
    }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Text(
        text.uppercase(), color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val KIND_LABEL = mapOf("NEW" to "New ticket", "CHANGED" to "CHANGED ticket", "CANCELLED" to "VOID ticket", "REPRINT" to "Reprint", "TEST" to "Test")
private val PRINT_LABEL = mapOf("QUEUED" to "Sending…", "PRINTED" to "Printed", "FAILED" to "Did NOT print", "NO_PRINTER" to "No printer set up")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderDetail(app: AppContainer, settings: SettingsEntity, v: OrderView, onChange: (OrderView) -> Unit) {
    val o = v.order
    val zone = settings.zone
    val jobs by app.orders.watchTickets(o.id).collectAsState(initial = emptyList())
    var jobId by remember(o.id) { mutableStateOf<String?>(null) }
    var notice by remember(o.id) { mutableStateOf<BannerMessage?>(null) }
    var voiding by remember { mutableStateOf(false) }
    var approveVoid by remember { mutableStateOf<String?>(null) }
    val action = rememberAction()
    val staff = app.session.staff.collectAsState().value
    val closed = o.status == OrderStatus.CANCELLED || o.status == OrderStatus.PICKED_UP
    val job = jobs.find { it.id == jobId } ?: jobs.lastOrNull()

    fun printed(j: PrintJobEntity, what: String) {
        notice = when (PrintStatus.valueOf(j.status)) {
            PrintStatus.PRINTED -> BannerMessage(BannerKind.OK, "$what Ticket printed ✓")
            PrintStatus.NO_PRINTER -> BannerMessage(BannerKind.WARN, "$what No printer is set up, so tell the kitchen.")
            else -> BannerMessage(BannerKind.ERROR, "$what The ticket did NOT print. ${j.error.orEmpty()}")
        }
        jobId = null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Order #${o.number}", style = MaterialTheme.typography.headlineSmall)
            StatusBadge(o.status)
            if (o.version > 1) Tag("Changed ${o.version - 1}×", Warn, WarnSoft)
        }
        Text(
            customerLine(o, settings) + " · taken ${clock(o.createdAt, zone)} by ${v.createdByName}" +
                (o.pickupAt?.let { " · pickup ${clock(it, zone)}" } ?: "") + (v.readyAt?.let { " · ready ${clock(it, zone)}" } ?: ""),
            color = Muted,
        )
        o.voidReason?.let { Banner(BannerMessage(BannerKind.ERROR, "Voided: $it" + (v.voidedByName?.let { n -> " (approved by $n)" } ?: ""))) }
        notice?.let { Banner(it) }
        action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }

        o.lines.forEach { line ->
            Column(Modifier.fillMaxWidth()) {
                Row {
                    Text("${line.quantity} × ", fontWeight = FontWeight.Bold)
                    if (line.code.isNotEmpty()) Text("${line.code} ", color = Red, fontWeight = FontWeight.Bold)
                    Text((if (line.open) "Open: " else "") + line.name, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(linePrice(line)))
                        if (line.overrideCents != null) StruckPrice(menuUnitPrice(line) * line.quantity)
                    }
                }
                val indent = Modifier.padding(start = 28.dp)
                line.options.filter { !it.inName }.takeIf { it.isNotEmpty() }?.let { shown -> Text(shown.joinToString(" · ") { it.name }, indent, fontWeight = FontWeight.SemiBold) }
                line.choices.forEach { c -> Text(c.name + if (c.priceCents > 0) "  +${money(c.priceCents)}" else "", indent) }
                line.modifiers.forEach { m -> Text(describeModifier(m.action, m.name), indent, color = premodColor(m.action), fontWeight = FontWeight.Bold) }
                line.note?.let { Text("“$it”", indent, color = Warn) }
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        o.note?.let { Text("Note: $it", color = Warn, fontWeight = FontWeight.Bold) }
        Row { Text("Subtotal"); Spacer(Modifier.weight(1f)); Text(money(o.subtotalCents)) }
        o.discount?.let { d -> Row { Text("Discount ${describeDiscount(d)}"); Spacer(Modifier.weight(1f)); Text("-" + money(o.discountCents)) } }
        Row { Text("Tax"); Spacer(Modifier.weight(1f)); Text(money(o.taxCents)) }
        Row {
            Text("Total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(money(o.totalCents), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (o.status) {
                OrderStatus.OPEN -> Button(onClick = { action.run { app.orders.setStatus(o.id, OrderStatus.READY) } }, enabled = !action.busy) { Text("Mark ready") }
                OrderStatus.READY -> {
                    Button(onClick = { action.run { app.orders.setStatus(o.id, OrderStatus.PICKED_UP) } }, enabled = !action.busy) { Text("Picked up") }
                    OutlinedButton(onClick = { action.run { app.orders.setStatus(o.id, OrderStatus.OPEN) } }, enabled = !action.busy) { Text("Not ready yet") }
                }
                else -> {}
            }
            if (!closed && staff?.can(Permission.ORDER) == true) {
                OutlinedButton(onClick = { onChange(v) }, enabled = !action.busy) { Text("Change order") }
            }
            OutlinedButton(onClick = { action.run { printed(app.orders.reprint(o.id), "Reprint:") } }, enabled = !action.busy) { Text("Reprint ticket") }
            if (!closed && staff?.can(Permission.ORDER) == true) {
                OutlinedButton(onClick = { voiding = true }, enabled = !action.busy) { Text("Void order", color = Red) }
            }
        }

        if (v.approvals.isNotEmpty()) {
            Text("Approvals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            v.approvals.forEach { a ->
                val by = if (a.byName == a.forName) a.byName else "${a.byName} for ${a.forName}"
                Text("${clock(Instant.ofEpochMilli(a.at), zone)}  ${a.what} ($by)", color = Muted)
            }
        }

        Text("Kitchen tickets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        jobs.forEach { j ->
            val active = j.id == job?.id
            Row(
                Modifier.fillMaxWidth().background(if (active) InfoSoft else Color.Transparent, RoundedCornerShape(8.dp)).padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { jobId = j.id }, modifier = Modifier.weight(1f)) {
                    Text(KIND_LABEL[j.kind] ?: j.kind, color = if (active) Info else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(clock(Instant.ofEpochMilli(j.createdAt), zone), color = Muted)
                }
                Text(
                    PRINT_LABEL[j.status] ?: j.status,
                    color = when (j.status) { "PRINTED" -> Ok; "FAILED" -> Red; "NO_PRINTER" -> Warn; else -> Info },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp),
                )
                if (j.status == PrintStatus.FAILED.name) {
                    TextButton(onClick = { action.run { printed(app.orders.retryTicket(j.id), "Try again:") } }, enabled = !action.busy) { Text("Try again") }
                }
            }
        }
        job?.let { TicketPreview(AppJson.decodeFromString<List<TicketLine>>(it.ticketJson)) }
    }

    fun void(reason: String, managerId: String?) {
        action.run { printed(app.orders.cancel(o.id, reason, managerId).second, "Order voided.") }
    }

    if (voiding) {
        VoidDialog(
            number = o.number,
            reasons = settings.voidReasons,
            onVoid = { reason ->
                voiding = false
                if (staff?.can(Permission.VOID) == true) void(reason, null) else approveVoid = reason
            },
            onDismiss = { voiding = false },
        )
    }
    approveVoid?.let { reason ->
        ApprovalDialog(
            app, Permission.VOID, "Void order #${o.number}: $reason",
            onApproved = { manager -> approveVoid = null; void(reason, manager.id) },
            onDismiss = { approveVoid = null },
        )
    }
}

/** Void asks why (backlog E5): one of the restaurant's reasons, or typed under Other. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoidDialog(number: Int, reasons: List<String>, onVoid: (String) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<String?>(null) }
    var other by remember { mutableStateOf("") }
    val reason = if (picked == null) other.trim() else picked!!
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void order #$number?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A VOID ticket prints so the kitchen stops cooking it. The order stays in history.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { r -> FilterChip(selected = picked == r, onClick = { picked = r }, label = { Text(r) }) }
                    FilterChip(selected = picked == null, onClick = { picked = null }, label = { Text("Other") })
                }
                if (picked == null) OutlinedTextField(other, { other = it.take(80) }, singleLine = true, label = { Text("Reason") })
            }
        },
        confirmButton = { Button(enabled = reason.isNotEmpty(), onClick = { onVoid(reason) }) { Text("Void order") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
    )
}
