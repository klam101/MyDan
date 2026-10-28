package com.restaurantorders.app

import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.AppJson
import com.restaurantorders.app.data.OrderInput
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.app.data.discountPresets
import com.restaurantorders.app.data.orderTypes
import com.restaurantorders.app.data.voidReasons
import com.restaurantorders.app.data.withLists
import com.restaurantorders.domain.Discount
import com.restaurantorders.domain.ModifierPick
import com.restaurantorders.domain.OpenItem
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.OrderException
import com.restaurantorders.domain.OrderStatus
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.Role
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.domain.lineInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OrdersTest {
    private val printer = FakePrinter()
    private lateinit var app: AppContainer

    @Before
    fun setUp() {
        app = testApp(printer)
    }

    @After
    fun tearDown() = app.db.close()

    /** Vegetable Lo Mein (Qt) with NO Mushroom and ADD Shrimp, from the starter menu. */
    private suspend fun loMein(quantity: Int = 1): NewOrderLine {
        val menu = app.menu.get()
        val dish = menu.categories.flatMap { it.items }.first { it.code == "26" }
        val mods = menu.modifierLists.flatMap { it.modifiers }
        return NewOrderLine(
            menuItemId = dish.id,
            quantity = quantity,
            optionIds = listOf(dish.optionGroups[0].options.first { it.name == "Qt" }.id),
            modifiers = listOf(
                ModifierPick(PreModifier.NO, mods.first { it.name == "Mushroom" }.id),
                ModifierPick(PreModifier.ADD, mods.first { it.name == "Shrimp" }.id),
            ),
        )
    }

    private fun tickets(orderId: String) = runBlocking { app.orders.watchTickets(orderId).first() }

    private fun ticketText(json: String) = AppJson.decodeFromString<List<TicketLine>>(json).map { it.text }

    @Test
    fun `numbers orders each day, prices from the menu, and prints the ticket`() = runBlocking {
        val (first, job) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        val (second, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        assertEquals(1, first.order.number)
        assertEquals(2, second.order.number)
        // Qt $7.80 + ADD Shrimp $1.75; NO Mushroom is free. Tax 8.875%.
        assertEquals(955, first.order.subtotalCents)
        assertEquals(85, first.order.taxCents)
        assertEquals("Alex", first.createdByName)
        assertEquals(PrintStatus.PRINTED.name, job.status)
        val text = ticketText(job.ticketJson)
        assertContains(text, "   NO MUSHROOM")
        assertContains(text, "   ADD SHRIMP")
        assertEquals(2, printer.sent.size)
    }

    @Test
    fun `keeps the order when the printer is down`() = runBlocking {
        printer.failWith = "The printer at 10.0.0.9 didn't answer."
        val (view, job) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        assertEquals(PrintStatus.FAILED.name, job.status)
        assertNotNull(app.orders.watchToday().first().find { it.order.id == view.order.id })

        printer.failWith = null
        assertEquals(PrintStatus.PRINTED.name, app.orders.retryTicket(job.id).status)
    }

    @Test
    fun `changing an order prints a CHANGED ticket that marks what changed`() = runBlocking {
        val input = OrderInput(OrderType.WALK_IN, lines = listOf(loMein()))
        val (created, _) = app.orders.create(input)
        val again = lineInput(created.order.lines[0]).copy(quantity = 2)
        val (changed, job) = app.orders.update(created.order.id, input.copy(lines = listOf(again)))
        assertEquals(2, changed.order.version)
        assertEquals(created.order.lines[0].id, changed.order.lines[0].id)
        assertEquals("CHANGED", job.kind)
        assertTrue(ticketText(job.ticketJson).any { it.startsWith("* 2 x 26 Vegetable Lo Mein") })
        assertEquals(listOf("NEW", "CHANGED"), tickets(created.order.id).map { it.kind })
    }

    @Test
    fun `voiding prints VOID and closes the order`() = runBlocking {
        val (created, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        val (cancelled, job) = app.orders.cancel(created.order.id, "Wrong order")
        assertEquals(OrderStatus.CANCELLED, cancelled.order.status)
        assertEquals("CANCELLED", job.kind)
        assertContains(ticketText(job.ticketJson)[0], "VOID")
        assertFailsWith<ActionException> { app.orders.update(created.order.id, OrderInput(OrderType.WALK_IN, lines = listOf(loMein()))) }
        assertEquals("CANCELLED", app.orders.reprint(created.order.id).kind)
    }

    @Test
    fun `ready and picked up`() = runBlocking {
        val (created, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        app.orders.setStatus(created.order.id, OrderStatus.READY)
        val ready = app.orders.get(created.order.id)
        assertEquals(OrderStatus.READY, ready.order.status)
        assertNotNull(ready.readyAt)
        app.orders.setStatus(created.order.id, OrderStatus.PICKED_UP)
        assertEquals(OrderStatus.PICKED_UP, app.orders.get(created.order.id).order.status)
    }

    @Test
    fun `refuses empty orders, phone orders without a name, and sold-out dishes`() = runBlocking {
        assertFailsWith<ActionException> { app.orders.create(OrderInput(OrderType.WALK_IN, lines = emptyList())) }
        assertContains(
            assertFailsWith<ActionException> { app.orders.create(OrderInput(OrderType.PHONE, lines = listOf(loMein()))) }.message!!,
            "Phone order",
        )
        app.menu.setAvailable(loMein().menuItemId, false)
        assertContains(assertFailsWith<OrderException> { app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein()))) }.message!!, "sold out")
    }

    @Test
    fun `cashiers take orders under their own name`() = runBlocking {
        app.staff.add("Tony", Role.CASHIER, "3333")
        app.staff.signIn("3333")
        val (view, _) = app.orders.create(OrderInput(OrderType.PHONE, customerName = "Kim", lines = listOf(loMein())))
        assertEquals("Tony", view.createdByName)
        assertEquals("Kim", view.order.customerName)
    }
    /** A cashier (Tony, 3333) signed in, with a manager (Mei, 2222) to approve. */
    private suspend fun cashierWithManager(): String {
        val manager = app.staff.add("Mei", Role.MANAGER, "2222")
        app.staff.add("Tony", Role.CASHIER, "3333")
        app.staff.signIn("3333")
        return manager.id
    }

    @Test
    fun `a cashier's price change or discount needs a manager, who is recorded (A4, B4, B5)`() = runBlocking {
        val managerId = cashierWithManager()
        val cheaper = loMein().copy(unitPriceCents = 800)
        val input = OrderInput(OrderType.WALK_IN, lines = listOf(cheaper), discount = Discount("family", percent = 10.0))
        assertEquals(setOf(Permission.PRICE, Permission.DISCOUNT), app.orders.approvalsNeeded(null, input).map { it.permission }.toSet())
        assertContains(assertFailsWith<ActionException> { app.orders.create(input) }.message!!, "manager's PIN")

        val approved = input.copy(approvedBy = mapOf(Permission.PRICE to managerId, Permission.DISCOUNT to managerId))
        val (view, job) = app.orders.create(approved)
        // $8.00, 10 % off before tax: $7.20 + 8.875 % tax.
        assertEquals(800, view.order.subtotalCents)
        assertEquals(80, view.order.discountCents)
        assertEquals(64, view.order.taxCents)
        assertEquals(784, view.order.totalCents)
        assertEquals(listOf("Mei" to "Tony", "Mei" to "Tony"), view.approvals.map { it.byName to it.forName })
        assertContains(ticketText(job.ticketJson), "DISCOUNT: 10 % family")
        // Changing the order without touching the price or discount needs no new approval.
        val again = view.order.lines.map { lineInput(it) }
        assertEquals(emptyList(), app.orders.approvalsNeeded(view.order.id, input.copy(lines = again)))
    }

    @Test
    fun `taking away a dish the kitchen has needs a manager (E4)`() = runBlocking {
        val managerId = cashierWithManager()
        val (created, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein(2))))
        val fewer = OrderInput(OrderType.WALK_IN, lines = listOf(lineInput(created.order.lines[0]).copy(quantity = 1)))
        assertEquals(listOf(Permission.VOID), app.orders.approvalsNeeded(created.order.id, fewer).map { it.permission })
        assertFailsWith<ActionException> { app.orders.update(created.order.id, fewer) }
        val (changed, _) = app.orders.update(created.order.id, fewer.copy(approvedBy = mapOf(Permission.VOID to managerId)))
        assertEquals(1, changed.order.lines[0].quantity)
        // Adding more needs nobody.
        val more = OrderInput(OrderType.WALK_IN, lines = listOf(lineInput(changed.order.lines[0]).copy(quantity = 3)))
        assertEquals(emptyList(), app.orders.approvalsNeeded(created.order.id, more))
    }

    @Test
    fun `voiding asks a reason and a manager, and prints VOID (E5)`() = runBlocking {
        val managerId = cashierWithManager()
        val (created, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        assertFailsWith<ActionException> { app.orders.cancel(created.order.id, "Customer left") }
        assertContains(assertFailsWith<ActionException> { app.orders.cancel(created.order.id, " ", managerId) }.message!!, "reason")
        val (voided, job) = app.orders.cancel(created.order.id, "Customer left", managerId)
        assertEquals("Customer left", voided.order.voidReason)
        assertEquals("Mei", voided.voidedByName)
        assertContains(ticketText(job.ticketJson), "REASON: Customer left")
        assertEquals("Void: Customer left", voided.approvals.last().what)
    }

    @Test
    fun `open items over the Settings limit need a manager (B2)`() = runBlocking {
        val managerId = cashierWithManager()
        val cup = NewOrderLine("", 1, openItem = OpenItem("Sauce cup", null, 50))
        assertEquals(emptyList(), app.orders.approvalsNeeded(null, OrderInput(OrderType.WALK_IN, lines = listOf(cup))))
        val tray = NewOrderLine("", 1, openItem = OpenItem("Party tray", null, 4500))
        val input = OrderInput(OrderType.WALK_IN, lines = listOf(tray))
        assertEquals(listOf(Permission.PRICE), app.orders.approvalsNeeded(null, input).map { it.permission })
        val (view, job) = app.orders.create(input.copy(approvedBy = mapOf(Permission.PRICE to managerId)))
        assertTrue(view.order.lines.single().open)
        assertContains(ticketText(job.ticketJson), "1 x Party tray")
    }

    @Test
    fun `order types use the restaurant's names, and pick-ups need a customer (B7)`() = runBlocking {
        val settings = app.settings.get()
        val renamed = settings.orderTypes.map { if (it.type == OrderType.PICK_UP) it.copy(name = "Call-in") else it }
        app.settings.save(settings.withLists(renamed, settings.discountPresets, settings.voidReasons))
        assertContains(assertFailsWith<ActionException> { app.orders.create(OrderInput(OrderType.PICK_UP, lines = listOf(loMein()))) }.message!!, "Call-in order needs")
        val (_, job) = app.orders.create(OrderInput(OrderType.PICK_UP, customerPhone = "803-537-9377", lines = listOf(loMein())))
        assertContains(ticketText(job.ticketJson), "#1  CALL-IN")
        val off = settings.orderTypes.map { it.copy(enabled = false) }
        assertContains(assertFailsWith<ActionException> { settings.withLists(off, emptyList(), emptyList()) }.message!!, "at least one")
    }
}
