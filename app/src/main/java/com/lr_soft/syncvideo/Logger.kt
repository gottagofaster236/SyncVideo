package com.lr_soft.syncvideo

import android.util.Log

object Logger {
    private const val ANDROID_LOG_TAG = "syncVideo"
    private const val MAX_MESSAGES_STORED = 30
    private val logMessages = mutableListOf<String>()
    private val logListeners = mutableSetOf<LogListener>()

    fun log(message: String) {
        Log.i(ANDROID_LOG_TAG, message)
        synchronized(logMessages) {
            logMessages += message
        }
        logListeners.forEach { it.onNewLogMessage() }
    }

    fun getLogs(maxMessages: Int = MAX_MESSAGES_STORED): List<String> {
        synchronized(logMessages) {
            return logMessages.takeLast(maxMessages.coerceAtMost(logMessages.size))
        }
    }

    fun interface LogListener {
        fun onNewLogMessage()
    }

    fun registerLogListener(listener: LogListener) {
        logListeners.add(listener)
    }

    fun unregisterLogListener(listener: LogListener) {
        logListeners.remove(listener)
    }
}