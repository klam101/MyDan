package com.restaurantorders.app.printing

import com.restaurantorders.app.data.AppJson
import com.restaurantorders.app.data.PrintJobDao
import com.restaurantorders.app.data.PrintJobEntity
import com.restaurantorders.app.data.SettingsDao
import com.restaurantorders.app.data.newId
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.printing.EscPos
import com.restaurantorders.printing.NetworkPrinter
import com.restaurantorders.printing.PrinterException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

enum class PrintStatus { QUEUED, PRINTED, FAILED, NO_PRINTER }

/** Sends one stored ticket's bytes to a printer; swapped for a fake in tests. */
interface TicketSender {
    fun send(host: String, port: Int, bytes: ByteArray)

    /** Throws PrinterException when the printer doesn't answer; prints nothing. */
    fun reach(host: String, port: Int)
}

val networkSender = object : TicketSender {
    override fun send(host: String, port: Int, bytes: ByteArray) = NetworkPrinter(host, port).send(bytes)

    override fun reach(host: String, port: Int) = NetworkPrinter(host, port, timeoutMs = 2000).reach()
}

/**
 * Kitchen tickets. Every ticket is stored before it is sent, so an order is never lost because
 * of the printer, and a failed ticket can be printed again.
 */
class PrintQueue(
    private val jobs: PrintJobDao,
    private val settings: SettingsDao,
    private val sender: TicketSender = networkSender,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = Instant::now,
    /** Told how every ticket went, so the printer's status stays current between checks. */
    private val onResult: (PrintJobEntity) -> Unit = {},
) {
    /** Stores the ticket, sends it, and returns the job with how it went. */
    suspend fun print(orderId: String?, kind: TicketKind, ticket: List<TicketLine>): PrintJobEntity {
        val job = PrintJobEntity(
            id = newId(),
            orderId = orderId,
            kind = kind.name,
            status = PrintStatus.QUEUED.name,
            createdAt = now().toEpochMilli(),
            ticketJson = AppJson.encodeToString(ticket),
        )
        jobs.insert(job)
        return deliver(job)
    }

    /** Sends a stored ticket again, e.g. after the printer was fixed. */
    suspend fun retry(jobId: String): PrintJobEntity = deliver(jobs.get(jobId) ?: error("No print job $jobId"))

    private suspend fun deliver(job: PrintJobEntity): PrintJobEntity {
        val config = settings.get()
        if (config == null || config.printerHost.isBlank()) return finish(job, PrintStatus.NO_PRINTER, null)
        val ticket = AppJson.decodeFromString<List<TicketLine>>(job.ticketJson)
        return try {
            withContext(io) { sender.send(config.printerHost, config.printerPort, EscPos.render(ticket, config.printerChinese)) }
            finish(job, PrintStatus.PRINTED, null)
        } catch (e: PrinterException) {
            finish(job, PrintStatus.FAILED, e.message)
        }
    }

    private suspend fun finish(job: PrintJobEntity, status: PrintStatus, error: String?): PrintJobEntity {
        jobs.setStatus(job.id, status.name, error)
        return job.copy(status = status.name, error = error).also(onResult)
    }
}
