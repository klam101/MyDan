package com.restaurantorders.printing

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Why a ticket didn't print, in words the cashier can act on. */
class PrinterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A receipt printer on the restaurant network that takes raw ESC/POS on TCP port 9100 (the
 * usual "RAW" or "JetDirect" port). `send` blocks, so the app calls it off the main thread.
 */
class NetworkPrinter(val host: String, val port: Int = 9100, private val timeoutMs: Int = 4000) {
    fun send(bytes: ByteArray) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                socket.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
                socket.shutdownOutput()
            }
        } catch (e: SocketTimeoutException) {
            throw PrinterException("The printer at $host didn't answer. Is it switched on and on the same network as this tablet?", e)
        } catch (e: ConnectException) {
            throw PrinterException("The printer at $host:$port refused the connection. Check the IP address in Settings.", e)
        } catch (e: NoRouteToHostException) {
            throw PrinterException("Can't reach $host. Is this tablet on the restaurant Wi-Fi?", e)
        } catch (e: UnknownHostException) {
            throw PrinterException("\"$host\" isn't a printer address. Enter its IP, e.g. 192.168.1.50.", e)
        } catch (e: IOException) {
            throw PrinterException("The ticket didn't print: ${e.message}", e)
        }
    }
}
