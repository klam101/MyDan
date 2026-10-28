// The menu a new restaurant starts with: the family restaurant's printed menu, its modifier
// lists and choice groups. The JSON comes from scripts/export-starter-menu.mjs.
package com.restaurantorders.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
private class StarterData(val sections: List<Section>, val modifierLists: List<ListSeed>, val choiceGroups: List<ChoiceSeed> = emptyList()) {
    @Serializable
    class Section(val name: String, val altName: String? = null, val note: String? = null, val sizes: List<String>, val rows: List<Row>)

    @Serializable
    class ChoiceSeed(val name: String, val altName: String? = null, val min: Int, val max: Int, val choices: List<Mod>, val categories: List<String>)

    @Serializable
    class Row(
        val code: String,
        val altName: String,
        val name: String,
        /** One price, or one per size (null = not sold in that size). */
        val prices: List<Double?>,
        val sized: Boolean,
        val spicy: Boolean,
        val choices: List<String>,
        /** Sizes for this dish when they differ from its section's. */
        val sizes: List<String>? = null,
    )

    @Serializable
    class ListSeed(val name: String, val altName: String? = null, val actions: List<String>, val modifiers: List<Mod>, val categories: List<String>)

    @Serializable
    class Mod(val name: String, val altName: String, val price: Double)
}

private fun cents(dollars: Double) = (dollars * 100).roundToInt()

/** Builds the starter menu with fresh ids from `newId(prefix)`. */
fun loadStarterMenu(newId: (prefix: String) -> String): Menu {
    val text = StarterData::class.java.getResourceAsStream("/starter-menu.json")!!.bufferedReader().use { it.readText() }
    val data = Json.decodeFromString<StarterData>(text)

    val lists = data.modifierLists.map { seed ->
        ModifierList(
            id = newId("mlist"),
            name = seed.name,
            actions = seed.actions.map { PreModifier.valueOf(it.uppercase()) },
            modifiers = seed.modifiers.map { Modifier(newId("mod"), it.name, it.altName, cents(it.price)) },
            altName = seed.altName,
        )
    }
    val groups = data.choiceGroups.map { seed ->
        ChoiceGroup(
            id = newId("cgroup"),
            name = seed.name,
            altName = seed.altName,
            min = seed.min,
            max = seed.max,
            choices = seed.choices.map { Choice(newId("choice"), it.name, it.altName, cents(it.price)) },
        )
    }

    val categories = data.sections.map { section ->
        val categoryId = newId("cat")
        val items = section.rows.map { row ->
            val labels = row.sizes ?: section.sizes
            val sizes = if (row.sized) {
                row.prices.mapIndexedNotNull { k, price ->
                    price?.let { (labels.getOrNull(k) ?: error("Menu item ${row.code} ${row.name} has a price without a size")) to cents(it) }
                }
            } else {
                emptyList()
            }
            val model = priceModel(if (row.sized) null else cents(row.prices.single()!!), sizes)
            val groups = buildList {
                if (model.sizes.isNotEmpty()) {
                    add(MenuOptionGroup(MenuOptionGroup.SIZE, model.sizes.map { (name, extra) -> MenuOption(newId("opt"), name, extra) }))
                }
                if (row.choices.isNotEmpty()) {
                    add(MenuOptionGroup(MenuOptionGroup.CHOICE, row.choices.map { MenuOption(newId("opt"), it, 0) }))
                }
            }
            MenuItem(
                id = newId("item"),
                categoryId = categoryId,
                code = row.code,
                name = row.name,
                altName = row.altName,
                priceCents = model.baseCents,
                spicy = row.spicy,
                available = true,
                optionGroups = groups,
                modifierListIds = emptyList(),
            )
        }
        val attached = data.modifierLists.zip(lists).filter { (seed, _) -> section.name in seed.categories }.map { it.second.id }
        val choices = data.choiceGroups.zip(groups).filter { (seed, _) -> section.name in seed.categories }.map { it.second.id }
        MenuCategory(categoryId, section.name, section.note, attached, items, altName = section.altName, choiceGroupIds = choices)
    }
    return Menu(categories, lists, groups)
}
