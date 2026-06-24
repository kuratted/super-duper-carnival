package com.lootdealsks.airpodstv

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context

/**
 * Handles all Bluetooth A2DP connection logic for AirPods / Beats devices.
 * Uses reflection to call the hidden BluetoothA2dp.connect() API.
 */
class BluetoothConnector {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var a2dpProxy: BluetoothA2dp? = null

    companion object {
        // Device name keywords — extend this list if needed (e.g. "Studio Buds")
        private val AIRPOD_KEYWORDS = listOf("AirPods", "AirPod", "Beats", "EarPods")
    }

    /** Returns the first paired AirPods/Beats device, or null if none found. */
    fun findAirPods(): BluetoothDevice? {
        return adapter?.bondedDevices?.find { device ->
            AIRPOD_KEYWORDS.any { keyword ->
                device.name?.contains(keyword, ignoreCase = true) == true
            }
        }
    }

    /** Returns ALL paired AirPods/Beats devices (in case the user has multiple). */
    fun findAllAirPods(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.filter { device ->
            AIRPOD_KEYWORDS.any { keyword ->
                device.name?.contains(keyword, ignoreCase = true) == true
            }
        } ?: emptyList()
    }

    /** Returns true if Bluetooth is on and available. */
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Connects to the given device via A2DP profile.
     * callback receives (success: Boolean, message: String).
     */
    fun connect(context: Context, device: BluetoothDevice, callback: (Boolean, String) -> Unit) {
        if (!isBluetoothEnabled()) {
            callback(false, "Bluetooth is off")
            return
        }

        adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpProxy = proxy as BluetoothA2dp
                try {
                    // BluetoothA2dp.connect() is a hidden API — access via reflection
                    val method = BluetoothA2dp::class.java.getDeclaredMethod(
                        "connect", BluetoothDevice::class.java
                    )
                    method.isAccessible = true
                    method.invoke(a2dpProxy, device)
                    callback(true, "⏳ Connecting to ${device.name}…")
                } catch (e: Exception) {
                    callback(false, "❌ Failed: ${e.message}")
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    /**
     * Disconnects the given device from A2DP.
     * callback receives (success: Boolean, message: String).
     */
    fun disconnect(context: Context, device: BluetoothDevice, callback: (Boolean, String) -> Unit) {
        val doDisconnect = { proxy: BluetoothA2dp ->
            try {
                val method = BluetoothA2dp::class.java.getDeclaredMethod(
                    "disconnect", BluetoothDevice::class.java
                )
                method.isAccessible = true
                method.invoke(proxy, device)
                callback(true, "❌ Disconnected from ${device.name}")
            } catch (e: Exception) {
                callback(false, "Error: ${e.message}")
            }
        }

        if (a2dpProxy != null) {
            doDisconnect(a2dpProxy!!)
        } else {
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    a2dpProxy = proxy as BluetoothA2dp
                    doDisconnect(a2dpProxy!!)
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        }
    }

    /**
     * Fetches current A2DP connection state for the given device.
     * Returns one of: BluetoothProfile.STATE_CONNECTED / CONNECTING / DISCONNECTING / DISCONNECTED
     */
    fun getConnectionState(context: Context, device: BluetoothDevice, callback: (Int) -> Unit) {
        adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpProxy = proxy as BluetoothA2dp
                callback(a2dpProxy!!.getConnectionState(device))
            }
            override fun onServiceDisconnected(profile: Int) {
                callback(BluetoothProfile.STATE_DISCONNECTED)
            }
        }, BluetoothProfile.A2DP)
    }

    /** Release the A2DP profile proxy. Call this in onDestroy(). */
    fun close() {
        a2dpProxy?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        a2dpProxy = null
    }
}
