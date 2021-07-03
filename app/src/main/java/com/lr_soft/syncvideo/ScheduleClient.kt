package com.lr_soft.syncvideo

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.system.measureTimeMillis


class ScheduleClient(private val context: Context) : ClientOrServer {
    private val client = HttpClient(CIO) {
        install(HttpTimeout)
    }
    private var serverUrl: String? = null
    private var clientThread: Thread? = null

    private fun searchForServer(): String? {
        Logger.log("Searching for the server")
        val lanIpAddress = getLanIpAddress()
        if (lanIpAddress == null) {
            Logger.log("Could not get LAN IP address")
            return null
        }
        Logger.log("Our IP Address: ${lanIpAddress.hostAddress}")

        val bytes = lanIpAddress.address
        val urlsToCheck = Array(256) { i ->
            bytes[3] = i.toByte()
            val serverAddress = InetAddress.getByAddress(bytes)
            "http://${serverAddress.hostAddress}:${ScheduleServer.PORT}"
        }

        val serverUrls: List<String>

        runBlocking {
		    val serverCheckDeferred = urlsToCheck.map {
			    async {
				    checkUrlForServer(it)
			    }
		    }
            val serverCheckResult = serverCheckDeferred.awaitAll()
            serverUrls = urlsToCheck.filterIndexed { index, _ -> serverCheckResult[index] }
        }

        when (serverUrls.size) {
            0 -> Logger.log("Server not found.")
            1 -> return serverUrls[0]
            else -> Logger.log("More than one server found! Cannot continue.")
        }
        return null
    }

    private suspend fun checkUrlForServer(url: String): Boolean {
        return try {
            val response: String = client.get("$url/alive") {
                timeout {
                    requestTimeoutMillis = 10000
                }
            }
            response == ScheduleServer.ALIVE_RESPONSE
        } catch (e: Exception) {
            when (e) {
                is IOException -> false
                is HttpRequestTimeoutException -> false
                else -> throw e
            }
        }
    }

    private fun getLanIpAddress(): Inet4Address? {
        try {
            for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
                for (inetAddress in networkInterface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address)
                        return inetAddress
                }
            }
        } catch (e: SocketException) {}
        return null
    }

    private fun clientMain() {
        Logger.log("Starting the client")
        serverUrl = searchForServer()
        Logger.log("Server: $serverUrl")
    }

    override fun start() {
        clientThread = Thread(::clientMain).apply { start() }
    }

    override fun stop() {
        Logger.log("Stopping the client")
    }
}