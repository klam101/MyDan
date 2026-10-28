package com.restaurantorders.app

import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.AppJson
import com.restaurantorders.app.data.OrderInput
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.domain.ModifierPick
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
    fun `cancelling prints CANCELLED and closes the order`() = runBlocking {
        val (created, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(loMein())))
        val (cancelled, job) = app.orders.cancel(created.order.id)
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
            "phone order",
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
}
