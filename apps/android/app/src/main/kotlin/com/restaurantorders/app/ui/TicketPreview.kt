package com.restaurantorders.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurantorders.app.ui.theme.Line
import com.restaurantorders.domain.LineStyle
import com.restaurantorders.domain.TicketLine

/** Shows a kitchen ticket the way the 80 mm printer lays it out. */
@Composable
fun TicketPreview(ticket: List<TicketLine>, modifier: Modifier = Modifier) {
    Column(
        modifier.width(340.dp).background(Color.White).border(1.dp, Line).padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        ticket.forEach { line ->
            val size = when (line.style) {
                LineStyle.NORMAL -> 11.sp
                LineStyle.TALL -> 14.sp
                LineStyle.BIG -> 22.sp
            }
            Text(
                line.text.ifEmpty { " " },
                fontFamily = FontFamily.Monospace,
                fontSize = size,
                lineHeight = size * 1.3f,
                fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                color = if (line.invert) Color.White else Color.Black,
                modifier = if (line.invert) Modifier.background(Color.Black).padding(horizontal = 6.dp) else Modifier,
            )
        }
    }
}
