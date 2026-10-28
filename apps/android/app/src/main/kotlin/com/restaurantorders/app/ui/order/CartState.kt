package com.restaurantorders.app.ui.order

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.restaurantorders.app.data.OrderInput
import com.restaurantorders.app.data.OrderView
import com.restaurantorders.domain.Discount
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.lineInput
import java.time.Instant

class CartLine(val key: Int, val input: NewOrderLine)

/**
 * The order being built. It lives above the tabs, so switching to Orders and back keeps it.
 * When changing a sent order, `editing` holds that order. Lines print in the cart's order.
 */
class CartState(initialType: OrderType = OrderType.WALK_IN) {
    private val defaultType = initialType

    var lines by mutableStateOf(listOf<CartLine>())
        private set
    var type by mutableStateOf(initialType)
    var customerName by mutableStateOf("")
    var customerPhone by mutableStateOf("")
    var pickupAt by mutableStateOf<Instant?>(null)
    var note by mutableStateOf("")
    var discount by mutableStateOf<Discount?>(null)
    var editing by mutableStateOf<OrderView?>(null)
        private set
    /** The line the tool buttons act on: Front's yellow line. */
    var selected by mutableStateOf<Int?>(null)

    /** Managers who approved risky parts of this order for a cashier (A4), by permission. */
    var approvedBy by mutableStateOf(mapOf<Permission, String>())
        private set

    private var nextKey = 1

    val selectedLine: CartLine? get() = lines.find { it.key == selected }

    /** Adds a dish and selects it; the same dish with the same picks just adds to the quantity. */
    fun add(input: NewOrderLine) {
        val same = lines.find { sameDish(it.input, input) }
        if (same != null) {
            replace(same.key, same.input.copy(quantity = (same.input.quantity + input.quantity).coerceAtMost(99)))
            selected = same.key
        } else {
            val line = CartLine(nextKey++, input)
            lines = lines + line
            selected = line.key
        }
    }

    fun replace(key: Int, input: NewOrderLine) {
        lines = lines.map { if (it.key == key) CartLine(key, input) else it }
    }

    /** Void before Send: the line just goes; the next one up is selected. */
    fun remove(key: Int) {
        val index = lines.indexOfFirst { it.key == key }
        lines = lines.filter { it.key != key }
        if (selected == key) selected = lines.getOrNull((index - 1).coerceAtLeast(0))?.key
    }

    fun setQuantity(key: Int, quantity: Int) {
        val line = lines.find { it.key == key } ?: return
        if (quantity <= 0) remove(key) else replace(key, line.input.copy(quantity = quantity.coerceAtMost(99)))
    }

    fun changeQuantity(key: Int, delta: Int) {
        val line = lines.find { it.key == key } ?: return
        setQuantity(key, line.input.quantity + delta)
    }

    /** Up (-1) or Down (+1), so the ticket reads in the order the kitchen cooks (B3). */
    fun move(key: Int, by: Int) {
        val from = lines.indexOfFirst { it.key == key }
        val to = from + by
        if (from < 0 || to !in lines.indices) return
        lines = lines.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun approve(permission: Permission, managerId: String) {
        approvedBy = approvedBy + (permission to managerId)
    }

    fun clear() {
        lines = emptyList()
        type = defaultType
        customerName = ""
        customerPhone = ""
        pickupAt = null
        note = ""
        discount = null
        editing = null
        selected = null
        approvedBy = emptyMap()
    }

    /** Loads a sent order to change it. Lines keep their ids so the CHANGED ticket can mark them. */
    fun load(view: OrderView) {
        clear()
        val order = view.order
        lines = order.lines.map { CartLine(nextKey++, lineInput(it)) }
        type = order.type
        customerName = order.customerName.orEmpty()
        customerPhone = order.customerPhone.orEmpty()
        pickupAt = order.pickupAt
        note = order.note.orEmpty()
        discount = order.discount
        editing = view
        selected = lines.lastOrNull()?.key
    }

    fun toInput() = OrderInput(
        type = type,
        customerName = customerName,
        customerPhone = customerPhone,
        pickupAt = if (type.takesPickupTime) pickupAt else null,
        note = note,
        lines = lines.map { it.input },
        discount = discount,
        approvedBy = approvedBy,
    )

    private fun sameDish(a: NewOrderLine, b: NewOrderLine) =
        a.openItem == null && b.openItem == null && a.unitPriceCents == null && b.unitPriceCents == null &&
            a.menuItemId == b.menuItemId && a.note.isNullOrEmpty() && b.note.isNullOrEmpty() &&
            a.optionIds.sorted() == b.optionIds.sorted() && a.choiceIds.sorted() == b.choiceIds.sorted() && a.modifiers == b.modifiers
}
