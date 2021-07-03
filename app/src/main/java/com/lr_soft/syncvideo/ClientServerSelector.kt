package com.lr_soft.syncvideo

import android.content.Context
import androidx.preference.PreferenceManager

class ClientServerSelector(private val context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var scheduleServer: ScheduleServer? = null
    private var scheduleClient: Any? = null

    fun update() {
        val newValue = sharedPreferences.getString(context.getString(R.string.pref_device_type_key), "client")
        if (newValue == "client") {
            scheduleServer?.stop()
            scheduleServer = null
            Logger.log("Client not implemented yet")
        }
        else {
            if (scheduleServer == null) {
                scheduleServer = ScheduleServer().apply { start() }
            }
        }
    }
}