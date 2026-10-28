// The data model, ported from apps/frontend/src/domain/types.ts. Money is integer cents.
package com.restaurantorders.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

enum class Role { OWNER, MANAGER, CASHIER }

/**
 * How a kitchen instruction changes a dish, printed before the ingredient: NO Onion, MORE Sauce.
 * The words are Front's (the restaurant's old register), so the kitchen reads what it is used
 * to. Only ADD and MORE charge the ingredient's price (backlog C3). The enum names are what is
 * stored, so they stay as they were when the labels changed.
 */
enum class PreModifier(val label: String, val charged: Boolean) {
    NO("NO", false),
    ADD("ADD", true),
    EXTRA("MORE", true),
    LIGHT("LESS", false),
    SIDE("ON SIDE", false),
    SUB("CHANGE", false),
    ONLY("ONLY", false),
    ;

    /** The word on the Special tab's action bar. */
    val button: String get() = if (this == SIDE) "Side" else label.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** The action bar's order, as on Front. */
        val BAR = listOf(ADD, EXTRA, LIGHT, NO, ONLY, SUB, SIDE)
    }
}

@Serializable
data class MenuOption(val id: String, val name: String, val extraCents: Int, val altName: String? = null)

/** A required pick of exactly one option, such as size (Pt/Qt) or protein (Chicken/Roast Pork). */
@Serializable
data class MenuOptionGroup(val name: String, val options: List<MenuOption>) {
    companion object {
        const val SIZE = "Size"
        const val CHOICE = "Choice"
    }
}

@Serializable
data class Choice(val id: String, val name: String, val altName: String? = null, val priceCents: Int)

/**
 * A shared group of priced picks, such as "Comb & Lunch Choice" (w. Pork Lo Mein +$4.00) or
 * "Something Extra", attached to categories or dishes like modifier lists (backlog C1). The
 * cashier picks from `min` to `max` of them; min 0 means the group can be skipped.
 */
@Serializable
data class ChoiceGroup(
    val id: String,
    val name: String,
    val altName: String? = null,
    val min: Int = 0,
    val max: Int = 1,
    val choices: List<Choice>,
)

@Serializable
data class Modifier(val id: String, val name: String, val altName: String?, val priceCents: Int)

/** A shared list of modifiers (e.g. Vegetables) attached to categories or dishes. */
@Serializable
data class ModifierList(
    val id: String,
    val name: String,
    /** Pre-modifiers the cashier may use with this list, in display order. */
    val actions: List<PreModifier>,
    val modifiers: List<Modifier>,
    val altName: String? = null,
)

@Serializable
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
    /** Choice groups for this dish only, on top of its category's groups. */
    val choiceGroupIds: List<String> = emptyList(),
)

@Serializable
data class MenuCategory(
    val id: String,
    val name: String,
    val note: String?,
    /** Modifier lists offered on every dish in the category. */
    val modifierListIds: List<String>,
    val items: List<MenuItem>,
    val altName: String? = null,
    /** Choice groups offered on every dish in the category. */
    val choiceGroupIds: List<String> = emptyList(),
)

@Serializable
data class Menu(
    val categories: List<MenuCategory>,
    val modifierLists: List<ModifierList>,
    val choiceGroups: List<ChoiceGroup> = emptyList(),
)

/**
 * How the customer gets the order. The restaurant names them, turns them on or off and chooses
 * whether they need a name or phone in Settings (backlog B7). PICK_UP and PHONE orders can have
 * a pickup time.
 */
enum class OrderType(val defaultName: String, val takesPickupTime: Boolean) {
    PICK_UP("Pick Up", true),
    WALK_IN("Walk In", false),
    PHONE("Phone", true),
    DINE_IN("Dine In", false),
}

/** Money off an order before tax (backlog B5): a percent or a dollar amount. */
@Serializable
data class Discount(val label: String, val percent: Double? = null, val amountCents: Int? = null)

/** A dish that isn't on the menu, rung up with a typed name and price (backlog B2). */
@Serializable
data class OpenItem(val name: String, val altName: String? = null, val priceCents: Int)

enum class OrderStatus { OPEN, READY, PICKED_UP, CANCELLED }

@Serializable
data class ModifierPick(val action: PreModifier, val modifierId: String)

/** One line as the cashier builds it. `lineId` keeps a line's identity when an order is edited. */
data class NewOrderLine(
    /** Empty for an open item. */
    val menuItemId: String,
    val quantity: Int,
    /** One option id from each of the dish's option groups. */
    val optionIds: List<String> = emptyList(),
    val modifiers: List<ModifierPick> = emptyList(),
    val note: String? = null,
    val lineId: String? = null,
    /** Picks from the dish's choice groups. */
    val choiceIds: List<String> = emptyList(),
    /** A manager's price for one of this dish, replacing the menu's (backlog B4). */
    val unitPriceCents: Int? = null,
    val openItem: OpenItem? = null,
)

@Serializable
data class LineOption(
    val optionId: String,
    val group: String,
    val name: String,
    val extraCents: Int,
    val altName: String? = null,
    /** Already in the dish's name ("Roast Pork Lo Mein"), so it isn't shown again on its own line. */
    val inName: Boolean = false,
)

@Serializable
data class LineChoice(val choiceId: String, val group: String, val name: String, val altName: String?, val priceCents: Int)

@Serializable
data class LineModifier(
    val action: PreModifier,
    val modifierId: String,
    val name: String,
    val altName: String?,
    /** What this modifier adds to the line's unit price (0 for NO, LIGHT, ON SIDE). */
    val priceCents: Int,
)

/** A dish in an order, copied from the menu when the order was taken. */
@Serializable
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
    val choices: List<LineChoice> = emptyList(),
    /** The price of one, when a manager changed it; null means the menu's price. */
    val overrideCents: Int? = null,
    /** An open item: typed in, not from the menu. */
    val open: Boolean = false,
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
    val discount: Discount? = null,
    val discountCents: Int = 0,
    /** Why the order was voided (it is then CANCELLED). */
    val voidReason: String? = null,
)

enum class TicketKind { NEW, CHANGED, CANCELLED, REPRINT, TEST }

enum class TicketLanguage { EN, ZH, BOTH }

enum class LineStyle { NORMAL, TALL, BIG }

@Serializable
data class TicketLine(
    val text: String,
    val style: LineStyle = LineStyle.NORMAL,
    val bold: Boolean = false,
    /** Printed reversed (white on black) to catch the chef's eye. */
    val invert: Boolean = false,
)
