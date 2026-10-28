// What each role may do, ported from apps/frontend/src/domain/permissions.ts.
package com.restaurantorders.domain

/**
 * VOID (void an order or a dish already sent), PRICE (change a dish's price) and DISCOUNT are
 * the risky actions: a cashier can only do them with a manager's PIN (backlog A4).
 */
enum class Permission { ORDER, ORDERS, KITCHEN, SOLD_OUT, MENU, STAFF, SETTINGS, VOID, PRICE, DISCOUNT }

private val BY_ROLE = mapOf(
    Role.OWNER to Permission.entries.toSet(),
    Role.MANAGER to Permission.entries.toSet() - Permission.SETTINGS,
    Role.CASHIER to setOf(Permission.ORDER, Permission.ORDERS, Permission.KITCHEN, Permission.SOLD_OUT),
)

fun can(role: Role, permission: Permission) = permission in BY_ROLE.getValue(role)

/** Owners manage everyone; managers manage cashiers only. */
fun canManage(actor: Role, target: Role) = actor == Role.OWNER || (actor == Role.MANAGER && target == Role.CASHIER)

/** Roles an actor may give to someone else. */
fun assignableRoles(actor: Role): List<Role> = when (actor) {
    Role.OWNER -> listOf(Role.OWNER, Role.MANAGER, Role.CASHIER)
    Role.MANAGER -> listOf(Role.CASHIER)
    Role.CASHIER -> emptyList()
}

val Role.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
