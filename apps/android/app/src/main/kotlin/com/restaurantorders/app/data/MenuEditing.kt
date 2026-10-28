// Changing the menu: categories, dishes and shared modification lists. Managers and owners
// only. The rules match addItem / saveItem / saveCategory / *ModifierList in
// apps/frontend/src/data/mockApi.ts. Option ids are kept when names stay the same, so open
// carts and past orders stay linked.
package com.restaurantorders.app.data

import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.MenuCategory
import com.restaurantorders.domain.MenuItem
import com.restaurantorders.domain.MenuOption
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.Modifier
import com.restaurantorders.domain.ModifierList
import com.restaurantorders.domain.OrderException
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.priceModel

/** What the dish editor sends. Sizes are full prices, e.g. Pt $4.95, Qt $7.60. */
data class DishInput(
    val categoryId: String,
    val code: String,
    val name: String,
    val altName: String,
    /** The price for a dish without sizes. */
    val priceCents: Int?,
    val sizes: List<Pair<String, Int>>,
    val choices: List<String>,
    val spicy: Boolean,
    val modifierListIds: List<String>,
)

data class ModifierInput(val id: String?, val name: String, val altName: String, val priceCents: Int)

/** Runs a menu change for managers and owners, saving the result. */
private suspend fun MenuRepository.edit(session: Session, change: (Menu) -> Menu): Menu {
    session.require(Permission.MENU)
    val next = try {
        change(get())
    } catch (e: OrderException) {
        throw ActionException(e.message ?: "That change isn't possible")
    }
    write(next)
    return next
}

private fun Menu.mapCategories(change: (MenuCategory) -> MenuCategory) = copy(categories = categories.map(change))

class MenuEditor(private val menu: MenuRepository, private val session: Session) {
    suspend fun addCategory(name: String): MenuCategory {
        if (name.isBlank()) throw ActionException("The category needs a name")
        val category = MenuCategory(newId(), name.trim(), null, emptyList(), emptyList())
        menu.edit(session) { it.copy(categories = it.categories + category) }
        return category
    }

    suspend fun saveCategory(id: String, name: String, note: String, modifierListIds: List<String>) {
        if (name.isBlank()) throw ActionException("The category needs a name")
        menu.edit(session) { m ->
            m.mapCategories { c -> if (c.id == id) c.copy(name = name.trim(), note = note.trim().ifEmpty { null }, modifierListIds = m.knownLists(modifierListIds)) else c }
        }
    }

    /** Only an empty category can be removed. */
    suspend fun removeCategory(id: String) = menu.edit(session) { m ->
        val category = m.categories.find { it.id == id } ?: throw ActionException("Category not found")
        if (category.items.isNotEmpty()) throw ActionException("Move or remove its dishes first")
        m.copy(categories = m.categories - category)
    }

    /** Adds a dish (`id` null) or changes one, moving it if its category changed. */
    suspend fun saveDish(id: String?, input: DishInput): MenuItem {
        if (input.name.isBlank()) throw ActionException("The dish needs a name")
        var saved: MenuItem? = null
        menu.edit(session) { m ->
            if (m.categories.none { it.id == input.categoryId }) throw ActionException("Category not found")
            val current = id?.let { itemId -> m.categories.flatMap { it.items }.find { it.id == itemId } ?: throw ActionException("Dish not found") }
            val model = priceModel(input.priceCents, input.sizes)
            val keep = { group: String, name: String -> current?.optionGroups?.find { it.name == group }?.options?.find { it.name == name }?.id ?: newId() }
            val choices = input.choices.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (choices.size == 1) throw ActionException("A dish with choices needs at least two of them")
            val groups = buildList {
                if (model.sizes.isNotEmpty()) add(MenuOptionGroup(MenuOptionGroup.SIZE, model.sizes.map { (n, extra) -> MenuOption(keep(MenuOptionGroup.SIZE, n), n, extra) }))
                if (choices.isNotEmpty()) add(MenuOptionGroup(MenuOptionGroup.CHOICE, choices.map { MenuOption(keep(MenuOptionGroup.CHOICE, it), it, 0) }))
            }
            val dish = MenuItem(
                id = current?.id ?: newId(),
                categoryId = input.categoryId,
                code = input.code.trim(),
                name = input.name.trim(),
                altName = input.altName.trim().ifEmpty { null },
                priceCents = model.baseCents,
                spicy = input.spicy,
                available = current?.available ?: true,
                optionGroups = groups,
                modifierListIds = m.knownLists(input.modifierListIds),
            )
            saved = dish
            m.mapCategories { c ->
                val without = c.items.filter { it.id != dish.id }
                when {
                    c.id != dish.categoryId -> c.copy(items = without)
                    // Stay in place when only edited; go to the end when added or moved here.
                    c.items.any { it.id == dish.id } -> c.copy(items = c.items.map { if (it.id == dish.id) dish else it })
                    else -> c.copy(items = without + dish)
                }
            }
        }
        return saved!!
    }

    /** Past orders keep the dish; it just leaves the menu. */
    suspend fun removeDish(id: String) = menu.edit(session) { m -> m.mapCategories { c -> c.copy(items = c.items.filter { it.id != id }) } }

    suspend fun saveModifierList(id: String?, name: String, actions: List<PreModifier>, modifiers: List<ModifierInput>): ModifierList {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ActionException("The list needs a name")
        if (actions.isEmpty()) throw ActionException("Pick at least one of NO, ADD, EXTRA, LIGHT, ON SIDE, SUB")
        val rows = modifiers.filter { it.name.isNotBlank() }
        if (rows.isEmpty()) throw ActionException("Add at least one ingredient")
        if (rows.any { it.priceCents < 0 }) throw ActionException("Prices must be zero or more")
        var saved: ModifierList? = null
        menu.edit(session) { m ->
            if (m.modifierLists.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) throw ActionException("There is already a list called $trimmed")
            val current = id?.let { listId -> m.modifierLists.find { it.id == listId } ?: throw ActionException("List not found") }
            val list = ModifierList(
                id = current?.id ?: newId(),
                name = trimmed,
                actions = PreModifier.entries.filter { it in actions },
                modifiers = rows.map { r ->
                    val keepId = r.id?.takeIf { rid -> current?.modifiers?.any { it.id == rid } == true }
                    Modifier(keepId ?: newId(), r.name.trim(), r.altName.trim().ifEmpty { null }, r.priceCents)
                },
            )
            saved = list
            m.copy(modifierLists = if (current == null) m.modifierLists + list else m.modifierLists.map { if (it.id == list.id) list else it })
        }
        return saved!!
    }

    /** Also detaches the list from every category and dish. */
    suspend fun removeModifierList(id: String) = menu.edit(session) { m ->
        m.copy(
            modifierLists = m.modifierLists.filter { it.id != id },
            categories = m.categories.map { c ->
                c.copy(modifierListIds = c.modifierListIds - id, items = c.items.map { it.copy(modifierListIds = it.modifierListIds - id) })
            },
        )
    }

    private fun Menu.knownLists(ids: List<String>) = ids.distinct().filter { id -> modifierLists.any { it.id == id } }
}
