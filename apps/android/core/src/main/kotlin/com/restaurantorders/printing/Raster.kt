// Tickets printed as a picture instead of text. Printers that only print images (Star TSP100)
// need this, and it makes Chinese print on any printer because the tablet draws the characters.
package com.restaurantorders.printing

import com.restaurantorders.domain.LINE_WIDTH
import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.TicketLine
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** How a ticket goes to the printer: as text, or as a picture in one of two formats. */
enum class PrintMode {
    /** ESC/POS text; Chinese only if the printer has GB18030 built in. */
    TEXT,

    /** An ESC/POS picture (GS v 0): most receipt printers, including ZBS, Xprinter and Epson. */
    IMAGE,

    /** A Star Graphic picture: Star printers that only print images, such as the TSP100. */
    STAR_IMAGE,
}

/** Dots across the printable width: 80 mm paper is 576 dots at 203 dpi; 58 mm paper is 384. */
const val DOTS_80MM = 576

/** A black-and-white picture, one bit per dot, rows packed 8 dots to a byte (first dot = high bit). */
class Raster(val width: Int, val height: Int, val bits: ByteArray) {
    init {
        require(width % 8 == 0) { "Width must be a multiple of 8 dots" }
        require(bits.size == width / 8 * height) { "Expected ${width / 8 * height} bytes" }
    }

    val bytesPerRow get() = width / 8

    fun isBlack(x: Int, y: Int): Boolean = bits[y * bytesPerRow + x / 8].toInt() and (0x80 ushr (x % 8)) != 0

    fun row(y: Int): ByteArray = bits.copyOfRange(y * bytesPerRow, (y + 1) * bytesPerRow)

    companion object {
        /** Packs a grid of dots; `black(x, y)` says whether a dot is printed. */
        fun of(width: Int, height: Int, black: (x: Int, y: Int) -> Boolean): Raster {
            val bytesPerRow = width / 8
            val bits = ByteArray(bytesPerRow * height)
            for (y in 0 until height) for (x in 0 until width) {
                if (black(x, y)) bits[y * bytesPerRow + x / 8] = (bits[y * bytesPerRow + x / 8].toInt() or (0x80 ushr (x % 8))).toByte()
            }
            return Raster(width, height, bits)
        }
    }
}

/**
 * Where each ticket line goes on the picture, shared by the tablet's and the computer's
 * drawing code so tickets look the same. A normal line fits LINE_WIDTH characters across, as on
 * a text ticket; TALL doubles the height and BIG doubles both, as the printer's own sizes do.
 */
class TicketLayout(val widthDots: Int) {
    /** Width of one character of normal text. */
    val cellWidth: Float = widthDots.toFloat() / LINE_WIDTH

    /** Height of a normal line, with a little space between lines. */
    val lineHeight: Int = ceil(cellWidth * 2.1f).toInt()

    fun scaleX(style: LineStyle) = if (style == LineStyle.BIG) 2f else 1f

    fun scaleY(style: LineStyle) = if (style == LineStyle.NORMAL) 1f else 2f

    fun height(line: TicketLine) = (lineHeight * scaleY(line.style)).toInt()

    /** Total height, with a margin at the top so the first line isn't cut. */
    fun height(lines: List<TicketLine>) = TOP_MARGIN + lines.sumOf { height(it) }

    companion object {
        const val TOP_MARGIN = 8
    }
}

/** Draws ticket lines into a picture; the Android app and the computer each have one. */
fun interface TicketRasterizer {
    fun render(lines: List<TicketLine>, widthDots: Int): Raster
}

/** Turns a picture into printer commands. */
object RasterCommands {
    private const val ESC = 0x1b
    private const val GS = 0x1d

    /** Rows sent per ESC/POS image command; small bands suit printers with small buffers. */
    private const val BAND = 128

    /** ESC/POS "print raster image" (GS v 0), then feed and cut as the text tickets do. */
    fun escPos(raster: Raster): ByteArray {
        val out = ByteArrayOutputStream()
        fun bytes(vararg b: Int) = b.forEach { out.write(it) }
        bytes(ESC, 0x40) // reset
        var y = 0
        while (y < raster.height) {
            val rows = minOf(BAND, raster.height - y)
            bytes(GS, 0x76, 0x30, 0x00, raster.bytesPerRow and 0xff, raster.bytesPerRow shr 8, rows and 0xff, rows shr 8)
            out.write(raster.bits, y * raster.bytesPerRow, rows * raster.bytesPerRow)
            y += rows
        }
        bytes(GS, 0x56, 0x42, 0x04) // feed 4 lines, then partial cut
        return out.toByteArray()
    }

    /**
     * Star Graphic mode, for Star printers that only print images (TSP100 series): enter raster
     * mode, send each row ("b" + length + dots; blank rows as a skip), then feed and cut.
     */
    fun star(raster: Raster): ByteArray {
        val out = ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun bytes(vararg b: Int) = b.forEach { out.write(it) }
        bytes(ESC); ascii("*rR") // initialise raster mode
        bytes(ESC); ascii("*rA") // enter raster mode
        bytes(ESC); ascii("*rP0"); bytes(0) // continuous paper: the page is as long as the picture
        bytes(ESC); ascii("*rE13"); bytes(0) // at the end: feed to the cutter and cut
        var blank = 0
        fun skipBlank() {
            if (blank > 0) {
                bytes(ESC); ascii("*rY$blank"); bytes(0)
                blank = 0
            }
        }
        for (y in 0 until raster.height) {
            val row = raster.row(y)
            val used = row.indexOfLast { it.toInt() != 0 } + 1
            if (used == 0) {
                blank++
                continue
            }
            skipBlank()
            bytes('b'.code, used and 0xff, used shr 8)
            out.write(row, 0, used)
        }
        skipBlank()
        bytes(ESC, 0x0c, 0x00) // end of page: print, feed and cut
        bytes(ESC); ascii("*rB") // leave raster mode
        return out.toByteArray()
    }
}
