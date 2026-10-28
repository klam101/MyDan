// Turning what the cashier picked into order lines, ported from apps/frontend/src/domain/lines.ts.
package com.restaurantorders.domain

/** A problem the cashier can fix: the message is shown as is. */
class OrderException(message: String) : Exception(message)

/**
 * Turns a picked dish into an order line, copying names and prices from the menu so later
 * menu changes don't rewrite past orders. Throws OrderException when the line is not valid.
 */
fun buildLine(menu: Menu, input: NewOrderLine, newId: () -> String): OrderLine {
    val (item, category) = findItem(menu, input.menuItemId) ?: throw OrderException("That dish is no longer on the menu")
    if (!item.available) throw OrderException("${item.name} is sold out")
    if (input.quantity !in 1..99) throw OrderException("Quantity must be a whole number from 1 to 99")

    val options = item.optionGroups.map { group ->
        val picked = group.options.filter { it.id in input.optionIds }
        if (picked.size != 1) {
            val what = if (group.name == MenuOptionGroup.CHOICE) "choice" else group.name.lowercase()
            throw OrderException("Pick one $what for ${item.name}")
        }
        LineOption(picked[0].id, group.name, picked[0].name, picked[0].extraCents)
    }
    if (input.optionIds.size != options.size) throw OrderException("${item.name} has an option that doesn't belong to it")

    val lists = listsForItem(item, category, menu.modifierLists)
    checkPicks(input.modifiers, lists)?.let { throw OrderException("${item.name}: $it") }
    val modifiers = input.modifiers.map { pick ->
        val modifier = lists.flatMap { it.modifiers }.first { it.id == pick.modifierId }
        LineModifier(pick.action, modifier.id, modifier.name, modifier.altName, modifierCharge(pick.action, modifier.priceCents))
    }

    return OrderLine(
        id = input.lineId ?: newId(),
        menuItemId = item.id,
        code = item.code,
        name = item.name,
        altName = item.altName,
        quantity = input.quantity,
        basePriceCents = item.priceCents,
        options = options,
        modifiers = modifiers,
        note = input.note?.trim()?.ifEmpty { null },
    )
}

/** The input that rebuilds a saved line, for changing an order. */
fun lineInput(line: OrderLine) = NewOrderLine(
    menuItemId = line.menuItemId,
    quantity = line.quantity,
    optionIds = line.options.map { it.optionId },
    modifiers = line.modifiers.map { ModifierPick(it.action, it.modifierId) },
    note = line.note,
    lineId = line.id,
)

/** True when two versions of a line would cook differently. */
fun lineChanged(a: OrderLine, b: OrderLine): Boolean {
    fun key(l: OrderLine) = listOf(l.menuItemId, l.quantity, l.options.map { it.optionId }, l.modifiers.map { it.action to it.modifierId }, l.note)
    return key(a) != key(b)
}
