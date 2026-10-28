// Orders: saving, changing and voiding them, and the kitchen tickets each step prints.
// The rules started from createOrder / updateOrder / cancelOrder in apps/frontend/src/data/mockApi.ts.
package com.restaurantorders.app.data

import androidx.room.withTransaction
import com.restaurantorders.app.printing.PrintQueue
import com.restaurantorders.domain.Discount
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.Order
import com.restaurantorders.domain.OrderLine
import com.restaurantorders.domain.OrderStatus
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.buildTicket
import com.restaurantorders.domain.describeDiscount
import com.restaurantorders.domain.findItem
import com.restaurantorders.domain.money
import com.restaurantorders.domain.totals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** What the cashier sends: a new order, or the new version of one being changed. */
data class OrderInput(
    val type: OrderType,
    val customerName: String = "",
    val customerPhone: String = "",
    /** When a pick-up or phone order will be collected; null means as soon as it's ready. */
    val pickupAt: Instant? = null,
    val note: String = "",
    val lines: List<NewOrderLine>,
    val discount: Discount? = null,
    /** The manager who approved each risky part of this order, when a cashier sends it (A4). */
    val approvedBy: Map<Permission, String> = emptyMap(),
)

/** A risky part of an order that the sender's role can't do alone. */
data class NeededApproval(val permission: Permission, val what: String)

/** An order plus what the screens show around it. */
data class OrderView(
    val order: Order,
    val createdByName: String,
    val readyAt: Instant?,
    val approvals: List<Approval> = emptyList(),
    val voidedByName: String? = null,
)

class OrderRepository(
    private val db: AppDatabase,
    private val menu: MenuRepository,
    private val staff: StaffRepository,
    private val printing: PrintQueue,
    private val session: Session,
    /** Printing runs here, so a ticket still goes out if the cashier leaves the screen. */
    private val appScope: CoroutineScope,
    private val now: () -> Instant,
) {
    private val dao = db.orders()

    private suspend fun settings() = db.settings().get() ?: throw ActionException("The restaurant isn't set up yet")

    /** Today's orders in the restaurant's time zone, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watchToday(): Flow<List<OrderView>> = db.settings().watch().flatMapLatest { s ->
        if (s == null) return@flatMapLatest flowOf(emptyList())
        dao.watchDay(LocalDate.now(s.zone).toString()).map { list -> list.map { it.toView() } }
    }

    fun watch(id: String): Flow<OrderView?> = dao.watch(id).map { it?.toView() }

    fun watchTickets(orderId: String): Flow<List<PrintJobEntity>> = db.printJobs().watchForOrder(orderId)

    /** Order id → status of its newest ticket. */
    fun watchLatestTicketStatus(): Flow<Map<String, String>> =
        db.printJobs().watchLatestStatuses().map { list -> list.associate { it.orderId to it.status } }

    suspend fun get(id: String): OrderView = dao.get(id)?.toView() ?: throw ActionException("Order not found")

    /**
     * What in this order needs a manager when the signed-in person sends it: a changed price, an
     * open item above the Settings limit, a discount, or (when changing a sent order) dishes
     * taken away. Empty when their role may do it all.
     */
    suspend fun approvalsNeeded(orderId: String?, input: OrderInput): List<NeededApproval> {
        val actor = session.require(Permission.ORDER)
        val previous = orderId?.let { get(it).order }
        return risky(previous, input, settings()).filter { !actor.can(it.permission) }
    }

    /** Saves the order, then prints the kitchen ticket. Returns the order and how the ticket went. */
    suspend fun create(input: OrderInput): Pair<OrderView, PrintJobEntity> {
        val actor = session.require(Permission.ORDER)
        val settings = settings()
        val details = compose(input, settings)
        val approvals = approve(risky(null, input, settings), input, actor)
        val at = now()
        val date = at.atZone(settings.zone).toLocalDate().toString()
        val entity = db.withTransaction {
            OrderEntity(
                id = newId(),
                number = dao.nextNumber(date),
                businessDate = date,
                type = input.type.name,
                status = OrderStatus.OPEN.name,
                customerName = details.customerName,
                customerPhone = details.customerPhone,
                pickupAt = input.pickupAt?.toEpochMilli(),
                note = details.note,
                linesJson = AppJson.encodeToString(details.lines),
                subtotalCents = details.subtotal,
                taxCents = details.tax,
                totalCents = details.total,
                createdAt = at.toEpochMilli(),
                updatedAt = at.toEpochMilli(),
                createdById = actor.id,
                createdByName = actor.name,
                discountJson = input.discount?.let { AppJson.encodeToString(it) },
                approvalsJson = AppJson.encodeToString(approvals),
            ).also { dao.insert(it) }
        }
        val view = entity.toView()
        return view to print(view.order, TicketKind.NEW)
    }

    /** Replaces an open order's dishes and details, and prints a CHANGED ticket. */
    suspend fun update(id: String, input: OrderInput): Pair<OrderView, PrintJobEntity> {
        val actor = session.require(Permission.ORDER)
        val current = dao.get(id) ?: throw ActionException("Order not found")
        if (current.status == OrderStatus.CANCELLED.name || current.status == OrderStatus.PICKED_UP.name) {
            throw ActionException("Order #${current.number} is closed and can't be changed")
        }
        val previous = current.toView()
        val settings = settings()
        val details = compose(input, settings)
        val approvals = approve(risky(previous.order, input, settings), input, actor)
        val updated = current.copy(
            type = input.type.name,
            customerName = details.customerName,
            customerPhone = details.customerPhone,
            pickupAt = input.pickupAt?.toEpochMilli(),
            note = details.note,
            linesJson = AppJson.encodeToString(details.lines),
            subtotalCents = details.subtotal,
            taxCents = details.tax,
            totalCents = details.total,
            updatedAt = now().toEpochMilli(),
            version = current.version + 1,
            discountJson = input.discount?.let { AppJson.encodeToString(it) },
            approvalsJson = AppJson.encodeToString(previous.approvals + approvals),
        )
        dao.update(updated)
        val view = updated.toView()
        return view to print(view.order, TicketKind.CHANGED, previous.order)
    }

    /**
     * Voids an order with a reason (backlog E5): it stays in history, greyed out, and the kitchen
     * gets a VOID ticket. A cashier needs a manager's approval.
     */
    suspend fun cancel(id: String, reason: String, approvedById: String? = null): Pair<OrderView, PrintJobEntity> {
        val (actor, approver) = staff.authorize(Permission.VOID, approvedById)
        val current = dao.get(id) ?: throw ActionException("Order not found")
        if (current.status == OrderStatus.PICKED_UP.name) throw ActionException("Order #${current.number} was already picked up")
        if (current.status == OrderStatus.CANCELLED.name) throw ActionException("Order #${current.number} is already voided")
        val why = reason.trim().ifEmpty { throw ActionException("Pick or type a reason for the void") }
        val approval = Approval(Permission.VOID, (approver ?: actor).name, actor.name, now().toEpochMilli(), "Void: $why")
        val updated = current.copy(
            status = OrderStatus.CANCELLED.name,
            updatedAt = now().toEpochMilli(),
            voidReason = why,
            voidedByName = (approver ?: actor).name,
            approvalsJson = AppJson.encodeToString(current.toView().approvals + approval),
        )
        dao.update(updated)
        val view = updated.toView()
        return view to print(view.order, TicketKind.CANCELLED)
    }

    /** Prints the order's ticket again (a voided order prints its VOID ticket again). */
    suspend fun reprint(id: String): PrintJobEntity {
        session.require(Permission.ORDERS)
        val order = get(id).order
        return print(order, if (order.status == OrderStatus.CANCELLED) TicketKind.CANCELLED else TicketKind.REPRINT)
    }

    /** Sends a ticket that failed again, exactly as it was. */
    suspend fun retryTicket(jobId: String): PrintJobEntity {
        session.require(Permission.ORDERS)
        return appScope.async { printing.retry(jobId) }.await()
    }

    /** Cooking → Ready → Picked up (or back to Cooking). */
    suspend fun setStatus(id: String, status: OrderStatus) {
        session.require(Permission.KITCHEN)
        require(status != OrderStatus.CANCELLED) { "Use cancel()" }
        val current = dao.get(id) ?: throw ActionException("Order not found")
        if (current.status == OrderStatus.CANCELLED.name) throw ActionException("Order #${current.number} was voided")
        val readyAt = when (status) {
            OrderStatus.OPEN -> null
            else -> current.readyAt ?: now().toEpochMilli()
        }
        dao.update(current.copy(status = status.name, readyAt = readyAt, updatedAt = now().toEpochMilli()))
    }

    private suspend fun print(order: Order, kind: TicketKind, previous: Order? = null): PrintJobEntity {
        val settings = settings()
        val ticket = buildTicket(order, kind, settings.language, settings.zone, previous, settings.typeName(order.type))
        return appScope.async { printing.print(order.id, kind, ticket) }.await()
    }

    /** The parts of an order (or of a change to `previous`) that only a manager or owner may do. */
    private suspend fun risky(previous: Order?, input: OrderInput, settings: SettingsEntity): List<NeededApproval> = buildList {
        val current = menu.get()
        val before = previous?.lines.orEmpty().associateBy { it.id }
        for (line in input.lines) {
            val old = line.lineId?.let { before[it] }
            val price = line.unitPriceCents
            if (price != null && price != old?.overrideCents) {
                val name = line.openItem?.name ?: findItem(current, line.menuItemId)?.first?.name ?: "a dish"
                add(NeededApproval(Permission.PRICE, "Change the price of $name to ${money(price)}"))
            }
            val open = line.openItem
            if (open != null && old == null && open.priceCents > settings.openItemLimitCents) {
                add(NeededApproval(Permission.PRICE, "Ring up ${open.name} at ${money(open.priceCents)} (over ${money(settings.openItemLimitCents)})"))
            }
        }
        val discount = input.discount
        if (discount != null && discount != previous?.discount) {
            add(NeededApproval(Permission.DISCOUNT, "Take ${describeDiscount(discount)} off the order"))
        }
        if (previous != null) {
            val after = input.lines.mapNotNull { l -> l.lineId?.let { it to l.quantity } }.toMap()
            val taken = previous.lines.filter { old -> (after[old.id] ?: 0) < old.quantity }
            if (taken.isNotEmpty()) {
                add(NeededApproval(Permission.VOID, "Take away dishes the kitchen already has: ${taken.joinToString(", ") { it.name }}"))
            }
        }
    }

    /** Checks each risky part was approved by someone allowed, and records who. */
    private suspend fun approve(needed: List<NeededApproval>, input: OrderInput, actor: Staff): List<Approval> =
        needed.distinctBy { it.permission to it.what }.map { n ->
            val (_, approver) = staff.authorize(n.permission, input.approvedBy[n.permission])
            Approval(n.permission, (approver ?: actor).name, actor.name, now().toEpochMilli(), n.what)
        }

    private data class Details(
        val customerName: String?,
        val customerPhone: String?,
        val note: String?,
        val lines: List<OrderLine>,
        val subtotal: Int,
        val tax: Int,
        val total: Int,
    )

    /** Checks the order and builds its lines from the current menu, with prices from the menu. */
    private suspend fun compose(input: OrderInput, settings: SettingsEntity): Details {
        if (input.lines.isEmpty()) throw ActionException("An order needs at least one dish")
        val name = input.customerName.trim().ifEmpty { null }
        val phone = input.customerPhone.trim().ifEmpty { null }
        if (settings.requiresCustomer(input.type) && name == null && phone == null) {
            throw ActionException("A ${settings.typeName(input.type)} order needs the customer's name or phone number")
        }
        val current = menu.get()
        val lines = input.lines.map { buildLine(current, it) { newId() } }
        val sum = totals(lines, settings.taxRate, input.discount)
        return Details(name, phone, input.note.trim().ifEmpty { null }, lines, sum.subtotalCents, sum.taxCents, sum.totalCents)
    }
}

fun OrderEntity.toView(): OrderView {
    val discount = discountJson?.let { AppJson.decodeFromString<Discount>(it) }
    return OrderView(
        order = Order(
            id = id,
            number = number,
            businessDate = LocalDate.parse(businessDate),
            type = OrderType.valueOf(type),
            status = OrderStatus.valueOf(status),
            customerName = customerName,
            customerPhone = customerPhone,
            pickupAt = pickupAt?.let(Instant::ofEpochMilli),
            note = note,
            lines = AppJson.decodeFromString<List<OrderLine>>(linesJson),
            subtotalCents = subtotalCents,
            taxCents = taxCents,
            totalCents = totalCents,
            createdAt = Instant.ofEpochMilli(createdAt),
            version = version,
            discount = discount,
            // Total = subtotal − discount + tax, so the discount needs no column of its own.
            discountCents = subtotalCents + taxCents - totalCents,
            voidReason = voidReason,
        ),
        createdByName = createdByName,
        readyAt = readyAt?.let(Instant::ofEpochMilli),
        approvals = AppJson.decodeFromString<List<Approval>>(approvalsJson),
        voidedByName = voidedByName,
    )
}
