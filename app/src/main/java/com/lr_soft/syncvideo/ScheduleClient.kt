package com.lr_soft.syncvideo

import android.content.Context
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class ScheduleClient(context: Context) : ClientOrServer(context) {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
    }

    private var serverUrl: String? = null
        get() {
            if (field == null)
                field = searchForServer()
            return field
        }

    private val currentlyDownloading = mutableSetOf<String>()

    override fun fetchSchedule(): Schedule? {
        var schedule: Schedule? = null

        runBlocking {
            val serverUrl = serverUrl ?: return@runBlocking

            val response: HttpResponse
            try {
                schedule = client.get("$serverUrl/schedule") {
                    accept(ContentType.Application.Json)
                }
            } catch (e: Exception) {
                handleRequestException(e)
                return@runBlocking
            }
        }
        return schedule
    }

    override fun handleMissingFile(filename: String) {
        // Checking before launching a thread to save on resources.
        if (alreadyDownloading(filename)) {
            return
        }

        thread {
            runBlocking {
                synchronized(currentlyDownloading) {
                    if (alreadyDownloading(filename)) {
                        return@runBlocking
                    }
                    currentlyDownloading.add(filename)
                }
                downloadFile(filename)
                currentlyDownloading.remove(filename)
            }
        }
    }

    private fun alreadyDownloading(filename: String): Boolean {
        synchronized(currentlyDownloading) {
            return currentlyDownloading.contains(filename)
        }
    }

    private suspend fun downloadFile(filename: String) {
        Logger.log("Starting downloading $filename")
        val folder = fileManager.folder
        val tmpFile = folder.createFile("text/plain", "$filename.tmp") ?: return
        var success = false

        try {
            val serverUrl = serverUrl ?: return
            client.get<HttpStatement>("$serverUrl/file/$filename"){
                timeout {
                    requestTimeoutMillis = TimeUnit.DAYS.toMillis(1)
                }
            }.execute { httpResponse ->
                val outputStream = withContext(Dispatchers.Default) {
                    context.contentResolver.openOutputStream(tmpFile.uri)
                } ?: return@execute

                outputStream.use {
                    val channel = httpResponse.receive<ByteReadChannel>()
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                        while (!packet.isEmpty) {
                            val bytes = packet.readBytes()
                            it.write(bytes)
                        }
                    }
                    success = true
                }
            }
        } catch (e: Exception) {
            handleRequestException(e)
        } finally {
            if (success) {
                tmpFile.renameTo(filename)
                Logger.log("Finished downloading $filename")
            }
            else {
                tmpFile.delete()
                Logger.log("Failed downloading $filename")
            }
        }
    }

    override fun start() {
        Logger.log("Created the client!")
    }

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
            val response = client.get<String>("$url/alive")
            response == ScheduleServer.ALIVE_RESPONSE
        } catch (e: Exception) {
            handleRequestException(e)
            return false
        }
    }

    private fun handleRequestException(e: Exception) {
        if (e is ClientRequestException) {
            // For example, file not found
            return
        } else if (e is IOException || e is HttpRequestTimeoutException) {
            serverUrl = null  // Have to find server again since this URL isn't working.
            return
        } else {
            throw e
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