// Turns ticket lines into ESC/POS bytes, the command language almost every receipt printer
// speaks. Ported from renderEscPos in apps/server/src/ticket.ts.
package com.restaurantorders.printing

import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.TicketLine
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EscPos {
    private const val ESC = 0x1b
    private const val GS = 0x1d
    private const val FS = 0x1c
    private const val LF = 0x0a

    private fun size(style: LineStyle) = when (style) {
        LineStyle.NORMAL -> 0x00
        LineStyle.TALL -> 0x01 // double height
        LineStyle.BIG -> 0x11 // double width and height
    }

    /**
     * @param chinese print Chinese characters using the printer's GB18030 mode. Most printers
     * sold to Chinese restaurants have it; leave it off until a test ticket shows they print
     * correctly. When off, anything outside plain ASCII prints as "?".
     */
    fun render(lines: List<TicketLine>, chinese: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun bytes(vararg b: Int) = b.forEach { out.write(it) }

        bytes(ESC, 0x40) // reset the printer
        if (chinese) bytes(FS, 0x26) else bytes(FS, 0x2e) // Chinese character mode on / off
        for (line in lines) {
            bytes(GS, 0x21, size(line.style))
            bytes(ESC, 0x45, if (line.bold) 1 else 0)
            bytes(GS, 0x42, if (line.invert) 1 else 0)
            out.write(encode(line.text, chinese))
            bytes(LF)
        }
        bytes(GS, 0x21, 0, ESC, 0x45, 0, GS, 0x42, 0)
        bytes(GS, 0x56, 0x42, 0x04) // feed 4 lines, then partial cut
        return out.toByteArray()
    }

    private val GB18030 = Charset.forName("GB18030")

    private fun encode(text: String, chinese: Boolean): ByteArray =
        if (chinese) text.toByteArray(GB18030) else text.replace(Regex("[^\\x20-\\x7e]"), "?").toByteArray(Charsets.US_ASCII)

    /** The ticket as plain text, for logs and previews. */
    fun text(lines: List<TicketLine>): String = lines.joinToString("\n") { it.text } + "\n"
}
