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
import kotlinx.coroutines.*

class SplashActivity : AppCompatActivity() {

    private val prefsName = "deskly_prefs"
    private val keyIp = "server_ip"
    private val keyPort = "server_port"

    // ✅ safe deviceKey (používa MainActivity ako primárny kľúč na token)
    private val keyDevice = "server_device_key"

    // ✅ raw discovery id (fallback/migrácia tokenu)
    private val keyDeviceRaw = "server_device_raw_id"

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

        // ✅ Collapsible manual section (NEW)
        val layoutManualHeader = findViewById<LinearLayout>(R.id.layoutManualHeader)
        val layoutManualContent = findViewById<LinearLayout>(R.id.layoutManualContent)
        val txtManualArrow = findViewById<TextView>(R.id.txtManualArrow)

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)

        val savedIp = prefs.getString(keyIp, "192.168.1.11").orEmpty()
        val savedPort = prefs.getString(keyPort, "5050").orEmpty()

        edtIp.setText(savedIp)
        edtPort.setText(savedPort)

        // ✅ helper: existuje token pre deviceKey alebo fallback rawId?
        fun hasTokenForDevice(deviceKey: String, rawId: String?): Boolean {
            val t1 = prefs.getString("auth_token__${deviceKey}", null)
            if (!t1.isNullOrBlank()) return true

            // fallback: starší štýl tokenu pod rawId
            val rid = rawId?.trim().orEmpty()
            if (rid.isNotBlank()) {
                val t2 = prefs.getString("auth_token__${rid}", null)
                if (!t2.isNullOrBlank()) return true
            }
            return false
        }

        fun updateHint() {
            val ip2 = prefs.getString(keyIp, "").orEmpty()
            val port2 = prefs.getString(keyPort, "5050").orEmpty()

            val dk = prefs.getString(keyDevice, null)
                ?: "manual_${ip2}:${port2}"

            val rawId = prefs.getString(keyDeviceRaw, null)

            val hasToken = hasTokenForDevice(dk, rawId)

            txtHint.text = if (hasToken) {
                "Token uložený • môžeš sa pripojiť jedným klikom"
            } else {
                "Najprv spáruj cez PIN (v Main)"
            }
        }
        updateHint()

        // ✅ Toggle manual section (NEW)
        fun setManualExpanded(expanded: Boolean) {
            layoutManualContent.visibility = if (expanded) View.VISIBLE else View.GONE
            txtManualArrow.text = if (expanded) "⌃" else "⌄"
        }

        // default: zatvorené (núdzová sekcia)
        setManualExpanded(false)

        layoutManualHeader.setOnClickListener {
            val isOpen = layoutManualContent.visibility == View.VISIBLE
            setManualExpanded(!isOpen)
        }

        val adapter = DeviceAdapter { d ->
            val ip = d.ip.trim()
            val port = d.port

            // ✅ validácia aby to nikdy nespadlo
            if (ip.isBlank()) {
                toast("Zariadenie nemá platnú IP.")
                return@DeviceAdapter
            }
            if (port !in 1..65535) {
                toast("Zariadenie má zlý port: $port")
                return@DeviceAdapter
            }
            if (d.id.isBlank()) {
                toast("Zariadenie nemá ID (discovery).")
                return@DeviceAdapter
            }

            // ✅ safe deviceKey (pre prefs/token)
            val deviceKey = makeSafeDeviceKey(d.id, ip, port)

            // UI: vyplň edittexty
            edtIp.setText(ip)
            edtPort.setText(port.toString())

            // ✅ persist: uložíme aj raw id, aby Main vedel migrovať token
            prefs.edit()
                .putString(keyIp, ip)
                .putString(keyPort, port.toString())
                .putString(keyDevice, deviceKey)
                .putString(keyDeviceRaw, d.id)
                .apply()

            val hasToken = hasTokenForDevice(deviceKey, d.id)
            if (!hasToken) {
                toast("Token nie je uložený – v Main spáruj cez PIN.")
            }

            // (voliteľné) manuál sekciu schovaj, keď vybral zariadenie
            setManualExpanded(false)

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = adapter

        fun doScan() {
            txtScanStatus.text = "Skenujem sieť…"
            btnScan.isEnabled = false

            scope.launch {
                val list = withContext(Dispatchers.IO) { DeviceDiscovery.scan() }

                adapter.submit(list)

                txtScanStatus.text = if (list.isEmpty()) {
                    "Nenašli sa žiadne zariadenia. Skontroluj, či server beží a UDP 5051 nie je blokované firewallom."
                } else {
                    "Nájdené zariadenia: ${list.size} • klikni na zariadenie pre pripojenie"
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
                toast("Zadaj IP adresu.")
                return@setOnClickListener
            }

            if (port == null || port !in 1..65535) {
                toast("Port musí byť číslo 1–65535 (napr. 5050).")
                return@setOnClickListener
            }

            val deviceKey = "manual_${ip}:${port}"

            prefs.edit()
                .putString(keyIp, ip)
                .putString(keyPort, port.toString())
                .putString(keyDevice, deviceKey)
                .remove(keyDeviceRaw) // manuál nemá discovery id
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // auto-scan pri otvorení
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
