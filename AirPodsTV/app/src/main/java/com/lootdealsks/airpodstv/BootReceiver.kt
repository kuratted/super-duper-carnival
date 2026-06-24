package com.lootdealsks.airpodstv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Fires after TV boot completes. If auto-connect is enabled (default: true),
 * waits 12 seconds for the Bluetooth stack to be ready, then connects AirPods.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("airpods_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_connect", true)) return

        // Delay 12s — Android TV Bluetooth stack needs time to initialise after boot
        Handler(Looper.getMainLooper()).postDelayed({
            val connector = BluetoothConnector()

            if (!connector.isBluetoothEnabled()) return@postDelayed

            val device = connector.findAirPods() ?: return@postDelayed
            connector.connect(context, device) { _, _ ->
                // Silent — no UI on boot
            }
        }, 12_000)
    }
}
