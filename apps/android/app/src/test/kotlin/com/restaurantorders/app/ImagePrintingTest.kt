package com.restaurantorders.app

import com.restaurantorders.app.printing.AndroidRasterizer
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.printing.DOTS_80MM
import com.restaurantorders.printing.PrintMode
import com.restaurantorders.printing.TicketLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Printers set to print pictures get the ticket drawn by the tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePrintingTest {
    private val printer = FakePrinter()
    private lateinit var app: AppContainer

    @Before
    fun setUp() {
        app = testApp(printer)
    }

    @After
    fun tearDown() = app.db.close()

    private fun ByteArray.hex(count: Int) = take(count).joinToString(" ") { "%02x".format(it) }

    @Test
    fun `the tablet draws tickets with ink where the text is`() {
        val lines = listOf(TicketLine(" VOID ", LineStyle.BIG, bold = true, invert = true), TicketLine("1 x L2 Roast Pork Lo Mein 叉撈麵", bold = true), TicketLine(""))
        val raster = AndroidRasterizer.render(lines, DOTS_80MM)
        val layout = TicketLayout(DOTS_80MM)
        assertEquals(576, raster.width)
        assertEquals(layout.height(lines), raster.height)
        assertTrue(raster.isBlack(1, TicketLayout.TOP_MARGIN + layout.height(lines[0]) / 2), "the VOID line is white on black")
        val dishTop = TicketLayout.TOP_MARGIN + layout.height(lines[0])
        assertTrue((dishTop until dishTop + layout.height(lines[1])).any { y -> (0 until 576).any { raster.isBlack(it, y) } }, "the dish line has ink")
    }

    @Test
    fun `each printer type gets its own format`() = runBlocking {
        val ticket = listOf(TicketLine("TEST", LineStyle.BIG))
        for ((mode, start) in listOf(
            PrintMode.TEXT to "1b 40 1c 2e", // ESC/POS text
            PrintMode.IMAGE to "1b 40 1d 76 30 00 48 00", // ESC/POS picture, 72 bytes (576 dots) across
            PrintMode.STAR_IMAGE to "1b 2a 72 52 1b 2a 72 41", // Star raster mode
        )) {
            app.settings.save(app.settings.get().copy(printerMode = mode.name))
            assertEquals(PrintStatus.PRINTED.name, app.printing.print(null, TicketKind.TEST, ticket).status)
            assertEquals(start, printer.sent.last().hex(start.split(" ").size), mode.name)
        }
    }
}
