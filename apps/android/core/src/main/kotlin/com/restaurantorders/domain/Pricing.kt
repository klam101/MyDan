// Prices and totals, ported from apps/frontend/src/domain/pricing.ts and money.ts.
package com.restaurantorders.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** "$6.35" */
fun money(cents: Int): String =
    (if (cents < 0) "-" else "") + String.format(Locale.ROOT, "$%d.%02d", abs(cents) / 100, abs(cents) % 100)

/** Parses a typed dollar amount ("7.6", "$7.60") into cents; null when it isn't a price. */
fun parseMoney(text: String): Int? {
    val cleaned = text.replace("$", "").trim()
    if (!Regex("""^\d+(\.\d{0,2})?$""").matches(cleaned)) return null
    return (cleaned.toDouble() * 100).roundToInt()
}

fun modifierCharge(action: PreModifier, priceCents: Int) = if (action.charged) priceCents else 0

fun unitPrice(line: OrderLine): Int =
    line.basePriceCents + line.options.sumOf { it.extraCents } + line.modifiers.sumOf { it.priceCents }

fun linePrice(line: OrderLine): Int = unitPrice(line) * line.quantity

data class Totals(val subtotalCents: Int, val taxCents: Int, val totalCents: Int)

/** Tax is rounded to the cent, halves up (the same as the web app). */
fun totals(lines: List<OrderLine>, taxRate: Double): Totals {
    val subtotal = lines.sumOf { linePrice(it) }
    val tax = (subtotal * taxRate).roundToInt()
    return Totals(subtotal, tax, subtotal + tax)
}

/** "$6.35", or the lowest and highest price for dishes whose price depends on size. */
fun priceLabel(item: MenuItem): String {
    val size = item.optionGroups.find { it.name == MenuOptionGroup.SIZE } ?: return money(item.priceCents)
    val extras = size.options.map { it.extraCents }
    return "${money(item.priceCents + extras.min())}–${money(item.priceCents + extras.max())}"
}

/** A dish's base price and size options, as stored: the base is the cheapest size. */
data class PriceModel(val baseCents: Int, val sizes: List<Pair<String, Int>>)

/** Works out the stored prices from what the menu editor gives; throws OrderException when invalid. */
fun priceModel(priceCents: Int?, sizes: List<Pair<String, Int>>): PriceModel {
    if (sizes.size == 1) throw OrderException("A dish with sizes needs at least two of them")
    if (sizes.any { it.first.isBlank() }) throw OrderException("Every size needs a name")
    if (sizes.map { it.first.trim() }.toSet().size != sizes.size) throw OrderException("Size names must be different")
    if (sizes.isEmpty()) {
        if (priceCents == null || priceCents < 0) throw OrderException("A price is required")
        return PriceModel(priceCents, emptyList())
    }
    val base = sizes.minOf { it.second }
    return PriceModel(base, sizes.map { it.first.trim() to it.second - base })
}
