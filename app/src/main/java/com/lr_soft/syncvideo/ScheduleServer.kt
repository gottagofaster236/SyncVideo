package com.lr_soft.syncvideo

import android.content.Context
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

class ScheduleServer(private val context: Context) : ClientOrServer {
    companion object {
        const val PORT = 23232
        const val ALIVE_RESPONSE = "SyncVideo server"
    }
    private val applicationEngine: ApplicationEngine
    private var isRunning = false

    private fun Application.registerRoutes() {
        routing {
            aliveCheckRouting()
        }
    }

    private fun Route.aliveCheckRouting() {
        get("/alive") {
            call.respond(ALIVE_RESPONSE)
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
        if (isRunning)
            return
        Logger.log("Starting the server")
        applicationEngine.start(wait = false)
        isRunning = true
    }

    override fun stop() {
        Logger.log("Stopping the server!")
        applicationEngine.stop(1000, 1000)
        isRunning = false
    }
}