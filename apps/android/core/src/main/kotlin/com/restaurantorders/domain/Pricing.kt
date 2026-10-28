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

/** One of this dish at the menu's price, with its size, choices and paid modifications. */
fun menuUnitPrice(line: OrderLine): Int =
    line.basePriceCents + line.options.sumOf { it.extraCents } + line.choices.sumOf { it.priceCents } + line.modifiers.sumOf { it.priceCents }

/** One of this dish as charged: a manager's price when there is one, else the menu's. */
fun unitPrice(line: OrderLine): Int = line.overrideCents ?: menuUnitPrice(line)

fun linePrice(line: OrderLine): Int = unitPrice(line) * line.quantity

/** How many dishes, counting quantities: "3 items". */
fun itemCount(lines: List<OrderLine>): Int = lines.sumOf { it.quantity }

data class Totals(val subtotalCents: Int, val taxCents: Int, val totalCents: Int, val discountCents: Int = 0)

/** What a discount takes off this subtotal. Throws OrderException when it isn't allowed. */
fun discountCents(subtotalCents: Int, discount: Discount?): Int {
    if (discount == null) return 0
    val off = when {
        discount.percent != null -> {
            if (discount.percent <= 0 || discount.percent > 100) throw OrderException("A discount is between 0 and 100 %")
            (subtotalCents * discount.percent / 100).roundToInt()
        }
        discount.amountCents != null -> {
            if (discount.amountCents <= 0) throw OrderException("A discount must be more than $0.00")
            discount.amountCents
        }
        else -> throw OrderException("Choose a percent or an amount for the discount")
    }
    if (off > subtotalCents) throw OrderException("The discount (${money(off)}) is more than the order (${money(subtotalCents)})")
    return off
}

/** The discount comes off before tax; tax is rounded to the cent, halves up (as in the web app). */
fun totals(lines: List<OrderLine>, taxRate: Double, discount: Discount? = null): Totals {
    val subtotal = lines.sumOf { linePrice(it) }
    val off = discountCents(subtotal, discount)
    val tax = ((subtotal - off) * taxRate).roundToInt()
    return Totals(subtotal, tax, subtotal - off + tax, off)
}

/** "10 % family" or "$2.00 off", as on the cart and ticket. */
fun describeDiscount(discount: Discount): String {
    val amount = discount.percent?.let { p -> "${if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()} %" } ?: money(discount.amountCents ?: 0)
    return if (discount.label.isBlank()) "$amount off" else "$amount ${discount.label}"
}

/** "$6.35", or the lowest and highest price for dishes whose price depends on size. */
fun priceLabel(item: MenuItem): String {
    val size = item.optionGroups.find { it.name == MenuOptionGroup.SIZE } ?: return money(item.priceCents)
    val extras = size.options.map { it.extraCents }
    return "${money(item.priceCents + extras.min())}–${money(item.priceCents + extras.max())}"
}

/** Front's tile price: the cheapest way to order the dish, with "+" when it can cost more. */
fun fromPriceLabel(item: MenuItem): String {
    val extras = item.optionGroups.map { g -> g.options.minOf { it.extraCents } to g.options.maxOf { it.extraCents } }
    val low = item.priceCents + extras.sumOf { it.first }
    return money(low) + if (extras.any { it.second > it.first }) "+" else ""
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
