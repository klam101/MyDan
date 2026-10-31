package com.restaurantorders

import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.printing.DOTS_80MM
import com.restaurantorders.printing.Raster
import com.restaurantorders.printing.RasterCommands
import com.restaurantorders.printing.TicketLayout
import com.restaurantorders.tools.AwtRasterizer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tickets printed as pictures: the two printer formats and the computer's drawing. */
class RasterTest {
    private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }

    /** 16 dots wide, 3 rows: a dot top-left, a blank row, a full row. */
    private val small = Raster.of(16, 3) { x, y -> (y == 0 && x == 0) || y == 2 }

    @Test
    fun `packs dots eight to a byte, first dot in the high bit`() {
        assertEquals("80 00 00 00 ff ff", small.bits.hex())
        assertTrue(small.isBlack(0, 0) && !small.isBlack(1, 0) && small.isBlack(15, 2))
    }

    @Test
    fun `ESC-POS pictures use GS v 0 in bands, then feed and cut`() {
        val hex = RasterCommands.escPos(small).hex()
        assertEquals("1b 40 1d 76 30 00 02 00 03 00 80 00 00 00 ff ff 1d 56 42 04", hex)
        // A tall picture goes in bands of 128 rows.
        val tall = RasterCommands.escPos(Raster.of(8, 300) { _, _ -> false }).hex()
        assertEquals(3, Regex("1d 76 30 00").findAll(tall).count())
        assertContains(tall, "1d 76 30 00 01 00 2c 00") // last band: 44 rows
    }

    @Test
    fun `Star pictures send each row, skip blank rows, then cut`() {
        val hex = RasterCommands.star(small).hex()
        assertTrue(hex.startsWith("1b 2a 72 52 1b 2a 72 41"), "initialise and enter raster mode: $hex")
        assertContains(hex, "62 01 00 80") // row 1: only the first byte is sent
        assertContains(hex, "1b 2a 72 59 31 00") // one blank row skipped ("Y1")
        assertContains(hex, "62 02 00 ff ff") // row 3: both bytes
        assertTrue(hex.endsWith("1b 0c 00 1b 2a 72 42"), "print the page and leave raster mode: $hex")
    }

    @Test
    fun `the computer draws a ticket the width of 80 mm paper`() {
        val lines = listOf(
            TicketLine(" VOID ", LineStyle.BIG, bold = true, invert = true),
            TicketLine("1 x L2 Roast Pork Lo Mein 叉撈麵", LineStyle.TALL, bold = true),
            TicketLine(""),
        )
        val raster = AwtRasterizer.render(lines, DOTS_80MM)
        val layout = TicketLayout(DOTS_80MM)
        assertEquals(576, raster.width)
        assertEquals(layout.height(lines), raster.height)
        // The VOID line is white on black: its left edge is solid.
        val voidMiddle = TicketLayout.TOP_MARGIN + layout.height(lines[0]) / 2
        assertTrue(raster.isBlack(1, voidMiddle))
        // The dish line has ink; the empty line has none.
        val dishTop = TicketLayout.TOP_MARGIN + layout.height(lines[0])
        assertTrue((dishTop until dishTop + layout.height(lines[1])).any { y -> (0 until 576).any { raster.isBlack(it, y) } })
        val emptyTop = dishTop + layout.height(lines[1])
        assertTrue((emptyTop until raster.height).none { y -> (0 until 576).any { raster.isBlack(it, y) } })
    }
}
