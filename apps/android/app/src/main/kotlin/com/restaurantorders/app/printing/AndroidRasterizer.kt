package com.restaurantorders.app.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.printing.Raster
import com.restaurantorders.printing.TicketLayout
import com.restaurantorders.printing.TicketRasterizer

/**
 * Draws tickets as pictures with Android's graphics, for printers set to print images. The
 * tablet's fonts draw the Chinese, so it prints on any printer. Follows TicketLayout, like the
 * computer's version in core/tools/AwtRasterizer.kt.
 */
object AndroidRasterizer : TicketRasterizer {
    override fun render(lines: List<TicketLine>, widthDots: Int): Raster {
        val layout = TicketLayout(widthDots)
        val height = layout.height(lines)
        val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
        // Size the font so one character of normal text is one cell wide.
        paint.textSize = 100f
        paint.textSize = 100f * layout.cellWidth / paint.measureText("0")
        val fill = Paint().apply { color = Color.BLACK }

        var top = TicketLayout.TOP_MARGIN.toFloat()
        for (line in lines) {
            val h = layout.height(line)
            paint.isFakeBoldText = line.bold
            paint.textScaleX = 1f
            val textWidth = paint.measureText(line.text)
            // Shrink a line that is too wide (e.g. Chinese characters) rather than cut it off.
            val sx = minOf(layout.scaleX(line.style), widthDots / textWidth.coerceAtLeast(1f))
            val sy = layout.scaleY(line.style)
            if (line.invert) canvas.drawRect(0f, top, textWidth * sx + 4f, top + h, fill)
            paint.color = if (line.invert) Color.WHITE else Color.BLACK
            val metrics = paint.fontMetrics
            val textHeight = (metrics.descent - metrics.ascent) * sy
            canvas.save()
            canvas.translate(2f, top + (h - textHeight) / 2f - metrics.ascent * sy)
            canvas.scale(sx, sy)
            canvas.drawText(line.text, 0f, 0f, paint)
            canvas.restore()
            top += h
        }

        val pixels = IntArray(widthDots * height)
        bitmap.getPixels(pixels, 0, widthDots, 0, 0, widthDots, height)
        bitmap.recycle()
        // A dot prints where the drawing is darker than mid-grey.
        return Raster.of(widthDots, height) { x, y ->
            val p = pixels[y * widthDots + x]
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000 < 128
        }
    }
}
