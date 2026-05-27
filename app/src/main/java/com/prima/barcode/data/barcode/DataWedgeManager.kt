package com.prima.barcode.data.barcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import timber.log.Timber

private const val DW_ACTION   = "com.symbol.datawedge.api.ACTION"
private const val SCAN_ACTION = "com.prima.barcode.SCAN"
private const val SCAN_EXTRA  = "com.symbol.datawedge.data_string"

object DataWedgeManager {

    fun configure(context: Context) {
        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.CREATE_PROFILE", "PrimaBarcode")
        })

        val appBundle = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val intentPlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("intent_output_enabled", "true")
                putString("intent_action",         SCAN_ACTION)
                putString("intent_delivery",       "2")
            })
        }
        val pluginList = ArrayList<Bundle>().apply { add(intentPlugin) }

        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.SET_CONFIG", Bundle().apply {
                putString("PROFILE_NAME",    "PrimaBarcode")
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE",     "UPDATE")
                putParcelableArray("APP_LIST", arrayOf(appBundle))
                putParcelableArrayList("PLUGIN_CONFIG", pluginList)
            })
        })
        Timber.d("DataWedge profile configured")
    }

    fun setAudioFeedback(context: Context, muted: Boolean) {
        val barcodePlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "BARCODE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", Bundle().apply {
                // Empty string disables the decode beep; non-empty restores default notification sound
                putString("decode_audio_feedback_uri",
                    if (muted) "" else "content://media/internal/audio/media/0")
            })
        }
        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.SET_CONFIG", Bundle().apply {
                putString("PROFILE_NAME",    "PrimaBarcode")
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE",     "UPDATE")
                putParcelableArrayList("PLUGIN_CONFIG", ArrayList<Bundle>().apply { add(barcodePlugin) })
            })
        })
    }

    fun createReceiver(onScan: (String) -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SCAN_ACTION) return
            val value = intent.getStringExtra(SCAN_EXTRA)?.trim() ?: return
            if (value.isNotEmpty()) onScan(value)
        }
    }

    fun intentFilter() = IntentFilter(SCAN_ACTION)
}
