package com.roomies.expensetracker.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceConfig {

    private const val MY_DEVICE_ID = "c9cbc5df15aae674"

    @SuppressLint("HardwareIds")
    fun isMyDevice(context: Context): Boolean {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) == MY_DEVICE_ID
    }
}