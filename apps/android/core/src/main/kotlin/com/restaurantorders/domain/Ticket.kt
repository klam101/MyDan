// Kitchen ticket layout, ported from apps/frontend/src/domain/ticket.ts. Turning the lines
// into printer bytes is printing/EscPos.kt.
package com.restaurantorders.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Characters per line on an 80 mm printer in its normal font. */
const val LINE_WIDTH = 42
private val RULE = TicketLine("-".repeat(LINE_WIDTH))
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun clock(at: Instant, zone: ZoneId) = CLOCK.format(at.atZone(zone))

private fun names(name: String, altName: String?, language: TicketLanguage): String = when {
    language == TicketLanguage.ZH && !altName.isNullOrEmpty() -> altName
    language == TicketLanguage.BOTH && !altName.isNullOrEmpty() -> "$name $altName"
    else -> name
}

private fun dishLines(line: OrderLine, language: TicketLanguage, mark: String = ""): List<TicketLine> = buildList {
    val code = if (line.code.isNotEmpty()) "${line.code} " else ""
    add(TicketLine("$mark${line.quantity} x $code${names(line.name, line.altName, language)}", LineStyle.TALL, bold = true))
    if (line.options.isNotEmpty()) {
        add(TicketLine("   ${line.options.joinToString(" / ") { it.name }.uppercase()}", LineStyle.TALL, bold = true))
    }
    for (m in line.modifiers) {
        val alt = if (language != TicketLanguage.EN && !m.altName.isNullOrEmpty()) " ${m.altName}" else ""
        add(TicketLine("   ${describeModifier(m.action, m.name).uppercase()}$alt", LineStyle.TALL, bold = true))
    }
    if (line.note != null) add(TicketLine("   >> ${line.note}", LineStyle.TALL))
}

/**
 * Lays out a kitchen ticket. For a CHANGED ticket, `previous` is the order as the kitchen last
 * saw it: dishes are marked + new, * changed, - removed.
 */
fun buildTicket(order: Order, kind: TicketKind, language: TicketLanguage, zone: ZoneId, previous: Order? = null): List<TicketLine> = buildList {
    when (kind) {
        TicketKind.CANCELLED -> add(TicketLine(" CANCELLED ", LineStyle.BIG, bold = true, invert = true))
        TicketKind.CHANGED -> add(TicketLine(" CHANGED ", LineStyle.BIG, bold = true, invert = true))
        TicketKind.REPRINT -> add(TicketLine("REPRINT", bold = true))
        else -> {}
    }

    val type = if (order.type == OrderType.PHONE) "PHONE" else "WALK-IN"
    add(TicketLine("#${order.number}  $type", LineStyle.BIG, bold = true))
    add(TicketLine("${clock(order.createdAt, zone)}  ${order.businessDate}"))
    order.pickupAt?.let { add(TicketLine("PICKUP ${clock(it, zone)}", LineStyle.TALL, bold = true)) }
    val customer = listOfNotNull(order.customerName, order.customerPhone).joinToString("  ")
    if (customer.isNotEmpty()) add(TicketLine(customer, bold = true))
    add(RULE)

    val before = if (kind == TicketKind.CHANGED) previous else null
    for (line in order.lines) {
        val old = before?.lines?.find { it.id == line.id }
        val mark = when {
            before == null -> ""
            old == null -> "+ "
            lineChanged(old, line) -> "* "
            else -> "  "
        }
        addAll(dishLines(line, language, mark))
    }

    if (before != null) {
        val removed = before.lines.filter { old -> order.lines.none { it.id == old.id } }
        if (removed.isNotEmpty()) {
            add(RULE)
            add(TicketLine("REMOVED:", LineStyle.TALL, bold = true))
            removed.forEach { addAll(dishLines(it, language, "- ")) }
        }
        add(TicketLine("+ new   * changed   - removed"))
    }

    add(RULE)
    order.note?.let { add(TicketLine("NOTE: $it", LineStyle.TALL, bold = true)) }
}

/** A short ticket that proves the printer is connected. */
fun buildTestTicket(restaurantName: String, printerName: String, now: Instant, zone: ZoneId): List<TicketLine> = listOf(
    TicketLine("TEST TICKET", LineStyle.BIG, bold = true),
    TicketLine(restaurantName, bold = true),
    TicketLine("${printerName.ifEmpty { "Kitchen printer" }}  ${clock(now, zone)}"),
    RULE,
    TicketLine("If you can read this, tickets will print here."),
)
