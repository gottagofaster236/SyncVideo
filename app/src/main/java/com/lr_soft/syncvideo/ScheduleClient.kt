package com.lr_soft.syncvideo

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException


class ScheduleClient(context: Context) : ClientOrServer(context) {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }
    }

    private var serverUrl: String? = null
        get() {
            if (field == null)
                field = searchForServer()
            return field
        }

    override fun fetchSchedule(): Schedule? {
        var schedule: Schedule? = null

        runBlocking {
            val serverUrl = serverUrl ?: return@runBlocking

            val response: HttpResponse
            val responseText: String
            try {
                response = client.get("$serverUrl/schedule")
                responseText = response.readText()
            } catch (e: Exception) {
                handleRequestException(e)
                return@runBlocking
            }

            if (response.status != HttpStatusCode.OK) {
                return@runBlocking
            }
            schedule = Json.decodeFromString(responseText)
        }
        return schedule
    }

    override fun handleMissingFile() {

    }

    override fun start() {}

    override fun stop() {}

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
			    async { checkUrlForServer(it) }
		    }
            val serverCheckResult = serverCheckDeferred.awaitAll()
            serverUrls = urlsToCheck.filterIndexed { index, _ -> serverCheckResult[index] }
        }

        when (serverUrls.size) {
            0 -> Logger.log("Server not found.")
            1 -> return serverUrls[0].also { Logger.log("Server IP: $it") }
            else -> Logger.log("More than one server found! Cannot continue.")
        }
        return null
    }

    private suspend fun checkUrlForServer(url: String): Boolean {
        return try {
            val response: String = client.get("$url/alive")
            response == ScheduleServer.ALIVE_RESPONSE
        } catch (e: Exception) {
            handleRequestException(e)
            return false
        }
    }

    private fun handleRequestException(e: Exception) {
        when (e) {
            is IOException -> return
            is HttpRequestTimeoutException -> return
            else -> throw e
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
}