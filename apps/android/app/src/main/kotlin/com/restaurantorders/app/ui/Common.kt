// Small pieces shared by the screens.
package com.restaurantorders.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.ui.theme.Info
import com.restaurantorders.app.ui.theme.InfoSoft
import com.restaurantorders.app.ui.theme.Ok
import com.restaurantorders.app.ui.theme.OkSoft
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.app.ui.theme.RedSoft
import com.restaurantorders.app.ui.theme.Warn
import com.restaurantorders.app.ui.theme.WarnSoft
import com.restaurantorders.domain.OrderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class BannerKind(val fg: Color, val bg: Color) {
    OK(Ok, OkSoft),
    ERROR(Red, RedSoft),
    WARN(Warn, WarnSoft),
    INFO(Info, InfoSoft),
}

data class BannerMessage(val kind: BannerKind, val text: String)

@Composable
fun Banner(message: BannerMessage, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(message.kind.bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(message.text, color = message.kind.fg, fontWeight = FontWeight.SemiBold)
    }
}

/** The message to show for a failed action: ours as is, anything else generic. */
fun Throwable.userMessage(): String = when (this) {
    is ActionException, is OrderException -> message ?: "Something went wrong"
    else -> "Something went wrong: ${message ?: javaClass.simpleName}"
}

/** Runs an action from a button: tracks busy, and turns failures into an error message. */
class Action(private val launch: (suspend () -> Unit) -> Unit) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun run(block: suspend () -> Unit) = launch {
        busy = true
        error = null
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.userMessage()
        } finally {
            busy = false
        }
    }
}

@Composable
fun rememberAction(): Action {
    val scope = rememberCoroutineScope()
    return remember { Action { block -> scope.launch { block() } } }
}

