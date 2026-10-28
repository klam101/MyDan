package com.restaurantorders.app.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.app.ui.setup.PinField
import com.restaurantorders.app.ui.theme.Muted
import com.restaurantorders.app.ui.theme.Red
import com.restaurantorders.domain.Role
import com.restaurantorders.domain.assignableRoles
import com.restaurantorders.domain.canManage
import com.restaurantorders.domain.label

/** Who works here, their role, and the PIN they sign in with. Owners and managers. */
@Composable
fun StaffScreen(app: AppContainer, me: Staff) {
    val staff by app.staff.watchAll().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Staff?>(null) }
    var notice by remember { mutableStateOf<BannerMessage?>(null) }
    val action = rememberAction()
    val roles = assignableRoles(me.role)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Staff", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) { Text("+ Add staff") }
        }
        Text(
            "Owners manage everyone and the settings. Managers edit the menu and manage cashiers. " +
                "Cashiers take orders and mark dishes sold out. Everyone signs in on the tablet with their own PIN.",
            color = Muted,
        )
        notice?.let { Banner(it) }
        action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(staff, key = { it.id }) { s ->
                val manageable = canManage(me.role, s.role)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name + if (s.id == me.id) "  (you)" else "", fontWeight = FontWeight.SemiBold, color = if (s.active) MaterialTheme.colorScheme.onSurface else Muted)
                            Text(if (s.active) "Active" else "Turned off", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (manageable && roles.size > 1) {
                            RolePicker(s.role, roles) { role -> action.run { app.staff.update(s.id, role = role); notice = BannerMessage(BannerKind.OK, "${s.name} is now ${role.label.lowercase()}.") } }
                        } else {
                            Text(s.role.label, modifier = Modifier.width(120.dp))
                        }
                        if (manageable && s.active) OutlinedButton(onClick = { pinFor = s }) { Text("Change PIN") }
                        if (manageable && s.id != me.id) {
                            TextButton(onClick = {
                                action.run {
                                    app.staff.update(s.id, active = !s.active)
                                    notice = BannerMessage(BannerKind.OK, if (s.active) "${s.name} can no longer sign in." else "${s.name} can sign in again.")
                                }
                            }) { Text(if (s.active) "Turn off" else "Turn on", color = if (s.active) Red else MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddStaffDialog(app, roles, onDone = { added -> adding = false; added?.let { notice = BannerMessage(BannerKind.OK, "${it.name} was added. They sign in with their PIN.") } })
    }
    pinFor?.let { s ->
        PinDialog(s, onDismiss = { pinFor = null }) { pin ->
            pinFor = null
            action.run { app.staff.update(s.id, pin = pin); notice = BannerMessage(BannerKind.OK, "${s.name}'s PIN is changed.") }
        }
    }
}

@Composable
private fun RolePicker(role: Role, roles: List<Role>, onPick: (Role) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.width(120.dp)) {
        OutlinedButton(onClick = { open = true }) { Text(role.label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            roles.forEach { r -> DropdownMenuItem(text = { Text(r.label) }, onClick = { open = false; if (r != role) onPick(r) }) }
        }
    }
}

@Composable
private fun AddStaffDialog(app: AppContainer, roles: List<Role>, onDone: (Staff?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.CASHIER) }
    var pin by remember { mutableStateOf("") }
    val action = rememberAction()
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Add staff") },
        text = {
            Column(Modifier.widthIn(min = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) }) }
                }
                PinField(pin, { pin = it }, "Their 4-digit PIN", Modifier.fillMaxWidth())
                action.error?.let { Text(it, color = Red) }
            }
        },
        confirmButton = { Button(enabled = !action.busy, onClick = { action.run { onDone(app.staff.add(name, role, pin)) } }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun PinDialog(staff: Staff, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New PIN for ${staff.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinField(pin, { pin = it }, "New PIN")
                PinField(pin2, { pin2 = it }, "PIN again")
                error?.let { Text(it, color = Red) }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = when {
                    pin.length != 4 -> "A PIN is 4 digits"
                    pin != pin2 -> "The two PINs don't match"
                    else -> null
                }
                if (error == null) onSave(pin)
            }) { Text("Save PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

