package com.lr_soft.syncvideo

import android.content.Context
import androidx.preference.PreferenceManager

class ClientServerSelector(private val context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var clientOrServer: ClientOrServer? = null
    private var currentDeviceType: String? = null

    fun updateSelection() {
        val newValue = sharedPreferences.getString(context.getString(R.string.pref_device_type_key), "client")
        if (currentDeviceType == newValue)
            return
        currentDeviceType = newValue
        clientOrServer?.stop()
        clientOrServer =
            if (newValue == "client") ScheduleClient(context) else ScheduleServer(context)
        clientOrServer?.start()
    }
}