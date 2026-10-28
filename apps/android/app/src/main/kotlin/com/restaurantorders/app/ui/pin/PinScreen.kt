package com.restaurantorders.app.ui.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer

/** The tablet's lock screen: whoever enters their PIN takes the next orders. */
@Composable
fun PinScreen(app: AppContainer, restaurantName: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Keys shrink on short screens (a small tablet held sideways) so all four rows fit.
        val keySize = ((maxHeight - 260.dp) / 4 - 10.dp).coerceIn(48.dp, 80.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(restaurantName.uppercase(), style = MaterialTheme.typography.titleLarge)
            Text("Enter your PIN", style = MaterialTheme.typography.headlineSmall)
            PinPad(app, hint = "Whoever enters their PIN takes the next orders.", keySize = keySize) { pin ->
                app.staff.signIn(pin)
            }
        }
    }
}
