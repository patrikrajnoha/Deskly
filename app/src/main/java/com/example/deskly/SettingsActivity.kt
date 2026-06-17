package com.example.deskly

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
    private fun saveIpPort(ip: String, port: Int) {
        val saved = DesklyPrefs.getSavedServer(this)
        val keepRawId = saved?.rawId != null && saved.ip == ip && saved.port == port
        DesklyPrefs.saveManualServer(this, ip, port, keepRawIdForMigration = keepRawId)
        getSharedPreferences(prefsName, MODE_PRIVATE)
            .edit()
            .putString(DesklyPrefs.KEY_DEVICE_NAME, "Manual PC")
            .apply()
    }

    private fun getTokenRaw(): String? {
        return DesklyPrefs.getToken(this)
    }

    private fun setTokenForCurrentDevice(token: String?) {
        DesklyPrefs.setTokenForCurrentDevice(this, token)
        DesklyClient.setToken(token)
    }

    // Default timer pref
    private val keyTimerAction = "timer_action" // "sleep" | "shutdown"

    // Views
    private lateinit var txtConnStatus: TextView
    private lateinit var txtAuthStatus: TextView
    private lateinit var txtAutoConnectValue: TextView
    private lateinit var txtLowPowerValue: TextView
    private lateinit var txtPerformanceDiagnosticsValue: TextView
    private lateinit var txtSavedPcValue: TextView
    private lateinit var txtSavedPcSubtitle: TextView
    private lateinit var txtPairingValue: TextView
    private lateinit var switchAutoConnect: Switch
    private lateinit var switchLowPower: Switch
    private lateinit var switchPerformanceDiagnostics: Switch

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
    private lateinit var spinnerEyeMode: Spinner
    private lateinit var seekEyeIntensity: SeekBar
    private lateinit var txtEyeIntensity: TextView
    private lateinit var seekScreenDim: SeekBar
    private lateinit var txtScreenDim: TextView
    private lateinit var switchVolumeFade: Switch
    private lateinit var switchRestoreBrightness: Switch
    private lateinit var btnEyeIntensityInfo: ImageButton
    private lateinit var btnScreenDimInfo: ImageButton

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
    private lateinit var manualConnectionHeader: LinearLayout
    private lateinit var manualConnectionContent: LinearLayout
    private lateinit var manualConnectionArrow: TextView
    private lateinit var btnProtocolInfo: Button
    private lateinit var btnUnsupportedInfo: Button
    private lateinit var switchClipboardSync: Switch
    private lateinit var txtClipboardSyncValue: TextView
    private lateinit var switchPowerActions: Switch
    private lateinit var txtPowerActionsValue: TextView
    private lateinit var spinnerVolumeButtonMode: Spinner
    private lateinit var txtVolumeButtonModeValue: TextView
    private lateinit var switchNotifications: Switch
    private lateinit var txtNotificationsValue: TextView

    private var busy = false
    private var selectedTimerAction: String = "sleep"

    // ✅ otvorené/zatvorené (keď chceš default open, daj true)
    private var wolExpanded: Boolean = false
    private var manualExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // AKTUALIZOVANÉ: Použitie addListener namiesto setListener
        DesklyClient.addListener(this)

        // Bind
        txtConnStatus = findViewById(R.id.txtConnStatus)
        txtAuthStatus = findViewById(R.id.txtAuthStatus)
        txtAutoConnectValue = findViewById(R.id.txtAutoConnectValue)
        txtLowPowerValue = findViewById(R.id.txtLowPowerValue)
        txtPerformanceDiagnosticsValue = findViewById(R.id.txtPerformanceDiagnosticsValue)
        txtSavedPcValue = findViewById(R.id.txtSavedPcValue)
        txtSavedPcSubtitle = findViewById(R.id.txtSavedPcSubtitle)
        txtPairingValue = findViewById(R.id.txtPairingValue)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)
        switchLowPower = findViewById(R.id.switchLowPower)
        switchPerformanceDiagnostics = findViewById(R.id.switchPerformanceDiagnostics)

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
        spinnerEyeMode = findViewById(R.id.spinnerEyeMode)
        seekEyeIntensity = findViewById(R.id.seekEyeIntensity)
        txtEyeIntensity = findViewById(R.id.txtEyeIntensity)
        seekScreenDim = findViewById(R.id.seekScreenDim)
        txtScreenDim = findViewById(R.id.txtScreenDim)
        switchVolumeFade = findViewById(R.id.switchVolumeFade)
        switchRestoreBrightness = findViewById(R.id.switchRestoreBrightness)
        btnEyeIntensityInfo = findViewById(R.id.btnEyeIntensityInfo)
        btnScreenDimInfo = findViewById(R.id.btnScreenDimInfo)

        edtMac = findViewById(R.id.edtMac)
        edtBroadcast = findViewById(R.id.edtBroadcast)
        edtWolPort = findViewById(R.id.edtWolPort)
        btnSendWol = findViewById(R.id.btnSendWol)
        txtInfo = findViewById(R.id.txtInfo)

        // ✅ Bind WOL dropdown (from updated XML)
        wolHeader = findViewById(R.id.wolHeader)
        wolContent = findViewById(R.id.wolContent)
        wolArrow = findViewById(R.id.wolArrow)
        manualConnectionHeader = findViewById(R.id.manualConnectionHeader)
        manualConnectionContent = findViewById(R.id.manualConnectionContent)
        manualConnectionArrow = findViewById(R.id.manualConnectionArrow)
        btnProtocolInfo = findViewById(R.id.btnProtocolInfo)
        btnUnsupportedInfo = findViewById(R.id.btnUnsupportedInfo)
        switchClipboardSync = findViewById(R.id.switchClipboardSync)
        txtClipboardSyncValue = findViewById(R.id.txtClipboardSyncValue)
        switchPowerActions = findViewById(R.id.switchPowerActions)
        txtPowerActionsValue = findViewById(R.id.txtPowerActionsValue)
        spinnerVolumeButtonMode = findViewById(R.id.spinnerVolumeButtonMode)
        txtVolumeButtonModeValue = findViewById(R.id.txtVolumeButtonModeValue)
        switchNotifications = findViewById(R.id.switchNotifications)
        txtNotificationsValue = findViewById(R.id.txtNotificationsValue)

        // Load prefs
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        edtIp.setText(prefs.getString(DesklyPrefs.KEY_IP, "") ?: "")
        edtPort.setText(prefs.getString(DesklyPrefs.KEY_PORT, "5050") ?: "5050")
        switchAutoConnect.isChecked = DesklyPrefs.getAutoConnect(this)
        txtAutoConnectValue.text = if (switchAutoConnect.isChecked) "On" else "Off"
        switchLowPower.isChecked = DesklyPrefs.getLowPowerMode(this)
        txtLowPowerValue.text = if (switchLowPower.isChecked) "On" else "Off"
        switchPerformanceDiagnostics.isChecked = DesklyPrefs.getPerformanceDiagnostics(this)
        txtPerformanceDiagnosticsValue.text = if (switchPerformanceDiagnostics.isChecked) "On" else "Off"
        switchClipboardSync.isChecked = DesklyPrefs.getClipboardSyncEnabled(this)
        txtClipboardSyncValue.text = if (switchClipboardSync.isChecked) "On" else "Off"
        switchPowerActions.isChecked = DesklyPrefs.getPowerActionsEnabled(this)
        txtPowerActionsValue.text = if (switchPowerActions.isChecked) "On" else "Off"
        switchNotifications.isChecked = DesklyPrefs.getNotificationsEnabled(this)
        txtNotificationsValue.text = if (switchNotifications.isChecked) "On" else "Off"
        setupVolumeButtonModeSpinner(DesklyPrefs.getVolumeButtonMode(this))

        selectedTimerAction = prefs.getString(keyTimerAction, "sleep") ?: "sleep"
        applyTimerActionUi()
        setupEyeProtectorPrefs()

        // WOL prefs
        edtMac.setText(prefs.getString(keyMac, "AA:BB:CC:DD:EE:FF"))
        edtBroadcast.setText(prefs.getString(keyBroadcast, "192.168.1.255"))
        edtWolPort.setText(prefs.getString(keyWolPort, "9"))
        txtInfo.text = "Requires Wake-on-LAN enabled on the PC."

        // ✅ nastav úvodný stav dropdownu (z wolExpanded)
        applyWolExpandedUi()
        applyManualExpandedUi()

        // ✅ toggle po kliknutí na hlavičku
        wolHeader.setOnClickListener {
            wolExpanded = !wolExpanded
            applyWolExpandedUi()
        }
        manualConnectionHeader.setOnClickListener {
            manualExpanded = !manualExpanded
            applyManualExpandedUi()
        }

        switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setAutoConnect(this, isChecked)
            txtAutoConnectValue.text = if (isChecked) "On" else "Off"
        }

        switchLowPower.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setLowPowerMode(this, isChecked)
            txtLowPowerValue.text = if (isChecked) "On" else "Off"
            DesklyClient.configurePerformance(
                PerformancePolicy(lowPowerEnabled = isChecked, foreground = true),
                DesklyPrefs.getPerformanceDiagnostics(this)
            )
        }

        switchPerformanceDiagnostics.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setPerformanceDiagnostics(this, isChecked)
            txtPerformanceDiagnosticsValue.text = if (isChecked) "On" else "Off"
            DesklyClient.configurePerformance(
                PerformancePolicy(lowPowerEnabled = DesklyPrefs.getLowPowerMode(this), foreground = true),
                isChecked
            )
        }

        switchRestoreBrightness.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setRestoreBrightnessOnExit(this, isChecked)
        }
        switchClipboardSync.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setClipboardSyncEnabled(this, isChecked)
            txtClipboardSyncValue.text = if (isChecked) "On" else "Off"
        }
        switchPowerActions.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setPowerActionsEnabled(this, isChecked)
            txtPowerActionsValue.text = if (isChecked) "On" else "Off"
        }
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setNotificationsEnabled(this, isChecked)
            txtNotificationsValue.text = if (isChecked) "On" else "Off"
        }

        btnEyeIntensityInfo.setOnClickListener {
            showInfo("Intensity", "Controls the strength of the color filter.")
        }
        btnScreenDimInfo.setOnClickListener {
            showInfo("Screen Dim", "Darkens the screen without changing color temperature.")
        }
        btnProtocolInfo.setOnClickListener {
            showInfo("Protocol Info", "Deskly uses local TCP JSON on port 5050 and UDP discovery on port 5051.")
        }
        btnUnsupportedInfo.setOnClickListener {
            showInfo("Unsupported Features", "Some controls depend on Windows, display hardware, permissions, or power plan support.")
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
                    if (!ok) toast("Command Failed")
                    updateUiState()

                    // auto-auth if token exists
                    val token = getTokenRaw()
                    if (!token.isNullOrBlank() && DesklyClient.state.connected) {
                        DesklyClient.setToken(token)
                        DesklyClient.auth(token) { okAuth, msg ->
                            runOnUiThread {
                                if (!okAuth) toast("Auth Failed")
                                else afterAuthInit(token)
                                renderState()
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
                runOnUiThread { toast(if (ok) "Done" else "Command Failed: $msg") }
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
                            toast("Command Failed")
                            updateUiState()
                            return@runOnUiThread
                        }

                        // save token per deviceKey
                        setTokenForCurrentDevice(token)

                        // auth
                        DesklyClient.auth(token) { okAuth, authMsg ->
                            runOnUiThread {
                                if (!okAuth) toast("Auth Failed: $authMsg")
                                else afterAuthInit(token)
                                renderState()
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
                            toast("Command Failed")
                            updateUiState()
                            return@runOnUiThread
                        }
                        doPair()
                    }
                }
            }
        }

        btnClearToken.setOnClickListener {
            confirmForgetPairing()
        }

        // --- Default timer selection ---
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

        switchVolumeFade.setOnCheckedChangeListener { _, isChecked ->
            DesklyPrefs.setFadeOutShutdown(this, isChecked)
        }

        // --- WOL send ---
        btnSendWol.setOnClickListener {
            val macStr = edtMac.text.toString().trim()
            val bc = edtBroadcast.text.toString().trim()
            val port = edtWolPort.text.toString().trim().toIntOrNull() ?: 9

            if (bc.isBlank()) {
                toast("Broadcast IP required")
                return@setOnClickListener
            }
            if (port !in 1..65535) {
                toast("Invalid port")
                return@setOnClickListener
            }
            if (normalizeMac(macStr) == null) {
                toast("Invalid MAC")
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
                    toast(if (ok) "Done" else "Command Failed")
                }
            }
        }

        // Initial status
        renderState()
        updateUiState()
    }

    private fun forgetPairing() {
        setTokenForCurrentDevice(null)
        DesklyClient.close("token cleared")
        toast("Done")
        renderState()
        updateUiState()
    }

    private fun confirmForgetPairing() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Forget Pairing?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Forget") { _, _ -> forgetPairing() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.danger))
        }
        dialog.show()
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
                if (msg.isNotBlank()) toast("Command Failed")
            }
        }
    }

    private fun renderState() {
        val s = DesklyClient.state
        val saved = DesklyPrefs.getSavedServer(this)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val authRejected = s.lastError?.contains("Unauthorized", ignoreCase = true) == true
        val viewState = ConnectionStatusModel.build(
            state = s,
            savedPcName = prefs.getString(DesklyPrefs.KEY_DEVICE_NAME, null),
            savedIp = saved?.ip,
            savedPort = saved?.port,
            hasToken = DesklyPrefs.hasToken(this),
            authRejected = authRejected
        )
        txtConnStatus.text = viewState.status
        txtAuthStatus.text = viewState.auth
        txtConnStatus.setBackgroundResource(
            when {
                s.authorized -> R.drawable.bg_status_connected
                s.connecting -> R.drawable.bg_status_warning
                authRejected -> R.drawable.bg_status_danger
                else -> R.drawable.bg_status_offline
            }
        )

        txtSavedPcValue.text = viewState.pcName
        txtSavedPcSubtitle.text = "${viewState.address} - ${viewState.connectionType.name}"
        txtPairingValue.text = when {
            authRejected -> "Auth failed"
            DesklyPrefs.hasToken(this) -> "Paired"
            else -> "Required"
        }
        txtAutoConnectValue.text = if (DesklyPrefs.getAutoConnect(this)) "On" else "Off"
    }

    private fun connectionStatusText(s: DesklyClient.State): String {
        return when {
            s.connecting -> "Searching"
            s.connected -> "Connected"
            else -> "Offline"
        }
    }

    private fun authStatusText(s: DesklyClient.State): String {
        return when {
            s.authorized -> "Authorized"
            s.authenticating -> "Searching"
            s.lastError?.contains("Unauthorized", ignoreCase = true) == true -> "Auth failed"
            s.connected && getTokenRaw().isNullOrBlank() -> "Pair Required"
            else -> "Pair Required"
        }
    }

    private fun updateUiState() {
        val s = DesklyClient.state
        val hasToken = !getTokenRaw().isNullOrBlank()

        btnConnect.isEnabled = !busy && !s.connecting && !s.authenticating
        btnDisconnect.isEnabled = (s.connected || s.connecting) && !busy
        btnPing.isEnabled = s.authorized && !busy

        btnPair.isEnabled = !busy
        btnClearToken.isEnabled = hasToken && !busy
    }

    private fun applyTimerActionUi() {
        btnTimerSleep.alpha = if (selectedTimerAction == "sleep") 1.0f else 0.55f
        btnTimerShutdown.alpha = if (selectedTimerAction == "shutdown") 1.0f else 0.55f
    }

    private fun setupEyeProtectorPrefs() {
        val modeAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            DesklyPrefs.eyeModes.map { it.displayText }
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(getColor(R.color.text_primary))
                v.textSize = 14f
                v.setSingleLine(false)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(getColor(R.color.text_primary))
                v.setBackgroundColor(getColor(R.color.card_high))
                v.textSize = 14f
                v.setPadding(24, 18, 24, 18)
                v.setSingleLine(false)
                return v
            }
        }
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEyeMode.adapter = modeAdapter

        val currentMode = DesklyPrefs.getEyeMode(this)
        val selectedIndex = DesklyPrefs.eyeModes.indexOfFirst { it.id == currentMode.id }.coerceAtLeast(0)
        spinnerEyeMode.setSelection(selectedIndex)
        spinnerEyeMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                DesklyPrefs.setEyeMode(this@SettingsActivity, DesklyPrefs.eyeModes[position].id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val intensity = DesklyPrefs.getEyeIntensity(this)
        seekEyeIntensity.progress = intensity
        txtEyeIntensity.text = "$intensity%"
        seekEyeIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress.coerceIn(0, 100)
                txtEyeIntensity.text = "$v%"
                if (fromUser) DesklyPrefs.setEyeIntensity(this@SettingsActivity, v)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                DesklyPrefs.setEyeIntensity(this@SettingsActivity, seekEyeIntensity.progress.coerceIn(0, 100))
            }
        })

        val dim = DesklyPrefs.getScreenDim(this)
        seekScreenDim.progress = dim
        txtScreenDim.text = "$dim%"
        seekScreenDim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress.coerceIn(0, 100)
                txtScreenDim.text = "$v%"
                if (fromUser) DesklyPrefs.setScreenDim(this@SettingsActivity, v)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                DesklyPrefs.setScreenDim(this@SettingsActivity, seekScreenDim.progress.coerceIn(0, 100))
            }
        })

        switchVolumeFade.isChecked = DesklyPrefs.getFadeOutShutdown(this)
        switchRestoreBrightness.isChecked = DesklyPrefs.getRestoreBrightnessOnExit(this)
    }

    private fun setupVolumeButtonModeSpinner(initialMode: String) {
        val labels = arrayOf("Phone Volume", "PC Volume")
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(getColor(R.color.text_primary))
                v.textSize = 14f
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(getColor(R.color.text_primary))
                v.setBackgroundColor(getColor(R.color.card_high))
                v.textSize = 14f
                v.setPadding(24, 18, 24, 18)
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerVolumeButtonMode.adapter = adapter

        val selectedIndex = if (DesklyPrefs.normalizeVolumeButtonMode(initialMode) == "pc") 1 else 0
        spinnerVolumeButtonMode.setSelection(selectedIndex)
        txtVolumeButtonModeValue.text = labels[selectedIndex]
        spinnerVolumeButtonMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val safeIndex = position.coerceIn(0, labels.lastIndex)
                val mode = if (safeIndex == 1) "pc" else "phone"
                DesklyPrefs.setVolumeButtonMode(this@SettingsActivity, mode)
                txtVolumeButtonModeValue.text = labels[safeIndex]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
        wolArrow.text = if (wolExpanded) "^" else "v"
    }

    private fun applyManualExpandedUi() {
        manualConnectionContent.visibility = if (manualExpanded) View.VISIBLE else View.GONE
        manualConnectionArrow.text = if (manualExpanded) "^" else "v"
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
