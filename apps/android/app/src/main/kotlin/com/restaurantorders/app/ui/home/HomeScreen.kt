package com.restaurantorders.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.data.enabledOrderTypes
import com.restaurantorders.domain.OrderType
import com.restaurantorders.app.data.zone
import com.restaurantorders.app.ui.LocalNetworkBanner
import com.restaurantorders.app.ui.menu.MenuScreen
import com.restaurantorders.app.ui.order.CartState
import com.restaurantorders.app.ui.order.NewOrderScreen
import com.restaurantorders.app.ui.orders.OrdersScreen
import com.restaurantorders.app.ui.settings.SettingsScreen
import com.restaurantorders.app.ui.staff.StaffScreen
import com.restaurantorders.app.ui.theme.Rail
import com.restaurantorders.app.ui.theme.RailActive
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.label
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Tab(val label: String, val permission: Permission) {
    NEW_ORDER("New order", Permission.ORDER),
    ORDERS("Orders", Permission.ORDERS),
    MENU("Menu", Permission.SOLD_OUT),
    STAFF("Staff", Permission.STAFF),
    SETTINGS("Settings", Permission.SETTINGS),
}

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DATE = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

/** The frame around every screen: a side rail (like Front's) with this person's tabs and Lock. */
@Composable
fun HomeScreen(app: AppContainer, settings: SettingsEntity, staff: Staff) {
    val tabs = Tab.entries.filter { staff.can(it.permission) }
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    if (tab !in tabs) tab = tabs.first()
    // The order being built survives switching tabs (it is cleared when the tablet is locked).
    val cart = remember(staff.id) { CartState(settings.enabledOrderTypes.firstOrNull()?.type ?: OrderType.WALK_IN) }
    // Knock on the printer while someone is signed in, and again whenever its address changes.
    LaunchedEffect(settings.printerHost, settings.printerPort) { app.printer.watch() }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(112.dp).fillMaxHeight().background(Rail).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(52.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text(staff.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Rail)
            }
            Text(staff.name, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(staff.role.label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.padding(4.dp))
            tabs.forEach { t -> RailButton(t.label, active = t == tab) { tab = t } }
            Spacer(Modifier.weight(1f))
            RailClock(settings)
            RailButton("Lock", active = false) { app.session.lock() }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            LocalNetworkBanner()
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.NEW_ORDER -> NewOrderScreen(app, settings, staff, cart, onChangeSaved = { tab = Tab.ORDERS })
                    Tab.ORDERS -> OrdersScreen(app, settings, onChange = { view -> cart.load(view); tab = Tab.NEW_ORDER })
                    Tab.MENU -> MenuScreen(app, staff)
                    Tab.STAFF -> StaffScreen(app, staff)
                    Tab.SETTINGS -> SettingsScreen(app, settings)
                }
            }
        }
    }
}

@Composable
private fun RailButton(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) RailActive else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 6.dp),
    )
}

@Composable
private fun RailClock(settings: SettingsEntity) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Instant.now()
        }
    }
    val here = now.atZone(settings.zone)
    Text(CLOCK.format(here), color = Color.White, fontWeight = FontWeight.SemiBold)
    Text(DATE.format(here), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
}
