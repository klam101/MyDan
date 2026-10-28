// The lists in Settings the restaurant edits: order types, discount presets and void reasons.
// Each is stored as JSON in its settings column; an empty column means the defaults below.
package com.restaurantorders.app.data

import com.restaurantorders.domain.Discount
import com.restaurantorders.domain.OrderType
import kotlinx.serialization.Serializable

/**
 * One of the restaurant's order types (backlog B7): its name, colour, whether it is used, and
 * whether Send needs the customer's name or phone. The kitchen doesn't, so that is off unless
 * the owner turns it on.
 */
@Serializable
data class OrderTypeSetting(
    val type: OrderType,
    val name: String,
    val color: Long,
    val enabled: Boolean,
    val requireCustomer: Boolean = false,
)

/** Front's two lanes are on; Phone and Dine In can be turned on in Settings. */
val DEFAULT_ORDER_TYPES = listOf(
    OrderTypeSetting(OrderType.PICK_UP, OrderType.PICK_UP.defaultName, 0xFF16A34A, enabled = true),
    OrderTypeSetting(OrderType.WALK_IN, OrderType.WALK_IN.defaultName, 0xFF2563EB, enabled = true),
    OrderTypeSetting(OrderType.PHONE, OrderType.PHONE.defaultName, 0xFF9333EA, enabled = false),
    OrderTypeSetting(OrderType.DINE_IN, OrderType.DINE_IN.defaultName, 0xFFEA580C, enabled = false),
)

val DEFAULT_DISCOUNTS = listOf(Discount("family", percent = 10.0), Discount("", amountCents = 200))

val DEFAULT_VOID_REASONS = listOf("Customer left", "Wrong order", "Duplicate", "Kitchen mistake")

private inline fun <reified T> parsed(json: String, default: T): T =
    if (json.isBlank()) default else runCatching { AppJson.decodeFromString<T>(json) }.getOrDefault(default)

/** Every order type, in the restaurant's order, with any type the settings don't list yet. */
val SettingsEntity.orderTypes: List<OrderTypeSetting>
    get() {
        val saved = parsed(orderTypesJson, DEFAULT_ORDER_TYPES)
        return saved + DEFAULT_ORDER_TYPES.filter { d -> saved.none { it.type == d.type } }.map { it.copy(enabled = false) }
    }

val SettingsEntity.enabledOrderTypes: List<OrderTypeSetting> get() = orderTypes.filter { it.enabled }

/** The restaurant's name for an order type, as on the order screen and the ticket. */
fun SettingsEntity.typeName(type: OrderType): String = orderTypes.first { it.type == type }.name

fun SettingsEntity.typeColor(type: OrderType): Long = orderTypes.first { it.type == type }.color

fun SettingsEntity.requiresCustomer(type: OrderType): Boolean = orderTypes.first { it.type == type }.requireCustomer

val SettingsEntity.discountPresets: List<Discount> get() = parsed(discountPresetsJson, DEFAULT_DISCOUNTS)

val SettingsEntity.voidReasons: List<String> get() = parsed(voidReasonsJson, DEFAULT_VOID_REASONS)

/** Checks and stores the lists; throws ActionException when one isn't valid. */
fun SettingsEntity.withLists(types: List<OrderTypeSetting>, discounts: List<Discount>, reasons: List<String>): SettingsEntity {
    if (types.none { it.enabled }) throw ActionException("Keep at least one order type on")
    if (types.any { it.name.isBlank() }) throw ActionException("Every order type needs a name")
    val cleanReasons = reasons.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return copy(
        orderTypesJson = AppJson.encodeToString(types.map { it.copy(name = it.name.trim()) }),
        discountPresetsJson = AppJson.encodeToString(discounts),
        voidReasonsJson = AppJson.encodeToString(cleanReasons),
    )
}
