// Which modifications a dish offers and how picks combine, ported from
// apps/frontend/src/domain/modifiers.ts.
package com.restaurantorders.domain

/** How a modifier reads on screen and on the ticket: "NO Onion", "Sauce ON SIDE". */
fun describeModifier(action: PreModifier, name: String): String =
    if (action == PreModifier.SIDE) "$name ON SIDE" else "${action.label} $name"

/** The modifier lists a dish offers: its category's lists, then its own, each once. */
fun listsForItem(item: MenuItem, category: MenuCategory?, lists: List<ModifierList>): List<ModifierList> =
    ((category?.modifierListIds ?: emptyList()) + item.modifierListIds)
        .distinct()
        .mapNotNull { id -> lists.find { it.id == id } }

fun findItem(menu: Menu, itemId: String): Pair<MenuItem, MenuCategory>? {
    for (category in menu.categories) {
        val item = category.items.find { it.id == itemId }
        if (item != null) return item to category
    }
    return null
}

/**
 * Adds a pick, or takes it away when the same one is tapped again. A modifier appears once
 * per dish, so tapping EXTRA Onion after NO Onion replaces it.
 */
fun togglePick(picks: List<ModifierPick>, pick: ModifierPick): List<ModifierPick> {
    val existing = picks.find { it.modifierId == pick.modifierId } ?: return picks + pick
    if (existing.action == pick.action) return picks - existing
    return picks.map { if (it == existing) pick else it }
}

/** Checks every pick against the dish's lists; returns the problem, or null when all is well. */
fun checkPicks(picks: List<ModifierPick>, lists: List<ModifierList>): String? {
    val seen = mutableSetOf<String>()
    for (pick in picks) {
        if (!seen.add(pick.modifierId)) return "Each modification can be picked once per dish"
        val list = lists.find { l -> l.modifiers.any { it.id == pick.modifierId } }
            ?: return "That modification is not offered on this dish"
        if (pick.action !in list.actions) {
            val name = list.modifiers.first { it.id == pick.modifierId }.name
            return "${pick.action.label} is not allowed for $name"
        }
    }
    return null
}
