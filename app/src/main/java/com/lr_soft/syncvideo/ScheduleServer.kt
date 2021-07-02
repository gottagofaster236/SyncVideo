package com.lr_soft.syncvideo

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

class ScheduleServer(port: Int = 1234) {
    private val applicationEngine: ApplicationEngine
    private var isRunning = false

    init {
        applicationEngine = embeddedServer(Netty, port) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/") {
                    call.respond(mapOf("message" to "hello world"))
                }
            }
        }
    }

    fun start() {
        if (isRunning)
            return
        Logger.log("Starting server!")
        applicationEngine.start(wait = false)
        isRunning = true
    }

    fun stop() {
        applicationEngine.stop(1000, 1000)
        isRunning = false
    }
}