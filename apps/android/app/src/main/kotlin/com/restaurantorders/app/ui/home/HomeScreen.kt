package com.restaurantorders.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.ui.LocalNetworkBanner
import com.restaurantorders.app.ui.order.CartState
import com.restaurantorders.app.ui.order.NewOrderScreen
import com.restaurantorders.app.ui.menu.MenuScreen
import com.restaurantorders.app.ui.orders.OrdersScreen
import com.restaurantorders.app.ui.staff.StaffScreen
import com.restaurantorders.app.ui.settings.SettingsScreen
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.label

enum class Tab(val label: String, val permission: Permission) {
    NEW_ORDER("New order", Permission.ORDER),
    ORDERS("Orders", Permission.ORDERS),
    MENU("Menu", Permission.SOLD_OUT),
    STAFF("Staff", Permission.STAFF),
    SETTINGS("Settings", Permission.SETTINGS),
}

/** The frame around every screen: tabs for this person's role, who's signed in, and Lock. */
@Composable
fun HomeScreen(app: AppContainer, settings: SettingsEntity, staff: Staff) {
    val tabs = Tab.entries.filter { staff.can(it.permission) }
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    if (tab !in tabs) tab = tabs.first()
    // The order being built survives switching tabs (it is cleared when the tablet is locked).
    val cart = remember(staff.id) { CartState() }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val active = t == tab
                    TextButton(onClick = { tab = t }) {
                        Text(
                            t.label,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(settings.restaurantName, fontWeight = FontWeight.Bold)
                    Text("${staff.name} · ${staff.role.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { app.session.lock() }) { Text("Lock") }
            }
        }
        HorizontalDivider()
        LocalNetworkBanner()
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.NEW_ORDER -> NewOrderScreen(app, settings, cart, onChangeSaved = { tab = Tab.ORDERS })
                Tab.ORDERS -> OrdersScreen(app, settings, onChange = { view -> cart.load(view); tab = Tab.NEW_ORDER })
                Tab.MENU -> MenuScreen(app, staff)
                Tab.STAFF -> StaffScreen(app, staff)
                Tab.SETTINGS -> SettingsScreen(app, settings)
            }
        }
    }
}

