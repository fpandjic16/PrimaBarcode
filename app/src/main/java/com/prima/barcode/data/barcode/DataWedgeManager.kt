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

// DataWedge aim_type (trigger mode) values. Device/version dependent — see setContinuousScan.
private const val AIM_TYPE_TRIGGER    = "0"   // single shot: one pull, one scan
private const val AIM_TYPE_CONTINUOUS = "4"   // press-and-hold continuously scans

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
        val appBundle = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
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
                putParcelableArray("APP_LIST", arrayOf(appBundle))
                putParcelableArrayList("PLUGIN_CONFIG", ArrayList<Bundle>().apply { add(barcodePlugin) })
            })
        })
        Timber.d("DataWedge audio feedback set: muted=$muted")
    }

    /**
     * Toggles the hardware scanner's trigger mode between single-shot and continuous read.
     *
     * Continuous Read = press and hold the trigger to keep scanning barcodes without
     * releasing. `same_symbol_timeout` stops the *same* item being counted repeatedly
     * while held; `different_symbol_timeout` is kept short so different items scan quickly.
     *
     * Note: `aim_type` values are scanner/DataWedge-version dependent. "4" is the common
     * Continuous Read value; if continuous mode doesn't engage on the target device,
     * confirm the value in DataWedge → Barcode input → Reader params → Aim Type.
     *
     * [sameSymbolTimeoutMs] is the minimum interval before the SAME barcode decodes
     * again. Keep it small enough to count multiple identical items by re-scanning —
     * it is driven by the app's debounce setting. Too large makes the count appear stuck.
     */
    fun setContinuousScan(context: Context, enabled: Boolean, sameSymbolTimeoutMs: Int = 500) {
        val appBundle = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val barcodePlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "BARCODE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("aim_type", if (enabled) AIM_TYPE_CONTINUOUS else AIM_TYPE_TRIGGER)
                if (enabled) {
                    putString("same_symbol_timeout",      sameSymbolTimeoutMs.coerceIn(0, 5000).toString())
                    putString("different_symbol_timeout", "100")
                }
            })
        }
        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.SET_CONFIG", Bundle().apply {
                putString("PROFILE_NAME",    "PrimaBarcode")
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE",     "UPDATE")
                putParcelableArray("APP_LIST", arrayOf(appBundle))
                putParcelableArrayList("PLUGIN_CONFIG", ArrayList<Bundle>().apply { add(barcodePlugin) })
            })
        })
        Timber.d("DataWedge continuous scan set: enabled=$enabled")
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
