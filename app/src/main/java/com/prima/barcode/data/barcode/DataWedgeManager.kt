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

// DataWedge aim_type: single shot, one pull one scan. Pinned rather than inherited so the trigger
// behaves the same on every device — a unit left in continuous read by another profile would
// otherwise deliver a burst of scans the app has no reason to expect.
private const val AIM_TYPE_TRIGGER = "0"

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
        // QR is turned on explicitly because sign-in codes depend on it. Everything else is left
        // to the device's own decoder settings (RESET_CONFIG "false" merges rather than replaces),
        // so this doesn't quietly disable a symbology a site relies on for its labels.
        // Devices whose scan engine is a 1D laser — the SE965 option on the MC3300 — cannot
        // decode QR at all, and no profile setting changes that; those fall back to the camera,
        // or to typing.
        val barcodePlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "BARCODE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("decoder_qrcode", "true")
                putString("aim_type", AIM_TYPE_TRIGGER)
            })
        }
        val pluginList = ArrayList<Bundle>().apply { add(intentPlugin); add(barcodePlugin) }

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


    fun createReceiver(onScan: (String) -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SCAN_ACTION) return
            val value = intent.getStringExtra(SCAN_EXTRA)?.trim() ?: return
            if (value.isNotEmpty()) onScan(value)
        }
    }

    fun intentFilter() = IntentFilter(SCAN_ACTION)
}
