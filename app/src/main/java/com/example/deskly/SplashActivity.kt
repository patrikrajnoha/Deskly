package com.example.deskly

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private val prefsName = "deskly_prefs"
    private val keyIp = DesklyPrefs.KEY_IP
    private val keyPort = DesklyPrefs.KEY_PORT
    private val keyDevice = DesklyPrefs.KEY_DEVICE
    private val keyDeviceRaw = DesklyPrefs.KEY_DEVICE_RAW
    private val keyDeviceName = DesklyPrefs.KEY_DEVICE_NAME

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val edtIp = findViewById<EditText>(R.id.edtIp)
        val edtPort = findViewById<EditText>(R.id.edtPort)
        val txtHint = findViewById<TextView>(R.id.txtHint)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        val btnScan = findViewById<Button>(R.id.btnScan)
        val txtScanStatus = findViewById<TextView>(R.id.txtScanStatus)
        val rvDevices = findViewById<RecyclerView>(R.id.rvDevices)

        val layoutManualHeader = findViewById<LinearLayout>(R.id.layoutManualHeader)
        val layoutManualContent = findViewById<LinearLayout>(R.id.layoutManualContent)
        val txtManualArrow = findViewById<TextView>(R.id.txtManualArrow)

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        edtIp.setText(prefs.getString(keyIp, "192.168.1.11").orEmpty())
        edtPort.setText(prefs.getString(keyPort, "5050").orEmpty())

        fun updateHint() {
            val hasToken = DesklyClient.state.authorized || DesklyPrefs.hasToken(this)
            txtHint.text = if (hasToken) {
                "Connected before. Choose a PC."
            } else {
                "Pair Required. Choose a PC, then enter the PIN."
            }
        }

        fun setManualExpanded(expanded: Boolean) {
            layoutManualContent.visibility = if (expanded) View.VISIBLE else View.GONE
            txtManualArrow.text = if (expanded) "^" else "v"
        }

        updateHint()
        setManualExpanded(false)

        layoutManualHeader.setOnClickListener {
            setManualExpanded(layoutManualContent.visibility != View.VISIBLE)
        }

        val adapter = DeviceAdapter { d ->
            val ip = d.ip.trim()
            val port = d.port

            if (ip.isBlank()) {
                toast("Command Failed")
                return@DeviceAdapter
            }
            if (port !in 1..65535) {
                toast("Invalid port")
                return@DeviceAdapter
            }
            if (d.id.isBlank()) {
                toast("Command Failed")
                return@DeviceAdapter
            }

            val deviceKey = makeSafeDeviceKey(d.id, ip, port)
            edtIp.setText(ip)
            edtPort.setText(port.toString())

            prefs.edit()
                .putString(keyIp, ip)
                .putString(keyPort, port.toString())
                .putString(keyDevice, deviceKey)
                .putString(keyDeviceRaw, d.id)
                .putString(keyDeviceName, d.name)
                .apply()

            if (!(DesklyClient.state.authorized || DesklyPrefs.hasToken(this))) {
                toast("Pair Required")
            }

            setManualExpanded(false)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = adapter

        fun doScan() {
            txtScanStatus.text = "Searching"
            btnScan.isEnabled = false

            scope.launch {
                val list = withContext(Dispatchers.IO) { DeviceDiscovery.scan() }
                adapter.submit(list)

                txtScanStatus.text = if (list.isEmpty()) {
                    "No PC Found. Check Deskly Host and Wi-Fi."
                } else {
                    "Found ${list.size}"
                }

                btnScan.isEnabled = true
                updateHint()
            }
        }

        btnScan.setOnClickListener { doScan() }

        btnContinue.setOnClickListener {
            val ip = edtIp.text?.toString()?.trim().orEmpty()
            val portStr = edtPort.text?.toString()?.trim().orEmpty()
            val port = portStr.toIntOrNull()

            if (ip.isEmpty()) {
                toast("IP required")
                return@setOnClickListener
            }
            if (port == null || port !in 1..65535) {
                toast("Invalid port")
                return@setOnClickListener
            }

            val deviceKey = "manual_${ip}:${port}"
            prefs.edit()
                .putString(keyIp, ip)
                .putString(keyPort, port.toString())
                .putString(keyDevice, deviceKey)
                .putString(keyDeviceName, "Manual PC")
                .remove(keyDeviceRaw)
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        doScan()
    }

    private fun makeSafeDeviceKey(id: String, ip: String, port: Int): String {
        val raw = "scan_${id}_${ip}_${port}"
        val safe = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return safe.take(80)
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
