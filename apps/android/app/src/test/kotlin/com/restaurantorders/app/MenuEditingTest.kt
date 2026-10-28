package com.restaurantorders.app

import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.DishInput
import com.restaurantorders.app.data.ModifierInput
import com.restaurantorders.app.data.OrderInput
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.NewOrderLine
import com.restaurantorders.domain.OrderType
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.Role
import com.restaurantorders.domain.listsForItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MenuEditingTest {
    private lateinit var app: AppContainer

    @Before
    fun setUp() {
        app = testApp()
    }

    @After
    fun tearDown() = app.db.close()

    private suspend fun category(name: String) = app.menu.get().categories.first { it.name == name }

    @Test
    fun `adds a sized dish with the cheapest size as base price`() = runBlocking {
        val loMein = category("Lo Mein")
        val dish = app.menuEditor.saveDish(
            null,
            DishInput(loMein.id, "27A", "Singapore Lo Mein", "星洲撈麵", null, listOf("Pt" to 625, "Qt" to 895), listOf("Chicken", "Shrimp"), spicy = true, modifierListIds = emptyList()),
        )
        assertEquals(625, dish.priceCents)
        assertEquals(listOf(0, 270), dish.optionGroups.first { it.name == MenuOptionGroup.SIZE }.options.map { it.extraCents })
        assertEquals("27A", category("Lo Mein").items.last().code)
    }

    @Test
    fun `editing keeps option ids so open carts still match`() = runBlocking {
        val dish = category("Fried Rice").items.first { it.code == "19" }
        val qtId = dish.optionGroups[0].options.first { it.name == "Qt" }.id
        val saved = app.menuEditor.saveDish(
            dish.id,
            DishInput(dish.categoryId, dish.code, dish.name, dish.altName.orEmpty(), null, listOf("Pt" to 495, "Qt" to 800), listOf("Chicken", "Roast Pork"), false, emptyList()),
        )
        assertEquals(qtId, saved.optionGroups[0].options.first { it.name == "Qt" }.id)
        assertEquals(305, saved.optionGroups[0].options.first { it.name == "Qt" }.extraCents)
        assertEquals(dish.id, category("Fried Rice").items.first { it.code == "19" }.id)
    }

    @Test
    fun `refuses bad prices and a single size`() = runBlocking {
        val c = category("Soup")
        assertFailsWith<ActionException> { app.menuEditor.saveDish(null, DishInput(c.id, "", "X", "", null, emptyList(), emptyList(), false, emptyList())) }
        assertFailsWith<ActionException> { app.menuEditor.saveDish(null, DishInput(c.id, "", "X", "", null, listOf("Pt" to 100), emptyList(), false, emptyList())) }
        Unit
    }

    @Test
    fun `a new modifier list attached to a category shows on its dishes`() = runBlocking {
        val list = app.menuEditor.saveModifierList(null, "Noodle extras", listOf(PreModifier.ADD), listOf(ModifierInput(null, "Egg", "蛋", 100)))
        val loMein = category("Lo Mein")
        app.menuEditor.saveCategory(loMein.id, loMein.name, loMein.note.orEmpty(), loMein.modifierListIds + list.id)
        val menu = app.menu.get()
        val c = menu.categories.first { it.name == "Lo Mein" }
        assertContains(listsForItem(c.items.first(), c, menu.modifierLists).map { it.name }, "Noodle extras")

        app.menuEditor.removeModifierList(list.id)
        assertFalse(app.menu.get().categories.any { list.id in it.modifierListIds })
    }

    @Test
    fun `only empty categories can be removed`() = runBlocking {
        assertFailsWith<ActionException> { app.menuEditor.removeCategory(category("Soup").id) }
        val empty = app.menuEditor.addCategory("Drinks")
        app.menuEditor.removeCategory(empty.id)
        assertFalse(app.menu.get().categories.any { it.name == "Drinks" })
    }

    @Test
    fun `cashiers can't edit the menu or export`() = runBlocking {
        app.staff.add("Tony", Role.CASHIER, "3333")
        app.staff.signIn("3333")
        assertFailsWith<ActionException> { app.menuEditor.addCategory("Drinks") }
        assertFailsWith<ActionException> { app.export() }
        Unit
    }

    @Test
    fun `export holds the menu and orders but never PINs`() = runBlocking {
        val dish = category("Appetizers").items.first()
        app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(NewOrderLine(dish.id, 2))))
        val json = app.export()
        assertContains(json, "\"restaurantName\": \"Chinatown Kitchen\"")
        assertContains(json, "\"createdBy\": \"Alex\"")
        assertTrue(json.contains("Pork Egg Roll"))
        assertFalse(json.contains("pinHash"))
    }
    @Test
    fun `choice groups are made once and attached to categories (C1)`() = runBlocking {
        val group = app.menuEditor.saveChoiceGroup(
            null, "Drink", "飲料", 0, 1,
            listOf(ModifierInput(null, "Coke", "可樂", 150), ModifierInput(null, "Tea", "茶", 100)),
        )
        val lunch = category("Lunch Special")
        app.menuEditor.saveCategory(lunch.id, lunch.name, lunch.note.orEmpty(), lunch.modifierListIds, "午餐", lunch.choiceGroupIds + group.id)
        val updated = category("Lunch Special")
        assertEquals("午餐", updated.altName)
        assertEquals(listOf("Comb & Lunch Choice", "Something Extra", "Drink"), updated.choiceGroupIds.map { id -> app.menu.get().choiceGroups.first { it.id == id }.name })

        // A dish in the category can now be ordered with a drink; the price adds on.
        val dish = updated.items.first()
        val coke = group.choices.first().id
        val (view, _) = app.orders.create(OrderInput(OrderType.WALK_IN, lines = listOf(NewOrderLine(dish.id, 1, dish.optionGroups.map { it.options.first().id }, choiceIds = listOf(coke)))))
        assertEquals("Coke", view.order.lines.single().choices.single().name)

        // Editing keeps choice ids; deleting detaches the group everywhere.
        val edited = app.menuEditor.saveChoiceGroup(group.id, "Drink", "飲料", 0, 2, listOf(ModifierInput(coke, "Coke", "可樂", 175)))
        assertEquals(coke, edited.choices.single().id)
        assertContains(assertFailsWith<ActionException> { app.menuEditor.saveChoiceGroup(null, "drink", "", 0, 1, listOf(ModifierInput(null, "x", "", 0))) }.message!!, "already a group")
        assertContains(assertFailsWith<ActionException> { app.menuEditor.saveChoiceGroup(null, "Sauce cup", "", 2, 1, listOf(ModifierInput(null, "x", "", 0))) }.message!!, "Picks go from")
        app.menuEditor.removeChoiceGroup(group.id)
        assertFalse(category("Lunch Special").choiceGroupIds.contains(group.id))
    }
}
