package com.restaurantorders.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.restaurantorders.app.AppContainer
import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.discountPresets
import com.restaurantorders.app.data.orderTypes
import com.restaurantorders.app.data.voidReasons
import com.restaurantorders.app.data.withLists
import com.restaurantorders.app.ui.MoneyField
import com.restaurantorders.app.data.zone
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.app.ui.Banner
import com.restaurantorders.app.ui.BannerKind
import com.restaurantorders.app.ui.BannerMessage
import com.restaurantorders.app.ui.rememberAction
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.buildTestTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

private val LANGUAGES = listOf(TicketLanguage.EN to "English", TicketLanguage.ZH to "Chinese", TicketLanguage.BOTH to "Both")

/** Restaurant details, tax, ticket language and the kitchen printer. Owners only. */
@Composable
fun SettingsScreen(app: AppContainer, saved: SettingsEntity) {
    var name by remember(saved) { mutableStateOf(saved.restaurantName) }
    var phone by remember(saved) { mutableStateOf(saved.phone) }
    var address by remember(saved) { mutableStateOf(saved.address) }
    var tax by remember(saved) { mutableStateOf((saved.taxRate * 100).toBigDecimal().stripTrailingZeros().toPlainString()) }
    var timezone by remember(saved) { mutableStateOf(saved.timezone) }
    var language by remember(saved) { mutableStateOf(saved.ticketLanguage) }
    var host by remember(saved) { mutableStateOf(saved.printerHost) }
    var port by remember(saved) { mutableStateOf(saved.printerPort.toString()) }
    var chinese by remember(saved) { mutableStateOf(saved.printerChinese) }
    var types by remember(saved) { mutableStateOf(saved.orderTypes) }
    var discounts by remember(saved) { mutableStateOf(saved.discountPresets) }
    var reasons by remember(saved) { mutableStateOf(saved.voidReasons) }
    var openLimit by remember(saved) { mutableStateOf<Int?>(saved.openItemLimitCents) }
    val printer by app.printer.health.collectAsState()
    var message by remember { mutableStateOf<BannerMessage?>(null) }
    var printMessage by remember { mutableStateOf<BannerMessage?>(null) }
    var exportMessage by remember { mutableStateOf<BannerMessage?>(null) }
    val action = rememberAction()
    val context = LocalContext.current
    // Android asks where to save the file (Downloads, Google Drive, a USB stick...).
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        action.run {
            val json = app.export()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
            exportMessage = BannerMessage(BannerKind.OK, "Saved a copy of the menu, staff and every order.")
        }
    }

    fun form(): SettingsEntity = saved.copy(
        restaurantName = name,
        phone = phone,
        address = address,
        taxRate = (tax.toDoubleOrNull() ?: throw ActionException("Sales tax must be a number, e.g. 8.875")) / 100,
        timezone = timezone.trim(),
        ticketLanguage = language,
        printerHost = host,
        printerPort = port.toIntOrNull() ?: throw ActionException("Printer port must be a number like 9100"),
        printerChinese = chinese,
        openItemLimitCents = openLimit ?: throw ActionException("The open item limit must be a price, e.g. 20.00"),
    ).withLists(types, discounts, reasons)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            message?.let { Banner(it) }
            action.error?.let { Banner(BannerMessage(BannerKind.ERROR, it)) }

            Section("Restaurant") {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(phone, { phone = it }, Modifier.weight(1f), label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, Modifier.weight(2f), label = { Text("Address") }, singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        tax, { tax = it }, Modifier.weight(1f), label = { Text("Sales tax %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        timezone, { timezone = it }, Modifier.weight(2f), singleLine = true,
                        label = { Text("Time zone (order numbers restart at midnight here)") },
                    )
                }
            }

            Section("Kitchen tickets") {
                Text("Dish names on tickets")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGUAGES.forEach { (value, label) ->
                        FilterChip(selected = language == value.name, onClick = { language = value.name }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        host, { host = it }, Modifier.weight(2f), singleLine = true,
                        label = { Text("Printer IP address (from its self-test page)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        port, { port = it }, Modifier.weight(1f), label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(chinese, { chinese = it })
                    Text("Printer can print Chinese characters (turn on only if the Chinese test ticket looks right)")
                }
                Text(
                    "Leave the IP empty to run without a printer; tickets then only show on screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = !action.busy,
                    onClick = {
                        action.run {
                            printMessage = BannerMessage(BannerKind.INFO, "Printing a test ticket…")
                            val settings = form()
                            app.settings.save(settings)
                            val ticket = buildTestTicket(settings.restaurantName, "${settings.printerHost}:${settings.printerPort}", Instant.now(), settings.zone)
                            val job = app.printing.print(null, TicketKind.TEST, ticket)
                            printMessage = when (PrintStatus.valueOf(job.status)) {
                                PrintStatus.PRINTED -> BannerMessage(BannerKind.OK, "Test ticket sent to the printer. Check that it came out.")
                                PrintStatus.NO_PRINTER -> BannerMessage(BannerKind.WARN, "No printer IP set, so nothing was printed.")
                                else -> BannerMessage(BannerKind.ERROR, job.error ?: "The test ticket didn't print.")
                            }
                        }
                    },
                ) { Text("Save and print test ticket") }
                printMessage?.let { Banner(it) }
                PrinterStatusLine(printer, saved) { action.run { app.printer.check() } }
            }

            Section("Ordering") {
                OrderTypesEditor(types) { types = it }
                Text("Discounts the cashier can pick", style = MaterialTheme.typography.titleSmall)
                DiscountsEditor(discounts) { discounts = it }
                Text("Reasons offered when an order is voided", style = MaterialTheme.typography.titleSmall)
                WordsEditor(reasons, "New reason, e.g. Customer left") { reasons = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoneyField(openLimit, { openLimit = it }, "Open item limit", Modifier.widthIn(max = 200.dp))
                    Text("A cashier needs a manager's PIN to ring up an open item above this price.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Section("Back up") {
                Text("Save everything on this tablet (settings, menu, staff names and every order) as one file. PINs are never included.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { exportTo.launch("orders-backup-${LocalDate.now()}.json") }, enabled = !action.busy) { Text("Export data") }
                exportMessage?.let { Banner(it) }
            }

            Button(
                onClick = {
                    action.run {
                        app.settings.save(form())
                        message = BannerMessage(BannerKind.OK, "Settings saved.")
                    }
                },
                enabled = !action.busy,
                modifier = Modifier.align(Alignment.End),
            ) { Text(if (action.busy) "Saving…" else "Save settings") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
