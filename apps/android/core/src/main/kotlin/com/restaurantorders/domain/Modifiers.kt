// Which modifications a dish offers and how picks combine, ported from
// apps/frontend/src/domain/modifiers.ts.
package com.restaurantorders.domain

/** How a modifier reads on screen and on the ticket: "NO Onion", "MORE Sauce", "Sauce ON SIDE". */
fun describeModifier(action: PreModifier, name: String): String =
    if (action == PreModifier.SIDE) "$name ON SIDE" else "${action.label} $name"

/** The modifier lists a dish offers: its category's lists, then its own, each once. */
fun listsForItem(item: MenuItem, category: MenuCategory?, lists: List<ModifierList>): List<ModifierList> =
    ((category?.modifierListIds ?: emptyList()) + item.modifierListIds)
        .distinct()
        .mapNotNull { id -> lists.find { it.id == id } }

/** The choice groups a dish offers: its category's groups, then its own, each once. */
fun choiceGroupsForItem(item: MenuItem, category: MenuCategory?, groups: List<ChoiceGroup>): List<ChoiceGroup> =
    ((category?.choiceGroupIds ?: emptyList()) + item.choiceGroupIds)
        .distinct()
        .mapNotNull { id -> groups.find { it.id == id } }

/**
 * Picks a choice, or takes it away when tapped again. In a group of at most one, a new pick
 * replaces the old; a full group ignores more picks until one is taken away.
 */
fun toggleChoice(picked: List<String>, group: ChoiceGroup, choiceId: String): List<String> {
    if (choiceId in picked) return picked - choiceId
    val inGroup = picked.filter { id -> group.choices.any { it.id == id } }
    return when {
        group.max == 1 -> picked - inGroup.toSet() + choiceId
        inGroup.size >= group.max -> picked
        else -> picked + choiceId
    }
}

/** "(Optional 1)", "(Pick 1)", "(Up to 3)": how many a group takes, as Front labels it. */
fun pickRule(group: ChoiceGroup): String = when {
    group.min == 0 && group.max == 1 -> "Optional 1"
    group.min == 0 -> "Optional, up to ${group.max}"
    group.min == group.max -> "Pick ${group.min}"
    else -> "Pick ${group.min} to ${group.max}"
}

/** Checks the picked choices against the dish's groups; returns the problem, or null. */
fun checkChoices(choiceIds: List<String>, groups: List<ChoiceGroup>): String? {
    for (id in choiceIds) {
        if (groups.none { g -> g.choices.any { it.id == id } }) return "That choice is not offered on this dish"
    }
    if (choiceIds.toSet().size != choiceIds.size) return "Each choice can be picked once"
    for (group in groups) {
        val count = choiceIds.count { id -> group.choices.any { it.id == id } }
        if (count < group.min) return "Pick ${group.min} from ${group.name}"
        if (count > group.max) return "Pick up to ${group.max} from ${group.name}"
    }
    return null
}

/** Name first in the language the person reads, then the other when there is one. */
fun displayNames(name: String, altName: String?, language: TicketLanguage): Pair<String, String?> =
    if (language == TicketLanguage.ZH && !altName.isNullOrEmpty()) altName to name else name to altName?.ifEmpty { null }

/** Dishes matching a menu number exactly, or part of the English or Chinese name (backlog B6). */
fun searchMenu(menu: Menu, query: String): List<MenuItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val all = menu.categories.flatMap { it.items }
    val byCode = all.filter { it.code.lowercase() == q }
    // A single letter or digit only matches menu numbers; Chinese names match from one character.
    val nameMatch = all.filter { item ->
        item !in byCode && ((q.length > 1 && item.name.lowercase().contains(q)) || (q.any { it.code > 0x2E80 } && item.altName?.contains(q) == true))
    }
    return byCode + nameMatch
}

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
