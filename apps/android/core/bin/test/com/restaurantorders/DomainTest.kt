// Ported from apps/frontend/test/domain.test.ts: both apps follow the same rules.
package com.restaurantorders

import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuCategory
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.MenuOption
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.Modifier
import com.restaurantorders.domain.ModifierList
import com.restaurantorders.domain.ModifierPick
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.Order
import com.restaurantorders.domain.OrderException
import com.restaurantorders.domain.OrderLine
import com.restaurantorders.domain.OrderStatus
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.PreModifier.ADD
import com.restaurantorders.domain.PreModifier.EXTRA
import com.restaurantorders.domain.PreModifier.LIGHT
import com.restaurantorders.domain.PreModifier.NO
import com.restaurantorders.domain.PreModifier.SIDE
import com.restaurantorders.domain.PreModifier.SUB
import com.restaurantorders.domain.Role
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.Totals
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.buildTicket
import com.restaurantorders.domain.can
import com.restaurantorders.domain.canManage
import com.restaurantorders.domain.checkPicks
import com.restaurantorders.domain.describeModifier
import com.restaurantorders.domain.linePrice
import com.restaurantorders.domain.listsForItem
import com.restaurantorders.domain.money
import com.restaurantorders.domain.parseMoney
import com.restaurantorders.domain.priceLabel
import com.restaurantorders.domain.togglePick
import com.restaurantorders.domain.totals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainTest {
    private val veg = ModifierList(
        "veg", "Vegetables", listOf(NO, EXTRA, ADD),
        listOf(Modifier("onion", "Onion", "洋蔥", 100), Modifier("broc", "Broccoli", null, 150)),
    )
    private val sauce = ModifierList("sauce", "Sauce & spice", listOf(NO, LIGHT, SIDE), listOf(Modifier("sauce1", "Sauce", null, 0)))
    private val protein = ModifierList("prot", "Add protein", listOf(ADD, SUB), listOf(Modifier("shrimp", "Shrimp", null, 250)))

    private val loMein = MenuItem(
        id = "lm", categoryId = "c1", code = "24", name = "Chicken Lo Mein", altName = null, priceCents = 565,
        spicy = false, available = true,
        optionGroups = listOf(MenuOptionGroup("Size", listOf(MenuOption("pt", "Pt", 0), MenuOption("qt", "Qt", 215)))),
        modifierListIds = listOf("prot", "veg"),
    )
    private val soldOut = loMein.copy(id = "so", code = "99", name = "Sold out dish", available = false, optionGroups = emptyList(), modifierListIds = emptyList())
    private val category = MenuCategory("c1", "Lo Mein", null, listOf("veg", "sauce"), listOf(loMein, soldOut))
    private val menu = Menu(listOf(category), listOf(veg, sauce, protein))

    private var n = 0
    private val id = { "l${++n}" }

    @Test
    fun `offers the category's lists, then the dish's own, each once`() {
        assertEquals(listOf("veg", "sauce", "prot"), listsForItem(loMein, category, menu.modifierLists).map { it.id })
    }

    @Test
    fun `toggles a pick off, and replaces a different action on the same ingredient`() {
        var picks = togglePick(emptyList(), ModifierPick(NO, "onion"))
        picks = togglePick(picks, ModifierPick(EXTRA, "onion"))
        assertEquals(listOf(ModifierPick(EXTRA, "onion")), picks)
        assertEquals(emptyList(), togglePick(picks, ModifierPick(EXTRA, "onion")))
    }

    @Test
    fun `rejects an action the list doesn't allow and ingredients from other dishes`() {
        val lists = listOf(veg, sauce)
        assertContains(checkPicks(listOf(ModifierPick(LIGHT, "onion")), lists)!!, "LIGHT is not allowed for Onion")
        assertContains(checkPicks(listOf(ModifierPick(ADD, "shrimp")), lists)!!, "not offered")
        assertNull(checkPicks(listOf(ModifierPick(NO, "onion"), ModifierPick(SIDE, "sauce1")), lists))
    }

    @Test
    fun `reads like the kitchen says it`() {
        assertEquals("NO Onion", describeModifier(NO, "Onion"))
        assertEquals("Sauce ON SIDE", describeModifier(SIDE, "Sauce"))
    }

    @Test
    fun `charges ADD and EXTRA, not NO, LIGHT or ON SIDE`() {
        val picks = listOf(ModifierPick(NO, "onion"), ModifierPick(EXTRA, "broc"), ModifierPick(ADD, "shrimp"), ModifierPick(SIDE, "sauce1"))
        val line = buildLine(menu, NewOrderLine("lm", 2, listOf("qt"), picks), id)
        assertEquals(listOf(0, 150, 250, 0), line.modifiers.map { it.priceCents })
        assertEquals((565 + 215 + 150 + 250) * 2, linePrice(line))
    }

    @Test
    fun `rounds tax to the cent`() {
        val line = buildLine(menu, NewOrderLine("lm", 1, listOf("pt")), id)
        assertEquals(Totals(565, 50, 615), totals(listOf(line), 0.08875))
    }

    @Test
    fun `refuses sold-out dishes, missing sizes and bad picks`() {
        assertContains(assertFailsWith<OrderException> { buildLine(menu, NewOrderLine("so", 1), id) }.message!!, "sold out")
        assertContains(assertFailsWith<OrderException> { buildLine(menu, NewOrderLine("lm", 1), id) }.message!!, "Pick one size")
        assertFailsWith<OrderException> { buildLine(menu, NewOrderLine("lm", 1, listOf("pt"), listOf(ModifierPick(LIGHT, "onion"))), id) }
    }

    @Test
    fun `formats and parses money`() {
        assertEquals("$6.35", money(635))
        assertEquals("$0.05", money(5))
        assertEquals(760, parseMoney("$7.6"))
        assertNull(parseMoney("7.605"))
        assertEquals("$5.65–$7.80", priceLabel(loMein))
    }

    private fun order(lines: List<OrderLine>) = Order(
        id = "o1", number = 7, businessDate = LocalDate.parse("2026-10-09"), type = OrderType.PHONE, status = OrderStatus.OPEN,
        customerName = "Kim", customerPhone = null, pickupAt = null, note = null, lines = lines,
        subtotalCents = 0, taxCents = 0, totalCents = 0, createdAt = Instant.parse("2026-10-09T22:00:00Z"),
    )

    private val zone = ZoneId.of("America/New_York")
    private val a = buildLine(menu, NewOrderLine("lm", 1, listOf("qt"), listOf(ModifierPick(NO, "onion")))) { "a" }
    private val b = buildLine(menu, NewOrderLine("lm", 1, listOf("pt"))) { "b" }

    @Test
    fun `prints modifications in capitals under the dish, with the Chinese name`() {
        val ticket = buildTicket(order(listOf(a)), TicketKind.NEW, TicketLanguage.BOTH, zone)
        val text = ticket.map { it.text }
        assertContains(text, "#7  PHONE")
        assertContains(text, "6:00 PM  2026-10-09")
        assertContains(text, "1 x 24 Chicken Lo Mein")
        assertContains(text, "   QT")
        assertContains(text, "   NO ONION 洋蔥")
        assertTrue(ticket.first { it.text == "   NO ONION 洋蔥" }.let { it.bold && it.style == LineStyle.TALL })
    }

    @Test
    fun `marks added, changed and removed dishes on a CHANGED ticket`() {
        val c = buildLine(menu, NewOrderLine("lm", 3, listOf("pt"))) { "c" }
        val after = order(listOf(a.copy(quantity = 2), c))
        val ticket = buildTicket(after, TicketKind.CHANGED, TicketLanguage.BOTH, zone, previous = order(listOf(a, b)))
        val text = ticket.map { it.text }
        assertTrue(ticket[0].invert && "CHANGED" in ticket[0].text)
        assertContains(text, "* 2 x 24 Chicken Lo Mein")
        assertContains(text, "+ 3 x 24 Chicken Lo Mein")
        assertContains(text, "REMOVED:")
        assertContains(text, "- 1 x 24 Chicken Lo Mein")
    }

    @Test
    fun `keeps settings for owners and staff management above cashiers`() {
        assertTrue(can(Role.CASHIER, Permission.SOLD_OUT))
        assertTrue(!can(Role.CASHIER, Permission.MENU))
        assertTrue(!can(Role.MANAGER, Permission.SETTINGS))
        assertTrue(canManage(Role.MANAGER, Role.CASHIER))
        assertTrue(!canManage(Role.MANAGER, Role.OWNER))
    }
}
