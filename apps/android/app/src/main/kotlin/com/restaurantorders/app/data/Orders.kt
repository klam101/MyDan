// Orders: saving, changing and cancelling them, and the kitchen tickets each step prints.
// The rules match createOrder / updateOrder / cancelOrder in apps/frontend/src/data/mockApi.ts.
package com.restaurantorders.app.data

import androidx.room.withTransaction
import com.restaurantorders.app.printing.PrintQueue
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.Order
import com.restaurantorders.domain.OrderLine
import com.restaurantorders.domain.OrderStatus
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.buildTicket
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
    /** When a phone order will be picked up; null means as soon as it's ready. */
    val pickupAt: Instant? = null,
    val note: String = "",
    val lines: List<NewOrderLine>,
)

/** An order plus what the screens show around it. */
data class OrderView(
    val order: Order,
    val createdByName: String,
    val readyAt: Instant?,
)

class OrderRepository(
    private val db: AppDatabase,
    private val menu: MenuRepository,
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

    suspend fun get(id: String): OrderView = dao.get(id)?.toView() ?: throw ActionException("Order not found")

    /** Saves the order, then prints the kitchen ticket. Returns the order and how the ticket went. */
    suspend fun create(input: OrderInput): Pair<OrderView, PrintJobEntity> {
        val staff = session.require(Permission.ORDER)
        val settings = settings()
        val details = compose(input, settings.taxRate)
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
                createdById = staff.id,
                createdByName = staff.name,
            ).also { dao.insert(it) }
        }
        val view = entity.toView()
        return view to print(view.order, TicketKind.NEW)
    }

    /** Replaces an open order's dishes and details, and prints a CHANGED ticket. */
    suspend fun update(id: String, input: OrderInput): Pair<OrderView, PrintJobEntity> {
        session.require(Permission.ORDER)
        val current = dao.get(id) ?: throw ActionException("Order not found")
        if (current.status == OrderStatus.CANCELLED.name || current.status == OrderStatus.PICKED_UP.name) {
            throw ActionException("Order #${current.number} is closed and can't be changed")
        }
        val previous = current.toView().order
        val details = compose(input, settings().taxRate)
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
        )
        dao.update(updated)
        val view = updated.toView()
        return view to print(view.order, TicketKind.CHANGED, previous)
    }

    suspend fun cancel(id: String): Pair<OrderView, PrintJobEntity> {
        session.require(Permission.ORDER)
        val current = dao.get(id) ?: throw ActionException("Order not found")
        if (current.status == OrderStatus.PICKED_UP.name) throw ActionException("Order #${current.number} was already picked up")
        if (current.status == OrderStatus.CANCELLED.name) throw ActionException("Order #${current.number} is already cancelled")
        val updated = current.copy(status = OrderStatus.CANCELLED.name, updatedAt = now().toEpochMilli())
        dao.update(updated)
        val view = updated.toView()
        return view to print(view.order, TicketKind.CANCELLED)
    }

    /** Prints the order's ticket again (a cancelled order prints its CANCELLED ticket again). */
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
        if (current.status == OrderStatus.CANCELLED.name) throw ActionException("Order #${current.number} was cancelled")
        val readyAt = when (status) {
            OrderStatus.OPEN -> null
            else -> current.readyAt ?: now().toEpochMilli()
        }
        dao.update(current.copy(status = status.name, readyAt = readyAt, updatedAt = now().toEpochMilli()))
    }

    private suspend fun print(order: Order, kind: TicketKind, previous: Order? = null): PrintJobEntity {
        val settings = settings()
        val ticket = buildTicket(order, kind, settings.language, settings.zone, previous)
        return appScope.async { printing.print(order.id, kind, ticket) }.await()
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
    private suspend fun compose(input: OrderInput, taxRate: Double): Details {
        if (input.lines.isEmpty()) throw ActionException("An order needs at least one dish")
        val name = input.customerName.trim().ifEmpty { null }
        val phone = input.customerPhone.trim().ifEmpty { null }
        if (input.type == OrderType.PHONE && name == null && phone == null) {
            throw ActionException("A phone order needs the customer's name or phone number")
        }
        val current = menu.get()
        val lines = input.lines.map { buildLine(current, it) { newId() } }
        val sum = totals(lines, taxRate)
        return Details(name, phone, input.note.trim().ifEmpty { null }, lines, sum.subtotalCents, sum.taxCents, sum.totalCents)
    }
}

fun OrderEntity.toView() = OrderView(
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
    ),
    createdByName = createdByName,
    readyAt = readyAt?.let(Instant::ofEpochMilli),
)
