package com.restaurantorders.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.ui.home.HomeScreen
import com.restaurantorders.app.ui.pin.PinScreen
import com.restaurantorders.app.ui.setup.SetupScreen

/** Not set up yet → Setup. Nobody signed in → PIN. Otherwise the screens for that person's role. */
@Composable
fun AppRoot(app: AppContainer) {
    val settings by app.settings.watch().collectAsState(initial = Loading)
    val staff by app.session.staff.collectAsState()

    when {
        settings === Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        settings == null -> SetupScreen(app)
        staff == null -> PinScreen(app, settings!!.restaurantName)
        else -> HomeScreen(app, settings!!, staff!!)
    }
}

/** Stands in for the settings until the database has answered. */
private val Loading = SettingsEntity(restaurantName = "", taxRate = 0.0, timezone = "UTC")
