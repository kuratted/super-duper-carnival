package com.lootdealsks.airpodstv

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    // ── UI refs ────────────────────────────────────────────────────────────
    private lateinit var tvDeviceName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var switchAutoConnect: SwitchCompat

    // ── BT ─────────────────────────────────────────────────────────────────
    private val connector = BluetoothConnector()
    private var currentDevice: BluetoothDevice? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── A2DP state broadcast receiver ──────────────────────────────────────
    private val a2dpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return

            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

            // Only react to our device
            if (device?.address == currentDevice?.address) {
                updateStatus(state)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDeviceName      = findViewById(R.id.tvDeviceName)
        tvStatus          = findViewById(R.id.tvStatus)
        btnConnect        = findViewById(R.id.btnConnect)
        btnDisconnect     = findViewById(R.id.btnDisconnect)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)

        requestBtPermissionIfNeeded()
        setupAutoConnectToggle()
        setupButtons()
        registerReceiver(a2dpReceiver, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
    }

    override fun onResume() {
        super.onResume()
        loadDevice()
    }

    // ── Permission (Android 12+) ───────────────────────────────────────────
    private fun requestBtPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    101
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) loadDevice()
    }

    // ── Load device info ──────────────────────────────────────────────────
    private fun loadDevice() {
        if (!connector.isBluetoothEnabled()) {
            tvDeviceName.text = "Bluetooth is off"
            tvStatus.text = "Turn on Bluetooth in TV settings"
            btnConnect.isEnabled = false
            return
        }

        currentDevice = connector.findAirPods()

        if (currentDevice != null) {
            tvDeviceName.text = "🎧  ${currentDevice!!.name}"
            btnConnect.isEnabled = true

            // Check current state
            connector.getConnectionState(this, currentDevice!!) { state ->
                mainHandler.post { updateStatus(state) }
            }
        } else {
            tvDeviceName.text = "No AirPods paired"
            tvStatus.text = "Open TV Settings → Remotes & Accessories → Pair"
            btnConnect.isEnabled = false
        }
    }

    // ── Buttons ────────────────────────────────────────────────────────────
    private fun setupButtons() {
        btnConnect.setOnClickListener {
            val device = currentDevice ?: return@setOnClickListener
            tvStatus.text = "⏳ Connecting…"
            btnConnect.isEnabled = false
            connector.connect(this, device) { _, message ->
                mainHandler.post {
                    tvStatus.text = message
                    btnConnect.isEnabled = false // status receiver will re-enable on disconnect
                }
            }
        }

        btnDisconnect.setOnClickListener {
            val device = currentDevice ?: return@setOnClickListener
            tvStatus.text = "⏳ Disconnecting…"
            connector.disconnect(this, device) { _, message ->
                mainHandler.post { tvStatus.text = message }
            }
        }
    }

    // ── Auto-connect toggle ────────────────────────────────────────────────
    private fun setupAutoConnectToggle() {
        val prefs = getSharedPreferences("airpods_prefs", Context.MODE_PRIVATE)
        switchAutoConnect.isChecked = prefs.getBoolean("auto_connect", true)
        switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_connect", isChecked).apply()
        }
    }

    // ── Status updates ────────────────────────────────────────────────────
    private fun updateStatus(state: Int) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                tvStatus.text = "✅  Connected"
                btnConnect.isEnabled = false
            }
            BluetoothProfile.STATE_CONNECTING -> {
                tvStatus.text = "⏳  Connecting…"
                btnConnect.isEnabled = false
            }
            BluetoothProfile.STATE_DISCONNECTING -> {
                tvStatus.text = "⏳  Disconnecting…"
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                tvStatus.text = "⚪  Disconnected"
                btnConnect.isEnabled = true
            }
        }
    }

    // ── Cleanup ────────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(a2dpReceiver)
        connector.close()
    }
}
