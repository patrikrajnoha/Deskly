package com.example.deskly

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity(), DesklyClient.Listener {

    // =========================
    // PREFS / TOKEN
    // =========================
    private val prefsName = "deskly_prefs"
    private val keyIp = "server_ip"
    private val keyPort = "server_port"
    private val keyDevice = "server_device_key"
    private val keyDeviceRaw = "server_device_raw_id"
    private val legacyKeyToken = "auth_token"

    private val keyTimerAction = "timer_action" // "sleep" | "shutdown"

    private fun tokenKey(deviceKey: String) = "auth_token__${deviceKey}"

    private fun getDeviceKey(): String {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val dk = prefs.getString(keyDevice, null)
        if (!dk.isNullOrBlank()) return dk

        val ip = prefs.getString(keyIp, null)?.trim().orEmpty()
        val port = prefs.getString(keyPort, "5050")?.trim().orEmpty()
        return "manual_${ip}:${port}"
    }

    private fun getTokenRaw(): String? {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val dk = getDeviceKey()

        prefs.getString(tokenKey(dk), null)?.let { t ->
            if (t.isNotBlank()) return t
        }

        val rawId = prefs.getString(keyDeviceRaw, null)?.trim().orEmpty()
        if (rawId.isNotBlank()) {
            val rawToken = prefs.getString(tokenKey(rawId), null)
            if (!rawToken.isNullOrBlank()) {
                prefs.edit()
                    .putString(tokenKey(dk), rawToken)
                    .remove(tokenKey(rawId))
                    .apply()
                return rawToken
            }
        }

        val legacy = prefs.getString(legacyKeyToken, null)
        if (!legacy.isNullOrBlank()) {
            prefs.edit()
                .putString(tokenKey(dk), legacy)
                .remove(legacyKeyToken)
                .apply()
            return legacy
        }

        return null
    }

    private fun getToken(): String? {
        val t = getTokenRaw()
        if (t.isNullOrEmpty()) {
            toast("No token. Pair first (Settings).")
            return null
        }
        return t
    }

    private fun getSavedIpPort(): Pair<String, Int>? {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val ip = prefs.getString(keyIp, "")?.trim().orEmpty()
        val port = prefs.getString(keyPort, "5050")?.trim().orEmpty().toIntOrNull()
        if (ip.isBlank() || port == null || port !in 1..65535) return null
        return ip to port
    }

    // =========================
    // UI
    // =========================
    private lateinit var txtStatus: TextView

    private lateinit var btnSettingsIcon: TextView
    private lateinit var btnAboutIcon: TextView

    private lateinit var seekPrimary: SeekBar
    private lateinit var btnNight: TextView

    private lateinit var seekInternalBrightness: SeekBar
    private lateinit var btnQuiet: TextView

    private lateinit var seekVolume: SeekBar
    private lateinit var btnMute: TextView

    private lateinit var txtTimerStatus: TextView
    private lateinit var btnTimerMinus: Button
    private lateinit var btnTimerPlus: Button
    private lateinit var btnTimerStart: Button
    private lateinit var btnTimerCancel: Button

    private lateinit var btnLock: Button
    private lateinit var btnSleep: Button
    private lateinit var btnRestart: Button
    private lateinit var btnShutdown: Button

    // =========================
    // STATE
    // =========================
    private var busy = false

    private var nightEnabled = false
    private var nightIntensity = 25
    private var primaryMode: PrimaryMode = PrimaryMode.BRIGHTNESS
    private enum class PrimaryMode { BRIGHTNESS, NIGHT_INTENSITY }

    private var lastNightTapAt = 0L
    private val doubleTapWindowMs = 280L

    // ✅ DVA RÔZNE DISPLAY ID
    private var internalDisplayId: String? = null
    private var externalDisplayId: String? = null

    private var ignoreInternalSeek = false
    private var ignorePrimarySeek = false

    private var quietEnabled: Boolean = false
    private var quietRequestInFlight: Boolean = false
    private val quietHandler = Handler(Looper.getMainLooper())
    private var quietTimeoutPosted = false

    private var ignoreVolumeSeek = false
    private var lastVol = 50
    private var lastMuted = false

    // =========================
    // SYNC cache (server → UI)
    // =========================
    // Brightness values keyed by displayId (as returned from backend)
    private val brightnessCache = mutableMapOf<String, Int>()

    // In case brightness_get arrives before display_list_response
    private fun applyCachedBrightnessToUi() {
        val extId = externalDisplayId
        val intId = internalDisplayId

        // External (Primary slider in BRIGHTNESS mode)
        if (!extId.isNullOrBlank()) {
            brightnessCache[extId]?.let { v ->
                if (primaryMode == PrimaryMode.BRIGHTNESS) {
                    ignorePrimarySeek = true
                    seekPrimary.progress = v.coerceIn(0, 100)
                    ignorePrimarySeek = false
                }
            }
        }

        // Internal
        if (!intId.isNullOrBlank()) {
            brightnessCache[intId]?.let { v ->
                ignoreInternalSeek = true
                seekInternalBrightness.progress = v.coerceIn(0, 100)
                ignoreInternalSeek = false
            }
        }
    }

    private fun applyVolumeToUi(volume: Int, muted: Boolean) {
        lastVol = volume.coerceIn(0, 100)
        lastMuted = muted

        ignoreVolumeSeek = true
        seekVolume.progress = lastVol
        ignoreVolumeSeek = false

        updateMuteIcon()
        updateUiState()
    }


    private var timerMinutes = 30
    private var timerRunning = false
    private var timerRemainingSecondsUi: Int = 0
    private var timerActionUi: String = "sleep"
    private var selectedTimerAction: String = "sleep"

    private var afterAuthDone = false
    private var connectInFlight = false
    private var authInFlight = false

    // =========================
    // Debug logging
    // =========================
    private fun logState(tag: String) {
        val s = DesklyClient.state
        Log.d(
            "Deskly",
            "[$tag] connected=${s.connected} authorized=${s.authorized} lastError=${s.lastError} " +
                    "tokenInPrefs=${!getTokenRaw().isNullOrBlank()} tokenInClient=${!DesklyClient.getToken().isNullOrBlank()} " +
                    "internalDisplayId=$internalDisplayId externalDisplayId=$externalDisplayId"
        )
    }

    // =========================
    // Auto connect + auth
    // =========================
    private fun ensureConnectedAndAuthed() {
        val s = DesklyClient.state
        val token = getTokenRaw()

        if (!token.isNullOrBlank()) DesklyClient.setToken(token)

        // 1) ak sme authorized -> init iba raz
        if (s.authorized) {
            if (!afterAuthDone && !token.isNullOrBlank()) {
                afterAuthDone = true
                afterAuthInit(token)
            }
            return
        }

        // 2) ak nie sme connected -> connect
        if (!s.connected) {
            if (connectInFlight) return

            val ipPort = getSavedIpPort()
            if (ipPort == null) {
                setStatusSimple(false)
                updateUiState()
                return
            }

            val (ip, port) = ipPort
            connectInFlight = true
            Log.d("Deskly", "[Main] auto-connect to $ip:$port")

            DesklyClient.connect(ip, port) { ok, err ->
                connectInFlight = false
                Log.d("Deskly", "[Main] connect done ok=$ok err=$err")
                if (!ok) {
                    toast(err ?: "Connect failed")
                    setStatusSimple(false)
                    updateUiState()
                    return@connect
                }
                ensureConnectedAndAuthed()
                setStatusSimple(true)
                updateUiState()
            }
            return
        }

        // 3) sme connected, ale nie authorized -> auth
        if (s.connected && !s.authorized && !token.isNullOrBlank()) {
            if (authInFlight) return
            authInFlight = true
            Log.d("Deskly", "[Main] auto-auth")

            DesklyClient.auth(token) { ok, msg ->
                authInFlight = false
                Log.d("Deskly", "[Main] auth done ok=$ok msg=$msg")
                if (!ok) toast(msg)
                setStatusSimple(DesklyClient.state.connected || DesklyClient.state.authorized)
                updateUiState()
            }
        }
    }

    // =========================
    // Timer UI countdown
    // =========================
    private val timerUiHandler = Handler(Looper.getMainLooper())
    private var timerUiPosted = false

    private val timerUiRunnable = object : Runnable {
        override fun run() {
            if (!timerRunning) {
                timerUiPosted = false
                return
            }

            if (timerRemainingSecondsUi > 0) {
                timerRemainingSecondsUi--
            }

            // keď to dobehne na 0, ukonči lokálny UI countdown (real stav si aj tak berieme zo servera)
            if (timerRemainingSecondsUi <= 0) {
                timerRemainingSecondsUi = 0
                timerRunning = false
                stopLocalTimerUi()
                btnTimerStart.text = "START"
                updateTimerStatusUi()
                return
            }

            btnTimerStart.text = formatTime(timerRemainingSecondsUi)
            updateTimerStatusUi()

            timerUiHandler.postDelayed(this, 1000)
        }
    }

    private fun startLocalTimerUi() {
        if (timerUiPosted) return
        timerUiPosted = true
        timerUiHandler.post(timerUiRunnable)
    }

    private fun stopLocalTimerUi() {
        timerUiHandler.removeCallbacks(timerUiRunnable)
        timerUiPosted = false
    }

    // =========================
    // Timer polling
    // =========================
    private val timerPollHandler = Handler(Looper.getMainLooper())
    private var timerPollPosted = false
    private val timerPollRunnable = object : Runnable {
        override fun run() {
            val token = getTokenRaw()
            val a = DesklyClient.state.authorized
            if (token.isNullOrBlank() || !a) {
                timerPollPosted = false
                return
            }
            DesklyClient.sendSecure("sleep_timer_status", token)
            timerPollHandler.postDelayed(this, 1000)
        }
    }

    private fun startTimerPolling() {
        if (timerPollPosted) return
        timerPollPosted = true
        timerPollHandler.post(timerPollRunnable)
    }

    private fun stopTimerPolling() {
        timerPollHandler.removeCallbacks(timerPollRunnable)
        timerPollPosted = false
    }

    private companion object {
        private const val QUIET_REQUEST_TIMEOUT_MS = 3500L
        private const val NIGHT_THROTTLE_MS = 120L

        // UX: internal brightness = fast, external brightness + night intensity = smooth/slow
        private const val INTERNAL_THROTTLE_MS = 40L
        private const val EXTERNAL_THROTTLE_MS = 160L
        private const val EXTERNAL_SEND_ON_STOP = true
    }

    // =========================
    // Night throttled sender (globálny night mode na PC strane)
    // =========================
    private val nightHandler = Handler(Looper.getMainLooper())
    private var pendingNightIntensity: Int? = null
    private var nightSendPosted = false
    private var lastNightSentAt = 0L

    // =========================
    // Internal brightness throttling (FAST)
    // =========================
    private val internalHandler = Handler(Looper.getMainLooper())
    private var internalPosted = false
    private var pendingInternal: Int? = null
    private var lastInternalSentAt = 0L

    // =========================
    // External brightness throttling (SMOOTH / SLOW)
    // =========================
    private val externalHandler = Handler(Looper.getMainLooper())
    private var externalPosted = false
    private var pendingExternal: Int? = null
    private var lastExternalSentAt = 0L

    private fun scheduleNightSend(enabled: Boolean, intensity: Int) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return

        val v = intensity.coerceIn(0, 100)
        pendingNightIntensity = v

        if (nightSendPosted) return
        nightSendPosted = true

        nightHandler.postDelayed({
            nightSendPosted = false
            val now = System.currentTimeMillis()
            if (now - lastNightSentAt < NIGHT_THROTTLE_MS) {
                scheduleNightSend(enabled, pendingNightIntensity ?: v)
                return@postDelayed
            }
            lastNightSentAt = now

            DesklyClient.sendSecure(
                "night_set",
                token,
                JSONObject().put("enabled", enabled).put("intensity", pendingNightIntensity ?: v)
            )
        }, NIGHT_THROTTLE_MS)
    }

    private fun scheduleInternalBrightnessSend(value: Int) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        val id = internalDisplayId ?: return

        val v = value.coerceIn(0, 100)
        pendingInternal = v

        if (internalPosted) return
        internalPosted = true

        internalHandler.postDelayed({
            internalPosted = false

            val now = System.currentTimeMillis()
            if (now - lastInternalSentAt < INTERNAL_THROTTLE_MS) {
                scheduleInternalBrightnessSend(pendingInternal ?: v)
                return@postDelayed
            }
            lastInternalSentAt = now

            DesklyClient.sendSecure(
                "brightness_set",
                token,
                JSONObject().put("displayId", id).put("value", pendingInternal ?: v)
            )
        }, INTERNAL_THROTTLE_MS)
    }

    private fun scheduleExternalBrightnessSend(value: Int) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        val id = externalDisplayId ?: return

        val v = value.coerceIn(0, 100)
        pendingExternal = v

        if (externalPosted) return
        externalPosted = true

        externalHandler.postDelayed({
            externalPosted = false

            val now = System.currentTimeMillis()
            if (now - lastExternalSentAt < EXTERNAL_THROTTLE_MS) {
                scheduleExternalBrightnessSend(pendingExternal ?: v)
                return@postDelayed
            }
            lastExternalSentAt = now

            DesklyClient.sendSecure(
                "brightness_set",
                token,
                JSONObject().put("displayId", id).put("value", pendingExternal ?: v)
            )
        }, EXTERNAL_THROTTLE_MS)
    }

    private fun updateNightButtonIcon() {
        btnNight.text = when (primaryMode) {
            PrimaryMode.BRIGHTNESS -> "🖥️" // externý jas
            PrimaryMode.NIGHT_INTENSITY -> if (nightEnabled) "🌙" else "🌑"
        }

        btnNight.alpha = when (primaryMode) {
            PrimaryMode.BRIGHTNESS -> 1.0f
            PrimaryMode.NIGHT_INTENSITY -> if (nightEnabled) 1.0f else 0.75f
        }
    }

    private fun updateMuteIcon() {
        btnMute.text = if (lastMuted) "🔇" else "🔊"
        btnMute.alpha = if (!DesklyClient.state.authorized) 0.35f else if (lastMuted) 0.65f else 1.0f
    }

    // =========================
    // Quiet helpers  (MUST stay inside the class)
    // =========================
    private fun applyQuietUi(enabled: Boolean) {
        quietEnabled = enabled
        btnQuiet.alpha = if (enabled) 1.0f else 0.55f
    }

    private fun requestQuietSet(enabled: Boolean) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return

        quietRequestInFlight = true
        updateUiState()

        if (!quietTimeoutPosted) {
            quietTimeoutPosted = true
            quietHandler.postDelayed({
                quietTimeoutPosted = false
                if (quietRequestInFlight) {
                    quietRequestInFlight = false
                    toast("Quiet: timeout")
                    updateUiState()
                }
            }, QUIET_REQUEST_TIMEOUT_MS)
        }

        DesklyClient.sendSecure("quiet_set", token, JSONObject().put("enabled", enabled))
    }

    private fun clearQuietInFlight() {
        quietRequestInFlight = false
        quietTimeoutPosted = false
        quietHandler.removeCallbacksAndMessages(null)
    }

    // =========================
    // Lifecycle
    // =========================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DesklyClient.addListener(this)

        txtStatus = findViewById(R.id.txtStatus)
        btnSettingsIcon = findViewById(R.id.btnSettingsIcon)
        btnAboutIcon = findViewById(R.id.btnAboutIcon)

        seekPrimary = findViewById(R.id.seekPrimary)
        btnNight = findViewById(R.id.btnNight)

        seekInternalBrightness = findViewById(R.id.seekInternalBrightness)
        btnQuiet = findViewById(R.id.btnQuiet)

        seekVolume = findViewById(R.id.seekVolume)
        btnMute = findViewById(R.id.btnMute)

        txtTimerStatus = findViewById(R.id.txtTimerStatus)
        btnTimerMinus = findViewById(R.id.btnTimerMinus)
        btnTimerPlus = findViewById(R.id.btnTimerPlus)
        btnTimerStart = findViewById(R.id.btnTimerStart)
        btnTimerCancel = findViewById(R.id.btnTimerCancel)

        btnLock = findViewById(R.id.btnLock)
        btnSleep = findViewById(R.id.btnSleep)
        btnRestart = findViewById(R.id.btnRestart)
        btnShutdown = findViewById(R.id.btnShutdown)

        btnSettingsIcon.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnAboutIcon.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        selectedTimerAction = getSharedPreferences(prefsName, MODE_PRIVATE)
            .getString(keyTimerAction, "sleep") ?: "sleep"

        setStatusSimple(false)
        applyPrimaryMode(PrimaryMode.BRIGHTNESS)
        updateNightButtonIcon()
        updateMuteIcon()

        // =========================
        // Primary slider = (EXTERNAL brightness) alebo (NIGHT intensity)
        // =========================
        seekPrimary.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || ignorePrimarySeek) return
                val p = progress.coerceIn(0, 100)

                when (primaryMode) {
                    PrimaryMode.BRIGHTNESS -> {
                        // externý monitor má byť prirodzene "pomalý"
                        scheduleExternalBrightnessSend(p)
                    }

                    PrimaryMode.NIGHT_INTENSITY -> {
                        // night intenzita má byť prirodzene "pomalá"
                        nightIntensity = p
                        // pri ťahaní slidera vždy zabezpeč, že night je zapnutý a pošli intenzitu
                        if (!nightEnabled) nightEnabled = true
                        scheduleNightSend(true, nightIntensity)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {
                // po pustení slidera pošli finálnu hodnotu (najmä pri externom jase)
                if (!EXTERNAL_SEND_ON_STOP) return
                if (primaryMode != PrimaryMode.BRIGHTNESS) return

                val token = getTokenRaw() ?: return
                if (!DesklyClient.state.authorized) return
                val id = externalDisplayId ?: return

                val finalValue = seekPrimary.progress.coerceIn(0, 100)
                DesklyClient.sendSecure(
                    "brightness_set",
                    token,
                    JSONObject().put("displayId", id).put("value", finalValue)
                )
            }
        })

        // single tap = prepni mód slidera (externý jas ↔ night intenzita)
        // double tap = night ON/OFF (pomalé)
        btnNight.setOnClickListener {
            val now = System.currentTimeMillis()
            val dt = now - lastNightTapAt
            lastNightTapAt = now

            // DOUBLE TAP => toggluj night enabled
            if (dt in 1..doubleTapWindowMs) {
                nightEnabled = !nightEnabled
                if (nightEnabled && nightIntensity <= 0) nightIntensity = 25

                if (primaryMode == PrimaryMode.NIGHT_INTENSITY) {
                    ignorePrimarySeek = true
                    seekPrimary.progress = nightIntensity.coerceIn(0, 100)
                    ignorePrimarySeek = false
                }

                scheduleNightSend(nightEnabled, nightIntensity)
                updateNightButtonIcon()
                return@setOnClickListener
            }

            // SINGLE TAP => prepni mód
            val next =
                if (primaryMode == PrimaryMode.BRIGHTNESS) PrimaryMode.NIGHT_INTENSITY
                else PrimaryMode.BRIGHTNESS
            applyPrimaryMode(next)

            // keď prepnem do režimu intenzity, automaticky zapni night (aby slider hneď "fungoval")
            if (primaryMode == PrimaryMode.NIGHT_INTENSITY && !nightEnabled) {
                nightEnabled = true
                if (nightIntensity <= 0) nightIntensity = 25
                scheduleNightSend(true, nightIntensity)
            }

            updateNightButtonIcon()
        }

        // =========================
        // Internal slider = INTERNAL brightness (FAST)
        // =========================
        seekInternalBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || ignoreInternalSeek) return
                scheduleInternalBrightnessSend(progress.coerceIn(0, 100))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnQuiet.setOnClickListener {
            getToken() ?: return@setOnClickListener
            if (!DesklyClient.state.authorized) return@setOnClickListener
            if (quietRequestInFlight) return@setOnClickListener
            requestQuietSet(!quietEnabled)
        }
        applyQuietUi(false)

        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || ignoreVolumeSeek) return
                lastVol = progress.coerceIn(0, 100)

                // ak posuniem volume > 0, UI nech zruší mute (optimisticky)
                if (lastVol > 0 && lastMuted) {
                    lastMuted = false
                    updateMuteIcon()
                }

                val token = getToken() ?: return
                DesklyClient.sendSecure("volume_set", token, JSONObject().put("volume", lastVol))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnMute.setOnClickListener {
            val token = getToken() ?: return@setOnClickListener

            // optimistic UI
            lastMuted = !lastMuted
            updateMuteIcon()

            DesklyClient.sendSecure("mute_toggle", token)
        }

        btnTimerStart.text = "START"
        updateTimerStatusUi()

        btnTimerMinus.setOnClickListener {
            timerMinutes = (timerMinutes - 5).coerceAtLeast(1)
            updateTimerStatusUi()
        }
        btnTimerPlus.setOnClickListener {
            timerMinutes = (timerMinutes + 5).coerceAtMost(240)
            updateTimerStatusUi()
        }

        btnTimerStart.setOnClickListener {
            val token = getToken() ?: return@setOnClickListener
            val seconds = timerMinutes * 60

            DesklyClient.sendSecure("sleep_timer_set", token, JSONObject().apply {
                put("seconds", seconds)
                put("action", selectedTimerAction)
            })

            timerRunning = true
            timerRemainingSecondsUi = seconds
            timerActionUi = selectedTimerAction

            btnTimerStart.text = formatTime(timerRemainingSecondsUi)
            updateTimerStatusUi()

            startTimerPolling()
            startLocalTimerUi()
        }

        btnTimerCancel.setOnClickListener {
            val token = getToken() ?: return@setOnClickListener
            DesklyClient.sendSecure("sleep_timer_cancel", token)

            timerRunning = false
            stopTimerPolling()
            stopLocalTimerUi()
            btnTimerStart.text = "START"
            updateTimerStatusUi()
        }

        btnLock.setOnClickListener { sendPower("power_lock") }
        btnSleep.setOnClickListener { sendPower("power_sleep") }
        btnRestart.setOnClickListener { confirmPower("Restart PC?", "power_restart") }
        btnShutdown.setOnClickListener { confirmPower("Shutdown PC?", "power_shutdown") }

        updateUiState()
        logState("Main_onCreate_end")
    }

    override fun onResume() {
        super.onResume()

        DesklyClient.addListener(this)

        selectedTimerAction = getSharedPreferences(prefsName, MODE_PRIVATE)
            .getString(keyTimerAction, "sleep") ?: "sleep"
        updateTimerStatusUi()

        ensureConnectedAndAuthed()

        setStatusSimple(DesklyClient.state.connected || DesklyClient.state.authorized)
        updateUiState()

        logState("Main_onResume")
    }

    override fun onPause() {
        super.onPause()
        DesklyClient.removeListener(this)
    }

    private fun applyPrimaryMode(mode: PrimaryMode) {
        primaryMode = mode
        when (mode) {
            PrimaryMode.BRIGHTNESS -> {
                // slider ovláda externý jas (neprepíname jeho hodnotu)
            }
            PrimaryMode.NIGHT_INTENSITY -> {
                // slider ovláda night intenzitu
                ignorePrimarySeek = true
                seekPrimary.progress = nightIntensity.coerceIn(0, 100)
                ignorePrimarySeek = false
            }
        }
    }

    private fun setStatusSimple(connected: Boolean) {
        txtStatus.text = if (connected) "Connected" else "Disconnected"
    }

    private fun updateTimerStatusUi() {
        val action = if (timerRunning) timerActionUi else selectedTimerAction
        val actionLabel = if (action == "shutdown") "SHUTDOWN" else "SLEEP"
        txtTimerStatus.text = if (timerRunning) {
            "TIMER • ${formatTime(timerRemainingSecondsUi)} • $actionLabel"
        } else {
            "TIMER • ${timerMinutes} min • $actionLabel"
        }
    }

    /**
     * Aktualizuje timer UI podľa stavu zo servera.
     * Volá sa zo sleep_timer_status_response / sleep_timer_response.
     */
    private fun applyTimerStatusFromServer(running: Boolean, remainingSeconds: Int, action: String?) {
        val act = (action ?: "sleep").let { if (it == "shutdown") "shutdown" else "sleep" }

        if (running) {
            timerRunning = true
            timerActionUi = act
            timerRemainingSecondsUi = remainingSeconds.coerceAtLeast(0)
            btnTimerStart.text = formatTime(timerRemainingSecondsUi)
            startLocalTimerUi()
        } else {
            timerRunning = false
            timerRemainingSecondsUi = 0
            stopLocalTimerUi()
            btnTimerStart.text = "START"
        }

        updateTimerStatusUi()
        updateUiState()
    }

    private fun sendPower(type: String) {
        val token = getToken() ?: return
        if (!DesklyClient.state.authorized) return
        DesklyClient.sendSecure(type, token)
    }

    private fun confirmPower(title: String, type: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Are you sure?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ -> sendPower(type) }
            .show()
    }

    private fun updateUiState() {
        val a = DesklyClient.state.authorized

        // keep icons consistent
        if (this::btnNight.isInitialized) updateNightButtonIcon()
        if (this::btnMute.isInitialized) updateMuteIcon()

        val hasExternal = !externalDisplayId.isNullOrBlank()
        seekPrimary.isEnabled = a && !busy && hasExternal
        seekPrimary.alpha = if (a && hasExternal) 1.0f else 0.35f

        btnNight.isEnabled = a && !busy // night je globálny, necháme len podľa auth
        if (!btnNight.isEnabled) btnNight.alpha = 0.35f

        val hasInternal = !internalDisplayId.isNullOrBlank()
        seekInternalBrightness.isEnabled = a && !busy && hasInternal
        seekInternalBrightness.alpha = if (a && hasInternal) 1.0f else 0.35f

        btnQuiet.isEnabled = a && !busy && !quietRequestInFlight
        btnQuiet.alpha = if (btnQuiet.isEnabled) (if (quietEnabled) 1.0f else 0.55f) else 0.35f

        seekVolume.isEnabled = a && !busy
        btnMute.isEnabled = a && !busy
        updateMuteIcon()
        if (!btnMute.isEnabled) btnMute.alpha = 0.35f

        btnTimerMinus.isEnabled = a && !busy && !timerRunning
        btnTimerPlus.isEnabled = a && !busy && !timerRunning
        btnTimerStart.isEnabled = a && !busy
        btnTimerCancel.isEnabled = a && !busy

        btnLock.isEnabled = a && !busy
        btnSleep.isEnabled = a && !busy
        btnRestart.isEnabled = a && !busy
        btnShutdown.isEnabled = a && !busy

        updateTimerStatusUi()
    }

    private fun formatTime(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val mm = s / 60
        val ss = s % 60
        return String.format("%d:%02d", mm, ss)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // =========================
    // DesklyClient callbacks
    // =========================
    override fun onState(state: DesklyClient.State) {
        logState("Main_onState")
        setStatusSimple(state.authorized || state.connected)

        if (!state.connected || !state.authorized) {
            clearQuietInFlight()
            stopTimerPolling()
            stopLocalTimerUi()
            timerRunning = false
            btnTimerStart.text = "START"
            afterAuthDone = false

            // keď padne conn/auth, display IDs už nemusia sedieť
            internalDisplayId = null
            externalDisplayId = null
        }

        if (state.authorized && !afterAuthDone) {
            afterAuthDone = true
            val token = getTokenRaw()
            if (!token.isNullOrBlank()) afterAuthInit(token)
        }

        updateUiState()
    }

    override fun onLog(line: String) {
        // Log.d("DesklyWire", line)
    }

    override fun onJson(json: JSONObject) {
        try {
            when (json.optString("type")) {

                "sleep_timer_status_response", "sleep_timer_response" -> {
                    if (!json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data") ?: return

                    val running = data.optBoolean("running", false)
                    val remaining = data.optInt("remainingSeconds", 0)
                    val action = data.optString("action", "sleep")

                    applyTimerStatusFromServer(running, remaining, action)
                }

                "sleep_timer_cancel_response" -> {
                    // ak niekde používaš takýto typ, nech to nezostane visieť
                    applyTimerStatusFromServer(false, 0, selectedTimerAction)
                }



                "audio_response" -> {
                    if (!json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data") ?: return
                    val vol = data.optInt("volume", lastVol).coerceIn(0, 100)
                    val muted = data.optBoolean("muted", lastMuted)
                    applyVolumeToUi(vol, muted)
                }

                "brightness_response" -> {
                    if (!json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data") ?: return
                    val did = data.optString("displayId", "").trim()

                    if (did.equals("all", ignoreCase = true)) {
                        val values = data.optJSONArray("values") ?: return
                        for (i in 0 until values.length()) {
                            val o = values.optJSONObject(i) ?: continue
                            val id = o.optString("id", "").trim()
                            val v = o.optInt("value", -1)
                            if (id.isNotEmpty() && v >= 0) brightnessCache[id] = v.coerceIn(0, 100)
                        }
                    } else {
                        val v = data.optInt("value", -1)
                        if (did.isNotEmpty() && v >= 0) brightnessCache[did] = v.coerceIn(0, 100)
                    }

                    // apply now if we already know display ids
                    applyCachedBrightnessToUi()
                    updateUiState()
                }

                "night_response" -> {
                    if (!json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data") ?: return

                    nightEnabled = data.optBoolean("enabled", nightEnabled)
                    nightIntensity = data.optInt("intensity", nightIntensity).coerceIn(0, 100)
                    if (nightEnabled && nightIntensity <= 0) nightIntensity = 25

                    // ak práve ovládam night intenzitu, nech slider ukazuje správnu hodnotu
                    if (primaryMode == PrimaryMode.NIGHT_INTENSITY) {
                        ignorePrimarySeek = true
                        seekPrimary.progress = nightIntensity
                        ignorePrimarySeek = false
                    }

                    updateUiState()
                }


                "quiet_response" -> {
                    // vždy ukonči in-flight (aj pri chybe)
                    clearQuietInFlight()

                    if (!json.optBoolean("ok", false)) {
                        // typicky: potrebuje admin / zly token / chyba
                        val msg = json.optString("message", "Quiet failed")
                        toast("Tichý režim: $msg")
                        updateUiState()
                        return
                    }

                    val data = json.optJSONObject("data")
                    val enabled = data?.optBoolean("enabled", quietEnabled) ?: quietEnabled

                    applyQuietUi(enabled)
                    updateUiState()
                }

                // ✅ tu je najdôležitejšia zmena: čítame "type" z backendu
                "display_list_response" -> {
                    if (!json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data") ?: return
                    val arr = data.optJSONArray("displays") ?: return

                    internalDisplayId = null
                    externalDisplayId = null

                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id", "").trim()
                        if (id.isEmpty()) continue

                        val type = o.optString("type", "").trim().lowercase()
                        val supports = o.optBoolean("supportsBrightness", false)
                        if (!supports) continue

                        val name = o.optString("name", "").trim()
                        Log.d("Deskly", "[display] id=$id type=$type name=$name supports=$supports")

                        when (type) {
                            "internal" -> if (internalDisplayId == null) internalDisplayId = id
                            "external" -> if (externalDisplayId == null) externalDisplayId = id
                        }
                    }

                    Log.d("Deskly", "[display_pick] internal=$internalDisplayId external=$externalDisplayId")
                    applyCachedBrightnessToUi()
                    updateUiState()
                }
            }
        } catch (_: Exception) {}
    }

    private fun afterAuthInit(token: String) {
        logState("Main_afterAuthInit")
        DesklyClient.sendSecure("volume_get", token)
        DesklyClient.sendSecure("display_list", token)
        DesklyClient.sendSecure("brightness_get", token, JSONObject().put("displayId", "all"))
        DesklyClient.sendSecure("night_get", token)
        DesklyClient.sendSecure("quiet_get", token)
        DesklyClient.sendSecure("sleep_timer_status", token)
        startTimerPolling()
    }
}
