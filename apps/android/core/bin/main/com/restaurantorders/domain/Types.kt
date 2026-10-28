// The data model, ported from apps/frontend/src/domain/types.ts. Money is integer cents.
package com.restaurantorders.domain

import java.time.Instant
import java.time.LocalDate

enum class Role { OWNER, MANAGER, CASHIER }

/**
 * How a modification changes a dish, as printed before the ingredient: NO Onion, EXTRA Sauce.
 * ADD, EXTRA and SUB charge the modifier's price; NO, LIGHT and ON SIDE are free.
 */
enum class PreModifier(val label: String, val charged: Boolean) {
    NO("NO", false),
    ADD("ADD", true),
    EXTRA("EXTRA", true),
    LIGHT("LIGHT", false),
    SIDE("ON SIDE", false),
    SUB("SUB", true),
}

data class MenuOption(val id: String, val name: String, val extraCents: Int)

/** A required pick of exactly one option, such as size (Pt/Qt) or protein (Chicken/Roast Pork). */
data class MenuOptionGroup(val name: String, val options: List<MenuOption>) {
    companion object {
        const val SIZE = "Size"
        const val CHOICE = "Choice"
    }
}

data class Modifier(val id: String, val name: String, val altName: String?, val priceCents: Int)

/** A shared list of modifiers (e.g. Vegetables) attached to categories or dishes. */
data class ModifierList(
    val id: String,
    val name: String,
    /** Pre-modifiers the cashier may use with this list, in display order. */
    val actions: List<PreModifier>,
    val modifiers: List<Modifier>,
)

data class MenuItem(
    val id: String,
    val categoryId: String,
    /** The number printed on the paper menu, e.g. "L6" or "19". Empty when there is none. */
    val code: String,
    val name: String,
    val altName: String?,
    /** Price before options; the cheapest size when the dish has sizes. */
    val priceCents: Int,
    val spicy: Boolean,
    val available: Boolean,
    val optionGroups: List<MenuOptionGroup>,
    /** Modifier lists for this dish only, on top of its category's lists. */
    val modifierListIds: List<String>,
)

data class MenuCategory(
    val id: String,
    val name: String,
    val note: String?,
    /** Modifier lists offered on every dish in the category. */
    val modifierListIds: List<String>,
    val items: List<MenuItem>,
)

data class Menu(val categories: List<MenuCategory>, val modifierLists: List<ModifierList>)

enum class OrderType { WALK_IN, PHONE }

enum class OrderStatus { OPEN, READY, PICKED_UP, CANCELLED }

data class ModifierPick(val action: PreModifier, val modifierId: String)

/** One line as the cashier builds it. `lineId` keeps a line's identity when an order is edited. */
data class NewOrderLine(
    val menuItemId: String,
    val quantity: Int,
    /** One option id from each of the dish's option groups. */
    val optionIds: List<String> = emptyList(),
    val modifiers: List<ModifierPick> = emptyList(),
    val note: String? = null,
    val lineId: String? = null,
)

data class LineOption(val optionId: String, val group: String, val name: String, val extraCents: Int)

data class LineModifier(
    val action: PreModifier,
    val modifierId: String,
    val name: String,
    val altName: String?,
    /** What this modifier adds to the line's unit price (0 for NO, LIGHT, ON SIDE). */
    val priceCents: Int,
)

/** A dish in an order, copied from the menu when the order was taken. */
data class OrderLine(
    val id: String,
    val menuItemId: String,
    val code: String,
    val name: String,
    val altName: String?,
    val quantity: Int,
    val basePriceCents: Int,
    val options: List<LineOption>,
    val modifiers: List<LineModifier>,
    val note: String?,
)

data class Order(
    val id: String,
    /** Short number shown on the ticket; starts again at 1 each business day. */
    val number: Int,
    val businessDate: LocalDate,
    val type: OrderType,
    val status: OrderStatus,
    val customerName: String?,
    val customerPhone: String?,
    val pickupAt: Instant?,
    val note: String?,
    val lines: List<OrderLine>,
    val subtotalCents: Int,
    val taxCents: Int,
    val totalCents: Int,
    val createdAt: Instant,
    /** Goes up by one every time the order is edited. */
    val version: Int = 1,
)

enum class TicketKind { NEW, CHANGED, CANCELLED, REPRINT, TEST }

enum class TicketLanguage { EN, ZH, BOTH }

enum class LineStyle { NORMAL, TALL, BIG }

data class TicketLine(
    val text: String,
    val style: LineStyle = LineStyle.NORMAL,
    val bold: Boolean = false,
    /** Printed reversed (white on black) to catch the chef's eye. */
    val invert: Boolean = false,
)
