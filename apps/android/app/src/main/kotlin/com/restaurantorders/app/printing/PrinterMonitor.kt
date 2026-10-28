package com.restaurantorders.app.printing

import com.restaurantorders.app.data.PrintJobEntity
import com.restaurantorders.app.data.SettingsDao
import com.restaurantorders.printing.PrinterException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant

enum class PrinterState { NOT_SET_UP, CHECKING, ONLINE, OFFLINE }

data class PrinterHealth(val state: PrinterState, val lastContact: Instant? = null, val problem: String? = null)

/**
 * Whether the kitchen printer can be reached (backlog H1). While the app is open it knocks on
 * the printer every 30 seconds, and every ticket sent also counts, so the order screen can warn
 * before a ticket is lost in a printer that's off. Orders still save either way.
 */
class PrinterMonitor(
    private val settings: SettingsDao,
    private val sender: TicketSender,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = Instant::now,
) {
    private val current = MutableStateFlow(PrinterHealth(PrinterState.CHECKING))
    val health: StateFlow<PrinterHealth> = current.asStateFlow()

    suspend fun check(): PrinterHealth {
        val config = settings.get()
        if (config == null || config.printerHost.isBlank()) return set(PrinterHealth(PrinterState.NOT_SET_UP))
        return try {
            withContext(io) { sender.reach(config.printerHost, config.printerPort) }
            set(PrinterHealth(PrinterState.ONLINE, lastContact = now()))
        } catch (e: PrinterException) {
            set(PrinterHealth(PrinterState.OFFLINE, current.value.lastContact, e.message))
        }
    }

    /** Called with every ticket's result by the print queue. */
    fun record(job: PrintJobEntity) {
        when (job.status) {
            PrintStatus.PRINTED.name -> set(PrinterHealth(PrinterState.ONLINE, lastContact = now()))
            PrintStatus.FAILED.name -> set(PrinterHealth(PrinterState.OFFLINE, current.value.lastContact, job.error))
            PrintStatus.NO_PRINTER.name -> set(PrinterHealth(PrinterState.NOT_SET_UP))
        }
    }

    /** Checks until cancelled; the home screen runs this while someone is signed in. */
    suspend fun watch() {
        while (true) {
            check()
            delay(CHECK_EVERY_MS)
        }
    }

    private fun set(health: PrinterHealth) = health.also { current.value = it }

    companion object {
        const val CHECK_EVERY_MS = 30_000L
    }
}
