package com.lr_soft.syncvideo

import android.content.Context
import android.os.SystemClock

abstract class ClientOrServer(protected val context: Context) {
    abstract fun start()

    abstract fun stop()

    abstract fun fetchSchedule(): Schedule?

    abstract fun handleMissingFile(filename: String)

    protected var fileManager = FileManager(context)

    var schedule: Schedule? = null
        get() {
            val curTime = SystemClock.elapsedRealtime()
            val shouldUpdate = (field == null ||
                    curTime - scheduleLastUpdatedElapsedRealtime > SCHEDULE_UPDATE_PERIOD_MS)
            if (shouldUpdate) {
                field = fetchSchedule()
                if (field == null)
                    Logger.log("Could not fetch schedule.")
                scheduleLastUpdatedElapsedRealtime = curTime
            }
            return field
        }
        private set

    private var scheduleLastUpdatedElapsedRealtime: Long = Long.MIN_VALUE

    private companion object {
        const val SCHEDULE_UPDATE_PERIOD_MS = 10_000
    }

}