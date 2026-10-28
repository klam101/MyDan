package com.restaurantorders

import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.MenuOptionGroup
import com.restaurantorders.domain.PreModifier
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.domain.loadStarterMenu
import com.restaurantorders.printing.EscPos
import com.restaurantorders.printing.NetworkPrinter
import com.restaurantorders.printing.PrinterException
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrintingTest {
    private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }

    @Test
    fun `renders sizes, bold, reverse and the cut`() {
        val bytes = EscPos.render(listOf(TicketLine(" CHANGED ", LineStyle.BIG, bold = true, invert = true), TicketLine("1 x L6")))
        val hex = bytes.hex()
        assertTrue(hex.startsWith("1b 40 1c 2e"), "resets the printer and turns Chinese mode off: $hex")
        assertContains(hex, "1d 21 11 1b 45 01 1d 42 01") // big, bold, reversed
        assertContains(hex, "1d 21 00 1b 45 00 1d 42 00 31 20 78 20 4c 36 0a") // "1 x L6", plain
        assertTrue(hex.endsWith("1d 56 42 04"), "ends with feed and cut: $hex")
    }

    @Test
    fun `prints Chinese as question marks unless Chinese mode is on`() {
        val plain = EscPos.render(listOf(TicketLine("NO ONION 洋蔥")))
        assertContains(String(plain, Charsets.US_ASCII), "NO ONION ??\n")
        val chinese = EscPos.render(listOf(TicketLine("洋")), chinese = true)
        assertContains(chinese.hex(), "1c 26") // Chinese mode on
        assertContains(chinese.hex(), "d1 f3") // 洋 in GB18030
    }

    @Test
    fun `sends the bytes to the printer's port`() {
        ServerSocket(0).use { server ->
            var received = ByteArray(0)
            val reader = thread { server.accept().use { received = it.getInputStream().readBytes() } }
            val bytes = EscPos.render(listOf(TicketLine("hello")))
            NetworkPrinter("127.0.0.1", server.localPort).send(bytes)
            reader.join(5000)
            assertContentEquals(bytes, received)
        }
    }

    @Test
    fun `explains a printer that isn't there`() {
        val port = ServerSocket(0).use { it.localPort } // free port, nothing listening now
        val e = assertFailsWith<PrinterException> { NetworkPrinter("127.0.0.1", port, timeoutMs = 1000).send(byteArrayOf(1)) }
        assertContains(e.message!!, "refused")
    }

    @Test
    fun `loads the starter menu with sizes, choices and modifier lists`() {
        var n = 0
        val menu = loadStarterMenu { "$it${++n}" }
        val items = menu.categories.flatMap { it.items }
        assertEquals(182, items.size)
        val friedRice = items.first { it.code == "19" }
        assertEquals(listOf(MenuOptionGroup.SIZE, MenuOptionGroup.CHOICE), friedRice.optionGroups.map { it.name })
        assertEquals(495, friedRice.priceCents)
        assertEquals(listOf(0, 265), friedRice.optionGroups[0].options.map { it.extraCents })
        val loMein = menu.categories.first { it.name == "Lo Mein" }
        assertEquals(setOf("Meat", "Vegetables", "Sauce & spice"), loMein.modifierListIds.map { id -> menu.modifierLists.first { it.id == id }.name }.toSet())
        assertEquals(
            listOf(PreModifier.ADD, PreModifier.EXTRA, PreModifier.NO, PreModifier.ONLY, PreModifier.SUB),
            menu.modifierLists.first { it.name == "Meat" }.actions,
        )
        assertEquals("肉類", menu.modifierLists.first { it.name == "Meat" }.altName)
    }
}
