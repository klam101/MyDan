// Checks the real kitchen printer from a computer, before the tablet app exists:
//   gradlew :core:printTest --args="192.168.1.50"            English ticket
//   gradlew :core:printTest --args="192.168.1.50 --chinese"  also try Chinese characters
package com.restaurantorders.tools

import com.restaurantorders.domain.ModifierPick
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.Order
import com.restaurantorders.domain.OrderStatus
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.buildLine
import com.restaurantorders.domain.buildTestTicket
import com.restaurantorders.domain.buildTicket
import com.restaurantorders.domain.loadStarterMenu
import com.restaurantorders.domain.totals
import com.restaurantorders.printing.EscPos
import com.restaurantorders.printing.NetworkPrinter
import com.restaurantorders.printing.PrinterException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val host = args.firstOrNull { !it.startsWith("--") } ?: run {
        println("Usage: gradlew :core:printTest --args=\"<printer ip> [port] [--chinese]\"")
        exitProcess(2)
    }
    val port = args.drop(1).firstOrNull { it.toIntOrNull() != null }?.toInt() ?: 9100
    val chinese = "--chinese" in args
    val zone = ZoneId.systemDefault()
    val now = Instant.now()

    var n = 0
    val menu = loadStarterMenu { prefix -> "$prefix${++n}" }
    val items = menu.categories.flatMap { it.items }
    val mods = menu.modifierLists.flatMap { it.modifiers }
    fun dish(code: String) = items.first { it.code == code }
    fun mod(name: String) = mods.first { it.name == name }.id

    val l6 = dish("L6")
    val friedRice = dish("19")
    val lines = listOf(
        buildLine(menu, NewOrderLine(l6.id, 1, modifiers = listOf(ModifierPick(PreModifier.NO, mod("Onion")), ModifierPick(PreModifier.EXTRA, mod("Spicy"))))) { "a" },
        buildLine(
            menu,
            NewOrderLine(
                friedRice.id,
                2,
                optionIds = friedRice.optionGroups.map { g -> g.options.first { it.name in setOf("Qt", "Chicken") }.id },
                modifiers = listOf(ModifierPick(PreModifier.ADD, mod("Shrimp"))),
                note = "Cut in half",
            ),
        ) { "b" },
    )
    val sum = totals(lines, 0.08875)
    val order = Order(
        id = "test", number = 1, businessDate = LocalDate.now(zone), type = OrderType.PHONE, status = OrderStatus.OPEN,
        customerName = "Test", customerPhone = null, pickupAt = null, note = "Sample order from printTest", lines = lines,
        subtotalCents = sum.subtotalCents, taxCents = sum.taxCents, totalCents = sum.totalCents, createdAt = now,
    )

    val language = if (chinese) TicketLanguage.BOTH else TicketLanguage.EN
    val tickets = listOf(
        buildTestTicket("Printer check", "$host:$port", now, zone),
        buildTicket(order, TicketKind.NEW, language, zone),
    )
    val printer = NetworkPrinter(host, port)
    for (ticket in tickets) {
        println(EscPos.text(ticket))
        try {
            printer.send(EscPos.render(ticket, chinese))
            println("-> sent to $host:$port\n")
        } catch (e: PrinterException) {
            println("-> FAILED: ${e.message}")
            exitProcess(1)
        }
    }
}
