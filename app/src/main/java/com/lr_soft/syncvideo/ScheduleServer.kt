package com.lr_soft.syncvideo

import android.content.Context
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*
import java.nio.charset.StandardCharsets

class ScheduleServer(context: Context) : ClientOrServer(context) {
    companion object {
        const val PORT = 23232
        const val ALIVE_RESPONSE = "SyncVideo server"
        private const val SCHEDULE_FILENAME = "schedule.txt"
    }
    private val applicationEngine: ApplicationEngine

    private fun Application.registerRoutes() {
        routing {
            aliveCheckRouting()
            scheduleRouting()
            getFileRouting()
        }
    }

    private fun Route.aliveCheckRouting() {
        get("/alive") {
            call.respond(ALIVE_RESPONSE)
        }
    }

    private fun Route.scheduleRouting() {
        get("/schedule") {
            schedule.let {
                if (it != null) {
                    call.respond(it)
                }
                else {
                    call.respondFileNotFound()
                }
            }
        }
    }

    private fun Route.getFileRouting() {
        get("/file/{filename}") {
            val filename = call.parameters["filename"] ?: return@get call.respondText(
                "Missing filename",
                status = HttpStatusCode.BadRequest
            )
            val file = fileManager.getFile(filename) ?: return@get call.respondFileNotFound()
            val inputStream = withContext(Dispatchers.Default) {
                this@ScheduleServer.context.contentResolver.openInputStream(file.uri)
            } ?: return@get call.respondFileNotFound()

            call.respond(InputStreamContent(inputStream))
        }
    }

    private suspend fun ApplicationCall.respondFileNotFound() {
        respondText(
            "File not found",
            status = HttpStatusCode.NotFound
        )
    }

    private class InputStreamContent(private val inputStream: InputStream) : OutgoingContent.ReadChannelContent() {
        override fun readFrom(): ByteReadChannel {
            return inputStream.toByteReadChannel()
        }
    }

    init {
        applicationEngine = embeddedServer(Netty, PORT) { module() }
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json()
        }
        registerRoutes()
    }

    override fun start() {
        Logger.log("Starting the server")
        applicationEngine.start(wait = false)
    }

    override fun stop() {
        Logger.log("Stopping the server!")
        applicationEngine.stop(1000, 1000)
    }

    override fun fetchSchedule(): Schedule? {
        try {
            val scheduleFile = fileManager.getFile(SCHEDULE_FILENAME) ?: return null
            val inputStream = context.contentResolver.openInputStream(scheduleFile.uri)
            val bufferedReader =
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val scheduleJson = bufferedReader.use { it.readText() }
            return try {
                Json.decodeFromString<Schedule>(scheduleJson)
            } catch (e: SerializationException) {
                Logger.log("Wrong schedule file format: \"${e.message}\"")
                null
            }
        } catch (e: IOException) {
            return null
        }
    }

    override fun handleMissingFile(filename: String) {
        // We can't do anything if a file is missing on the server side.
    }
}