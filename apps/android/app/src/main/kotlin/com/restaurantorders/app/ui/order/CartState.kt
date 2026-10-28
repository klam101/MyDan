package com.restaurantorders.app.ui.order

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.restaurantorders.app.data.OrderInput
import com.restaurantorders.app.data.OrderView
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.lineInput
import java.time.Instant

class CartLine(val key: Int, val input: NewOrderLine)

/**
 * The order being built. It lives above the tabs, so switching to Orders and back keeps it.
 * When changing a sent order, `editing` holds that order.
 */
class CartState {
    var lines by mutableStateOf(listOf<CartLine>())
        private set
    var type by mutableStateOf(OrderType.WALK_IN)
    var customerName by mutableStateOf("")
    var customerPhone by mutableStateOf("")
    var pickupAt by mutableStateOf<Instant?>(null)
    var note by mutableStateOf("")
    var editing by mutableStateOf<OrderView?>(null)
        private set
    /** The line just added, so it can be highlighted. */
    var lastAdded by mutableStateOf<Int?>(null)
        private set

    private var nextKey = 1

    /** Adds a dish; the same dish with the same picks and no note just adds to the quantity. */
    fun add(input: NewOrderLine) {
        val same = lines.find { sameDish(it.input, input) }
        if (same != null) {
            replace(same.key, same.input.copy(quantity = (same.input.quantity + input.quantity).coerceAtMost(99)))
            lastAdded = same.key
        } else {
            val line = CartLine(nextKey++, input)
            lines = lines + line
            lastAdded = line.key
        }
    }

    fun replace(key: Int, input: NewOrderLine) {
        lines = lines.map { if (it.key == key) CartLine(key, input) else it }
    }

    fun remove(key: Int) {
        lines = lines.filter { it.key != key }
    }

    fun changeQuantity(key: Int, delta: Int) {
        val line = lines.find { it.key == key } ?: return
        val quantity = line.input.quantity + delta
        if (quantity <= 0) remove(key) else replace(key, line.input.copy(quantity = quantity.coerceAtMost(99)))
    }

    fun clear() {
        lines = emptyList()
        type = OrderType.WALK_IN
        customerName = ""
        customerPhone = ""
        pickupAt = null
        note = ""
        editing = null
        lastAdded = null
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
        editing = view
    }

    fun toInput() = OrderInput(
        type = type,
        customerName = customerName,
        customerPhone = customerPhone,
        pickupAt = if (type == OrderType.PHONE) pickupAt else null,
        note = note,
        lines = lines.map { it.input },
    )

    private fun sameDish(a: NewOrderLine, b: NewOrderLine) =
        a.menuItemId == b.menuItemId && a.note.isNullOrEmpty() && b.note.isNullOrEmpty() &&
            a.optionIds.sorted() == b.optionIds.sorted() && a.modifiers == b.modifiers
}
