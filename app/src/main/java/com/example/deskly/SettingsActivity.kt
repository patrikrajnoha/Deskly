package com.example.deskly

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity(), DesklyClient.Listener {

    private val prefsName = "deskly_prefs"

    // WOL
    private val keyMac = "wol_mac"
    private val keyBroadcast = "wol_broadcast"
    private val keyWolPort = "wol_port"

    // Connection prefs (rovnaké ako v Main)
    private val keyIp = "server_ip"
    private val keyPort = "server_port"
    private val keyDevice = "server_device_key"
    private val legacyKeyToken = "auth_token"

    private fun tokenKey(deviceKey: String) = "auth_token__${deviceKey}"

    private fun getDeviceKey(): String {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val dk = prefs.getString(keyDevice, null)
        if (!dk.isNullOrBlank()) return dk
        val ip = prefs.getString(keyIp, null)?.trim().orEmpty()
        val port = prefs.getString(keyPort, "5050")?.trim().orEmpty()
        return "manual_${ip}:${port}"
    }

    private fun saveIpPort(ip: String, port: Int) {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putString(keyIp, ip)
            .putString(keyPort, port.toString())
            .putString(keyDevice, "manual_${ip}:${port}")
            .apply()
    }

    private fun getTokenRaw(): String? {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val dk = getDeviceKey()
        val perDevice = prefs.getString(tokenKey(dk), null)
        if (!perDevice.isNullOrBlank()) return perDevice

        val legacy = prefs.getString(legacyKeyToken, null)
        if (!legacy.isNullOrBlank()) {
            prefs.edit().putString(tokenKey(dk), legacy).remove(legacyKeyToken).apply()
            return legacy
        }
        return null
    }

    private fun setTokenForCurrentDevice(token: String?) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val dk = getDeviceKey()
        if (token.isNullOrBlank()) prefs.edit().remove(tokenKey(dk)).apply()
        else prefs.edit().putString(tokenKey(dk), token).apply()
        DesklyClient.setToken(token)
    }

    // Timer action pref
    private val keyTimerAction = "timer_action" // "sleep" | "shutdown"

    // Views
    private lateinit var txtConnStatus: TextView
    private lateinit var txtAuthStatus: TextView

    private lateinit var edtIp: EditText
    private lateinit var edtPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnPing: Button

    private lateinit var edtPin: EditText
    private lateinit var btnPair: Button
    private lateinit var btnClearToken: Button

    private lateinit var btnTimerSleep: Button
    private lateinit var btnTimerShutdown: Button

    // WOL views (existing ids)
    private lateinit var edtMac: EditText
    private lateinit var edtBroadcast: EditText
    private lateinit var edtWolPort: EditText
    private lateinit var btnSendWol: Button
    private lateinit var txtInfo: TextView

    // ✅ WOL dropdown header/content/arrow (new ids from XML)
    private lateinit var wolHeader: LinearLayout
    private lateinit var wolContent: LinearLayout
    private lateinit var wolArrow: TextView

    private var busy = false
    private var selectedTimerAction: String = "sleep"

    // ✅ otvorené/zatvorené (keď chceš default open, daj true)
    private var wolExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // AKTUALIZOVANÉ: Použitie addListener namiesto setListener
        DesklyClient.addListener(this)

        // Bind
        txtConnStatus = findViewById(R.id.txtConnStatus)
        txtAuthStatus = findViewById(R.id.txtAuthStatus)

        edtIp = findViewById(R.id.edtIp)
        edtPort = findViewById(R.id.edtPort)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnPing = findViewById(R.id.btnPing)

        edtPin = findViewById(R.id.edtPin)
        btnPair = findViewById(R.id.btnPair)
        btnClearToken = findViewById(R.id.btnClearToken)

        btnTimerSleep = findViewById(R.id.btnTimerSleep)
        btnTimerShutdown = findViewById(R.id.btnTimerShutdown)

        edtMac = findViewById(R.id.edtMac)
        edtBroadcast = findViewById(R.id.edtBroadcast)
        edtWolPort = findViewById(R.id.edtWolPort)
        btnSendWol = findViewById(R.id.btnSendWol)
        txtInfo = findViewById(R.id.txtInfo)

        // ✅ Bind WOL dropdown (from updated XML)
        wolHeader = findViewById(R.id.wolHeader)
        wolContent = findViewById(R.id.wolContent)
        wolArrow = findViewById(R.id.wolArrow)

        // Load prefs
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        edtIp.setText(prefs.getString(keyIp, "") ?: "")
        edtPort.setText(prefs.getString(keyPort, "5050") ?: "5050")

        selectedTimerAction = prefs.getString(keyTimerAction, "sleep") ?: "sleep"
        applyTimerActionUi()

        // WOL prefs
        edtMac.setText(prefs.getString(keyMac, "AA:BB:CC:DD:EE:FF"))
        edtBroadcast.setText(prefs.getString(keyBroadcast, "192.168.1.255"))
        edtWolPort.setText(prefs.getString(keyWolPort, "9"))
        txtInfo.text = "Wake-on-LAN pošle magic packet na broadcast. PC musí mať povolené WOL v BIOS + adaptéri."

        // ✅ nastav úvodný stav dropdownu (z wolExpanded)
        applyWolExpandedUi()

        // ✅ toggle po kliknutí na hlavičku
        wolHeader.setOnClickListener {
            wolExpanded = !wolExpanded
            applyWolExpandedUi()
        }

        // --- Connection actions ---
        btnConnect.setOnClickListener {
            if (busy) return@setOnClickListener

            val ip = edtIp.text.toString().trim()
            val port = edtPort.text.toString().trim().toIntOrNull()
            if (ip.isBlank() || port == null || port !in 1..65535) {
                toast("Invalid IP/Port")
                return@setOnClickListener
            }

            busy = true
            saveIpPort(ip, port)
            updateUiState()

            // connect
            DesklyClient.connect(ip, port) { ok, err ->
                runOnUiThread {
                    busy = false
                    if (!ok) toast(err ?: "Connect failed")
                    updateUiState()

                    // auto-auth if token exists
                    val token = getTokenRaw()
                    if (!token.isNullOrBlank() && DesklyClient.state.connected) {
                        DesklyClient.setToken(token)
                        DesklyClient.auth(token) { okAuth, msg ->
                            runOnUiThread {
                                if (!okAuth) toast(msg)
                                else afterAuthInit(token)
                                updateUiState()
                            }
                        }
                    }
                }
            }
        }

        btnDisconnect.setOnClickListener {
            DesklyClient.close("disconnect")
        }

        btnPing.setOnClickListener {
            DesklyClient.pingSecure { ok, msg ->
                runOnUiThread { toast(if (ok) "pong ✅" else "ping ❌: $msg") }
            }
        }

        // --- Pairing actions ---
        btnPair.setOnClickListener {
            if (busy) return@setOnClickListener

            val pin = edtPin.text.toString().trim()
            val ip = edtIp.text.toString().trim()
            val port = edtPort.text.toString().trim().toIntOrNull()

            if (pin.length != 6) { toast("PIN must be 6 digits"); return@setOnClickListener }
            if (ip.isBlank() || port == null || port !in 1..65535) { toast("Invalid IP/Port"); return@setOnClickListener }

            busy = true
            saveIpPort(ip, port)
            updateUiState()

            val doPair = {
                DesklyClient.pair(pin) { okPair, token, msg ->
                    runOnUiThread {
                        busy = false
                        if (!okPair || token.isNullOrBlank()) {
                            toast(if (msg.isNotBlank()) msg else "Pairing failed")
                            updateUiState()
                            return@runOnUiThread
                        }

                        // save token per deviceKey
                        setTokenForCurrentDevice(token)

                        // auth
                        DesklyClient.auth(token) { okAuth, authMsg ->
                            runOnUiThread {
                                if (!okAuth) toast("AUTH ❌: $authMsg")
                                else afterAuthInit(token)
                                updateUiState()
                            }
                        }
                    }
                }
            }

            if (DesklyClient.state.connected) doPair()
            else {
                DesklyClient.connect(ip, port) { ok, err ->
                    runOnUiThread {
                        if (!ok) {
                            busy = false
                            toast(err ?: "Connect failed")
                            updateUiState()
                            return@runOnUiThread
                        }
                        doPair()
                    }
                }
            }
        }

        btnClearToken.setOnClickListener {
            setTokenForCurrentDevice(null)
            toast("Token cleared")
            updateUiState()
        }

        // --- Timer action selection ---
        btnTimerSleep.setOnClickListener {
            selectedTimerAction = "sleep"
            prefs.edit().putString(keyTimerAction, selectedTimerAction).apply()
            applyTimerActionUi()
        }

        btnTimerShutdown.setOnClickListener {
            selectedTimerAction = "shutdown"
            prefs.edit().putString(keyTimerAction, selectedTimerAction).apply()
            applyTimerActionUi()
        }

        // --- WOL send ---
        btnSendWol.setOnClickListener {
            val macStr = edtMac.text.toString().trim()
            val bc = edtBroadcast.text.toString().trim()
            val port = edtWolPort.text.toString().trim().toIntOrNull() ?: 9

            if (bc.isBlank()) {
                toast("Zadaj broadcast IP (napr. 192.168.1.255).")
                return@setOnClickListener
            }
            if (port !in 1..65535) {
                toast("Port musí byť 1–65535 (default 9).")
                return@setOnClickListener
            }
            if (normalizeMac(macStr) == null) {
                toast("MAC musí byť AA:BB:CC:DD:EE:FF (alebo AABBCCDDEEFF).")
                return@setOnClickListener
            }

            prefs.edit()
                .putString(keyMac, macStr)
                .putString(keyBroadcast, bc)
                .putString(keyWolPort, port.toString())
                .apply()

            lifecycleScope.launch(Dispatchers.IO) {
                val ok = sendWol(macStr, bc, port)
                withContext(Dispatchers.Main) {
                    toast(if (ok) "WOL sent ✅" else "WOL failed ❌")
                }
            }
        }

        // Initial status
        renderState()
        updateUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // AKTUALIZOVANÉ: Odoberáme listener pri zničení aktivity
        DesklyClient.removeListener(this)
    }

    // -------------------------
    // DesklyClient.Listener
    // -------------------------
    override fun onState(state: DesklyClient.State) {
        renderState()

        // keď padne auth/conn, busy reset
        if (!state.connected || !state.authorized) {
            busy = false
        }
        updateUiState()
    }

    override fun onLog(line: String) {
        // settings log neukazujeme
    }

    override fun onJson(json: JSONObject) {
        val type = json.optString("type")
        if (type.endsWith("_response")) {
            val ok = json.optBoolean("ok", true)
            if (!ok) {
                val msg = json.optString("message", "")
                if (msg.isNotBlank()) toast(msg)
            }
        }
    }

    private fun renderState() {
        val s = DesklyClient.state
        txtConnStatus.text = if (s.connected) "Connected" else "Disconnected"
        txtAuthStatus.text = if (s.authorized) "Authorized" else "Unauthorized"
    }

    private fun updateUiState() {
        val s = DesklyClient.state
        val hasToken = !getTokenRaw().isNullOrBlank()

        btnConnect.isEnabled = !busy
        btnDisconnect.isEnabled = s.connected && !busy
        btnPing.isEnabled = s.authorized && !busy

        btnPair.isEnabled = !busy
        btnClearToken.isEnabled = hasToken && !busy
    }

    private fun applyTimerActionUi() {
        btnTimerSleep.alpha = if (selectedTimerAction == "sleep") 1.0f else 0.55f
        btnTimerShutdown.alpha = if (selectedTimerAction == "shutdown") 1.0f else 0.55f
    }

    private fun afterAuthInit(token: String) {
        DesklyClient.sendSecure("volume_get", token)
        DesklyClient.sendSecure("display_list", token)
        DesklyClient.sendSecure("brightness_get", token, JSONObject().put("displayId", "all"))
        DesklyClient.sendSecure("night_get", token)
        DesklyClient.sendSecure("quiet_get", token)
        DesklyClient.sendSecure("sleep_timer_status", token)
    }

    private fun applyWolExpandedUi() {
        wolContent.visibility = if (wolExpanded) View.VISIBLE else View.GONE
        wolArrow.text = if (wolExpanded) "▴" else "▾"
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun normalizeMac(mac: String): ByteArray? {
        val cleaned = mac.trim()
            .replace("-", ":")
            .replace(" ", "")
            .uppercase()

        val parts = if (cleaned.contains(":")) cleaned.split(":") else cleaned.chunked(2)
        if (parts.size != 6) return null

        return try {
            parts.map {
                if (it.length != 2) return null
                it.toInt(16).toByte()
            }.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun sendWol(macStr: String, broadcastIp: String, port: Int): Boolean {
        return try {
            val mac = normalizeMac(macStr) ?: return false

            val data = ByteArray(6 + 16 * mac.size)
            for (i in 0 until 6) data[i] = 0xFF.toByte()
            var idx = 6
            repeat(16) {
                for (b in mac) data[idx++] = b
            }

            val address = InetAddress.getByName(broadcastIp)
            DatagramSocket().use { sock ->
                sock.broadcast = true
                val packet = DatagramPacket(data, data.size, address, port)
                sock.send(packet)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
