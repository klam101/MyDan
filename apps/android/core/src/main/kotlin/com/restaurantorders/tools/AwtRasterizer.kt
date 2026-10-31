// Draws tickets as pictures on a computer (Java's own graphics), for printTest. The tablet
// draws them with Android's graphics instead (app/printing/AndroidRasterizer.kt); both follow
// TicketLayout so tickets look the same.
package com.restaurantorders.tools

import com.restaurantorders.domain.TicketLine
import com.restaurantorders.printing.Raster
import com.restaurantorders.printing.TicketLayout
import com.restaurantorders.printing.TicketRasterizer
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object AwtRasterizer : TicketRasterizer {
    override fun render(lines: List<TicketLine>, widthDots: Int): Raster {
        val layout = TicketLayout(widthDots)
        val height = layout.height(lines)
        val image = BufferedImage(widthDots, height, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, widthDots, height)

        // Size the font so one character of normal text is one cell wide.
        val probe = Font(Font.MONOSPACED, Font.PLAIN, 100)
        val size = 100f * layout.cellWidth / g.getFontMetrics(probe).charWidth('0')

        var top = TicketLayout.TOP_MARGIN
        for (line in lines) {
            val h = layout.height(line)
            val font = Font(Font.MONOSPACED, if (line.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)
            val metrics = g.getFontMetrics(font)
            val textWidth = metrics.stringWidth(line.text).toFloat()
            // Shrink a line that is too wide (e.g. Chinese characters) rather than cut it off.
            val sx = minOf(layout.scaleX(line.style), widthDots / textWidth.coerceAtLeast(1f))
            val sy = layout.scaleY(line.style)
            if (line.invert) {
                g.color = Color.BLACK
                g.fillRect(0, top, (textWidth * sx).toInt() + 4, h)
            }
            g.color = if (line.invert) Color.WHITE else Color.BLACK
            val saved = g.transform
            g.translate(2.0, top + (h - metrics.height * sy) / 2.0 + metrics.ascent * sy)
            g.scale(sx.toDouble(), sy.toDouble())
            g.font = font
            g.drawString(line.text, 0, 0)
            g.transform = saved
            top += h
        }
        g.dispose()
        val pixels = image.raster
        return Raster.of(widthDots, height) { x, y -> pixels.getSample(x, y, 0) < 128 }
    }
}
