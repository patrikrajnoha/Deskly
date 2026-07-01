package com.example.deskly

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), DesklyClient.Listener {

    // =========================
    // PREFS / TOKEN
    // =========================
    private val prefsName = "deskly_prefs"
    private val keyTimerAction = "timer_action" // "sleep" | "shutdown"

    private fun getTokenRaw(): String? = DesklyPrefs.getToken(this)

    private fun getToken(): String? {
        val t = getTokenRaw()
        if (t.isNullOrEmpty()) {
            toast("Pair Required")
            return null
        }
        return t
    }

    private fun getSavedIpPort(): Pair<String, Int>? {
        val server = DesklyPrefs.getSavedServer(this) ?: return null
        return server.ip to server.port
    }

    private fun isStateForSavedServer(
        state: DesklyClient.State = DesklyClient.state,
        saved: Pair<String, Int>? = getSavedIpPort()
    ): Boolean {
        val (ip, port) = saved ?: return false
        return state.serverIp == ip && state.serverPort == port
    }

    private fun isAuthRejected(
        state: DesklyClient.State = DesklyClient.state,
        token: String? = getTokenRaw()
    ): Boolean {
        return !token.isNullOrBlank() &&
                rejectedAuthToken == token &&
                state.lastError?.contains("Unauthorized", ignoreCase = true) == true
    }

    // =========================
    // UI
    // =========================
    private lateinit var txtStatus: TextView
    private lateinit var txtPcName: TextView
    private lateinit var txtPcAddress: TextView
    private lateinit var txtActionStatus: TextView
    private lateinit var txtVolumeValue: TextView
    private lateinit var txtBrightnessValue: TextView
    private lateinit var txtBrightnessSupport: TextView
    private lateinit var txtEyeModeValue: TextView
    private lateinit var txtEyeIntensityValue: TextView
    private lateinit var txtTimerActionChip: TextView
    private lateinit var txtQuietValue: TextView
    private lateinit var txtBuiltInBrightnessValue: TextView
    private lateinit var cardEyeProtector: View
    private lateinit var cardMouse: View
    private lateinit var cardApps: View
    private lateinit var cardMedia: View
    private lateinit var cardKeyboard: View
    private lateinit var cardSlides: View
    private lateinit var cardShortcuts: View
    private lateinit var cardClipboard: View
    private lateinit var txtSectionTrackpad: View
    private lateinit var txtSectionApps: View
    private lateinit var txtSectionMedia: View
    private lateinit var txtSectionKeyboard: View
    private lateinit var txtSectionSlides: View
    private lateinit var txtSectionShortcuts: View
    private lateinit var txtSectionClipboard: View
    private lateinit var txtSectionControls: View
    private lateinit var txtSectionPower: View
    private lateinit var txtSectionSystem: View
    private lateinit var sectionControlsContent: View
    private lateinit var cardPower: View
    private lateinit var cardSystem: View
    private lateinit var spacerAfterTrackpad: View
    private lateinit var spacerAfterApps: View
    private lateinit var spacerAfterSlides: View
    private lateinit var spacerAfterMedia: View
    private lateinit var spacerAfterKeyboard: View
    private lateinit var spacerAfterShortcuts: View
    private lateinit var spacerAfterWebRemote: View
    private lateinit var spacerAfterClipboard: View

    private lateinit var btnSettingsIcon: ImageButton
    private lateinit var btnAboutIcon: ImageButton
    private lateinit var btnModeMouse: Button
    private lateinit var btnModeKeys: Button
    private lateinit var btnModeApps: Button
    private lateinit var btnModeSlides: Button
    private lateinit var btnModeSystem: Button

    private lateinit var seekPrimary: SeekBar
    private lateinit var btnNight: ImageButton

    private lateinit var seekInternalBrightness: SeekBar
    private lateinit var btnQuiet: ImageButton

    private lateinit var seekVolume: SeekBar
    private lateinit var btnMute: ImageButton
    private lateinit var btnDisplayOff: Button
    private lateinit var btnDisplayOn: Button
    private lateinit var btnDisplayInternal: Button
    private lateinit var btnDisplayDuplicate: Button
    private lateinit var btnDisplayExtend: Button
    private lateinit var btnDisplayExternal: Button
    private lateinit var txtPowerPlan: TextView
    private lateinit var btnPowerSaver: Button
    private lateinit var btnPowerBalanced: Button
    private lateinit var btnPowerHigh: Button

    private lateinit var txtTimerStatus: TextView
    private lateinit var btnTimerMinus: Button
    private lateinit var btnTimerPlus: Button
    private lateinit var btnTimerStart: Button
    private lateinit var btnTimerCancel: Button

    private lateinit var btnLock: Button
    private lateinit var btnSleep: Button
    private lateinit var btnRestart: Button
    private lateinit var btnShutdown: Button

    private lateinit var touchpadSurface: TextView
    private lateinit var btnMouseLeft: Button
    private lateinit var btnMouseRight: Button
    private lateinit var btnMouseDouble: Button
    private lateinit var btnMouseDrag: Button
    private lateinit var seekMouseSensitivity: SeekBar
    private lateinit var txtMouseSensitivity: TextView
    private lateinit var seekTouchpadScrollSensitivity: SeekBar
    private lateinit var txtTouchpadScrollSensitivity: TextView
    private lateinit var switchTouchpadNaturalScroll: Switch
    private lateinit var switchTouchpadFeedback: Switch
    private lateinit var switchMouseAcceleration: Switch
    private lateinit var switchMouseLeftHanded: Switch
    private lateinit var btnTouchpadHelp: Button

    private lateinit var edtKeyboardText: EditText
    private lateinit var btnKeyboardSend: Button
    private lateinit var btnKeyboardSendChars: Button
    private lateinit var btnKeyboardVoice: Button
    private lateinit var btnKeyEnter: Button
    private lateinit var btnKeyBackspace: Button
    private lateinit var btnKeyEsc: Button
    private lateinit var btnKeyTab: Button
    private lateinit var btnKeyUp: Button
    private lateinit var btnKeyLeft: Button
    private lateinit var btnKeyDown: Button
    private lateinit var btnKeyRight: Button
    private lateinit var btnShortcutCopy: Button
    private lateinit var btnShortcutPaste: Button
    private lateinit var btnShortcutSelectAll: Button
    private lateinit var btnShortcutAltTab: Button
    private lateinit var btnMediaPrevious: Button
    private lateinit var btnMediaPlayPause: Button
    private lateinit var btnMediaNext: Button
    private lateinit var btnMediaVolumeDown: Button
    private lateinit var btnMediaMute: Button
    private lateinit var btnMediaVolumeUp: Button
    private lateinit var btnMediaSeekBackward: Button
    private lateinit var btnMediaFullscreen: Button
    private lateinit var btnMediaSeekForward: Button
    private lateinit var btnVideoList: Button
    private lateinit var btnAppBrowser: Button
    private lateinit var btnAppFiles: Button
    private lateinit var btnAppNotes: Button
    private lateinit var btnAppCalc: Button
    private lateinit var btnAppTasks: Button
    private lateinit var btnAppSet1: Button
    private lateinit var btnAppSet2: Button
    private lateinit var btnAppSet3: Button
    private lateinit var btnAppSet4: Button
    private lateinit var btnAppSet5: Button
    private lateinit var btnAppWindows: Button
    private lateinit var btnWeb1: Button
    private lateinit var btnWeb2: Button
    private lateinit var btnWeb3: Button
    private lateinit var btnWeb4: Button
    private lateinit var btnWeb5: Button
    private lateinit var btnWebSet1: Button
    private lateinit var btnWebSet2: Button
    private lateinit var btnWebSet3: Button
    private lateinit var btnWebSet4: Button
    private lateinit var btnWebSet5: Button
    private lateinit var btnSlidesStart: Button
    private lateinit var btnSlidesPrevious: Button
    private lateinit var btnSlidesNext: Button
    private lateinit var btnSlidesBlack: Button
    private lateinit var btnSlidesExit: Button
    private lateinit var btnQuickShortcut1: Button
    private lateinit var btnQuickShortcut2: Button
    private lateinit var btnQuickShortcut3: Button
    private lateinit var btnQuickShortcut4: Button
    private lateinit var btnQuickShortcut5: Button
    private lateinit var btnQuickShortcutSet1: Button
    private lateinit var btnQuickShortcutSet2: Button
    private lateinit var btnQuickShortcutSet3: Button
    private lateinit var btnQuickShortcutSet4: Button
    private lateinit var btnQuickShortcutSet5: Button
    private lateinit var cardWebRemote: View
    private lateinit var txtSectionWebRemote: View
    private lateinit var btnWebBack: Button
    private lateinit var btnWebForward: Button
    private lateinit var btnWebRefresh: Button
    private lateinit var btnWebNewTab: Button
    private lateinit var btnWebCloseTab: Button
    private lateinit var btnWebPrevTab: Button
    private lateinit var btnWebNextTab: Button
    private lateinit var btnWebPageUp: Button
    private lateinit var btnWebPageDown: Button
    private lateinit var btnWebFullscreen: Button
    private lateinit var edtClipboardText: EditText
    private lateinit var btnClipboardCopy: Button

    // =========================
    // STATE
    // =========================
    private var busy = false
    private val appShortcutConfigured = BooleanArray(5)
    private val webShortcuts = arrayOfNulls<DesklyPrefs.WebShortcut>(5)
    private val appCatalog = mutableListOf<AppCatalogItem>()
    private var pendingAppPickerSlot: Int? = null
    private val appPickerHandler = Handler(Looper.getMainLooper())
    private val actionStatusHandler = Handler(Looper.getMainLooper())
    private val shortcutSlotActions = arrayOfNulls<String>(5)
    private var remoteMode: RemoteMode = RemoteMode.MOUSE
    private var selectedVideoTargetId: String? = null
    private enum class RemoteMode { MOUSE, KEYS, APPS, SLIDES, SYSTEM }
    private data class AppCatalogItem(val id: String, val label: String)

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
    private var currentPowerPlan: String = ""
    private val supportedPowerPlans = mutableMapOf(
        "power_saver" to false,
        "balanced" to false,
        "high_performance" to false
    )

    private var ignoreVolumeSeek = false
    private var lastVol = 50
    private var lastMuted = false
    private var previousConnectionState: DesklyClient.State? = null

    private var mouseSensitivityPercent = 100
    private var touchpadSettings = TouchpadSettings()
    private val touchpadMotionFilter = TouchpadMotionFilter()
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartedAt = 0L
    private var touchMoved = false
    private var rightClickFired = false
    private var twoFingerGestureActive = false
    private var pinchGestureActive = false
    private var mouseDragActive = false
    private var mouseDragButton = "left"
    private val touchpadGestureInterpreter = TouchpadGestureInterpreter()
    private var lastMouseMoveSentAt = 0L
    private var lastTouchZoomSentAt = 0L
    private var lastMouseMoveLoggedAt = 0L
    private var lastMouseFailureLoggedAt = 0L
    private var lastTapUpAt = 0L
    private var singleTapPending = false
    private var touchSlopPx = 12
    private val mouseLongPressHandler = Handler(Looper.getMainLooper())
    private val mouseTapHandler = Handler(Looper.getMainLooper())
    private val mouseLongPressRunnable = Runnable {
        if (!touchMoved && DesklyClient.state.authorized) {
            rightClickFired = true
            sendMouseClick(secondaryMouseButton())
        }
    }
    private val mouseSingleTapRunnable = Runnable {
        singleTapPending = false
        sendMouseClick(primaryMouseButton())
    }

    // =========================
    // SYNC cache (server → UI)
    // =========================
    // Brightness values keyed by displayId (as returned from backend)
    private val brightnessCache = mutableMapOf<String, Int>()
    private val originalBrightnessCache = mutableMapOf<String, Int>()
    private var restoreBrightnessSent = false

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

        refreshValueLabels()
    }

    private fun rememberOriginalBrightness(displayId: String, value: Int) {
        if (displayId.isBlank()) return
        originalBrightnessCache.putIfAbsent(displayId, value.coerceIn(0, 100))
    }

    private fun applyVolumeToUi(volume: Int, muted: Boolean) {
        lastVol = volume.coerceIn(0, 100)
        lastMuted = muted

        ignoreVolumeSeek = true
        seekVolume.progress = lastVol
        ignoreVolumeSeek = false

        updateMuteIcon()
        refreshValueLabels()
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
    private var rejectedAuthToken: String? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectPosted = false
    private var reconnectAttempts = 0
    private var performancePolicy = PerformancePolicy()
    private var performanceDiagnostics = false
    private var foregroundActive = false
    private var droppedMouseMoves = 0
    private var sentMouseMoves = 0
    private var lastPerfLogAt = 0L

    // =========================
    // Auto connect + auth
    // =========================
    private fun ensureConnectedAndAuthed() {
        val s = DesklyClient.state
        val token = getTokenRaw()

        if (!token.isNullOrBlank()) DesklyClient.setToken(token)
        if (rejectedAuthToken != null && rejectedAuthToken != token) {
            rejectedAuthToken = null
        }

        if (s.connecting || s.authenticating) {
            renderConnectionStatus()
            updateUiState()
            return
        }

        val savedIpPort = getSavedIpPort()
        val connectedToDifferentServer = s.connected && savedIpPort != null && !isStateForSavedServer(s, savedIpPort)

        if (s.authorized && !connectedToDifferentServer) {
            if (!afterAuthDone && !token.isNullOrBlank()) {
                afterAuthDone = true
                afterAuthInit(token)
            }
            renderConnectionStatus()
            updateUiState()
            return
        }

        if (!s.connected || connectedToDifferentServer) {
            if (connectInFlight) return
            if (!DesklyPrefs.getAutoConnect(this)) {
                renderConnectionStatus()
                updateUiState()
                return
            }

            val ipPort = savedIpPort
            if (ipPort == null) {
                renderConnectionStatus()
                updateUiState()
                return
            }

            val (ip, port) = ipPort
            connectInFlight = true
            afterAuthDone = false
            if (connectedToDifferentServer) {
                Log.d("Deskly", "Saved PC changed; reconnecting to $ip:$port authorized=${DesklyClient.state.authorized}")
            } else {
                Log.d("Deskly", "Reconnect/connect attempt to $ip:$port authorized=${DesklyClient.state.authorized}")
            }
            DesklyClient.connect(ip, port) { ok, err ->
                connectInFlight = false
                if (!ok) {
                    Log.w("Deskly", "Reconnect/connect failed: ${err ?: "unknown"} authorized=${DesklyClient.state.authorized}")
                    scheduleReconnect()
                    renderConnectionStatus()
                    updateUiState()
                    return@connect
                }
                reconnectAttempts = 0
                ensureConnectedAndAuthed()
            }
            return
        }

        if (token.isNullOrBlank()) {
            renderConnectionStatus()
            updateUiState()
            return
        }

        if (isAuthRejected(s)) {
            renderConnectionStatus()
            updateUiState()
            return
        }

        if (authInFlight) return
        authInFlight = true

        DesklyClient.auth(token) { ok, msg ->
            authInFlight = false
            if (ok) {
                rejectedAuthToken = null
                reconnectAttempts = 0
                Log.d("Deskly", "Auth success authorized=${DesklyClient.state.authorized}")
                if (!afterAuthDone) {
                    afterAuthDone = true
                    afterAuthInit(token)
                }
            } else {
                rejectedAuthToken = token
                Log.w("Deskly", "Auth failed: $msg authorized=${DesklyClient.state.authorized}")
                toast("Auth Failed: $msg")
            }
            renderConnectionStatus()
            updateUiState()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectPosted) return
        if (!DesklyPrefs.getAutoConnect(this)) return
        if (getSavedIpPort() == null) return
        if (isAuthRejected()) return

        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(6)
        val delayMs = when (reconnectAttempts) {
            1 -> performancePolicy.reconnectBaseDelayMs
            2 -> performancePolicy.reconnectBaseDelayMs * 2
            3 -> performancePolicy.reconnectBaseDelayMs * 4
            else -> performancePolicy.reconnectBaseDelayMs * 8
        }
            .coerceAtMost(30_000L)

        Log.d("Deskly", "Reconnect scheduled attempt=$reconnectAttempts delayMs=$delayMs authorized=${DesklyClient.state.authorized}")
        reconnectPosted = true
        reconnectHandler.postDelayed({
            reconnectPosted = false
            if (!DesklyClient.state.authorized && !DesklyClient.state.connecting && !DesklyClient.state.authenticating) {
                ensureConnectedAndAuthed()
            }
        }, delayMs)
    }

    private fun cancelReconnect() {
        reconnectPosted = false
        reconnectHandler.removeCallbacksAndMessages(null)
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
                btnTimerStart.text = "Start"
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
            if (!performancePolicy.shouldPollTimer(timerRunning)) {
                timerPollPosted = false
                return
            }
            DesklyClient.sendSecure("sleep_timer_status", token)
            timerPollHandler.postDelayed(this, performancePolicy.timerPollMs)
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
        private const val MOUSE_MOVE_THROTTLE_MS = 16L
        private const val MOUSE_TAP_MAX_MS = 220L
        private const val MOUSE_DOUBLE_TAP_MS = 260L
        private const val MOUSE_LONG_PRESS_MS = 560L
        private const val MOUSE_MOVE_LOG_THROTTLE_MS = 1200L
        private const val MOUSE_FAILURE_LOG_THROTTLE_MS = 1500L
        private const val TOUCH_ZOOM_THROTTLE_MS = 140L
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
        val mode = DesklyPrefs.getEyeMode(this)
        val screenDim = if (enabled) DesklyPrefs.getScreenDim(this) else 0
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
                JSONObject()
                    .put("enabled", enabled)
                    .put("mode", mode.id)
                    .put("kelvin", mode.kelvin)
                    .put("intensity", pendingNightIntensity ?: v)
                    .put("screenDim", screenDim)
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
        btnNight.setImageResource(
            when (primaryMode) {
                PrimaryMode.BRIGHTNESS -> R.drawable.ic_brightness
                PrimaryMode.NIGHT_INTENSITY -> R.drawable.ic_nightlight
            }
        )
        btnNight.contentDescription = when (primaryMode) {
            PrimaryMode.BRIGHTNESS -> "Brightness"
            PrimaryMode.NIGHT_INTENSITY -> "Night Mode"
        }

        btnNight.alpha = when (primaryMode) {
            PrimaryMode.BRIGHTNESS -> 1.0f
            PrimaryMode.NIGHT_INTENSITY -> if (nightEnabled) 1.0f else 0.75f
        }
    }

    private fun updateMuteIcon() {
        btnMute.setImageResource(if (lastMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        btnMute.contentDescription = if (lastMuted) "Unmute" else "Mute"
        btnMute.alpha = if (!DesklyClient.state.authorized) 0.35f else if (lastMuted) 0.65f else 1.0f
    }

    private fun refreshHeaderInfo() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val saved = DesklyPrefs.getSavedServer(this)
        val viewState = ConnectionStatusModel.build(
            state = DesklyClient.state,
            savedPcName = prefs.getString(DesklyPrefs.KEY_DEVICE_NAME, null),
            savedIp = saved?.ip,
            savedPort = saved?.port,
            hasToken = DesklyPrefs.hasToken(this),
            authRejected = isAuthRejected()
        )
        txtPcName.text = viewState.pcName
        txtPcAddress.text = "${viewState.address} - ${viewState.connectionType.name}"
    }

    private fun refreshValueLabels() {
        if (this::txtVolumeValue.isInitialized) txtVolumeValue.text = "${lastVol.coerceIn(0, 100)}%"
        if (this::txtBrightnessValue.isInitialized) {
            txtBrightnessValue.text = when (primaryMode) {
                PrimaryMode.BRIGHTNESS -> "${seekPrimary.progress.coerceIn(0, 100)}%"
                PrimaryMode.NIGHT_INTENSITY -> "${nightIntensity.coerceIn(0, 100)}%"
            }
        }
        if (this::txtBuiltInBrightnessValue.isInitialized) {
            txtBuiltInBrightnessValue.text = "${seekInternalBrightness.progress.coerceIn(0, 100)}%"
        }
        if (this::txtBrightnessSupport.isInitialized) {
            txtBrightnessSupport.text = when {
                !DesklyClient.state.authorized -> "Offline"
                primaryMode == PrimaryMode.NIGHT_INTENSITY -> "Night Mode intensity"
                externalDisplayId.isNullOrBlank() -> "Unsupported"
                else -> "External"
            }
        }
        if (this::txtEyeModeValue.isInitialized) {
            val mode = DesklyPrefs.getEyeMode(this)
            txtEyeModeValue.text = "${mode.name} - ${mode.kelvin}K"
        }
        if (this::txtEyeIntensityValue.isInitialized) {
            txtEyeIntensityValue.text = "${nightIntensity.coerceIn(0, 100)}%"
        }
        if (this::txtQuietValue.isInitialized) {
            txtQuietValue.text = if (quietEnabled) "On" else "Off"
            txtQuietValue.setTextColor(getColor(if (quietEnabled) R.color.text_primary else R.color.text_muted))
        }
    }

    // =========================
    // Quiet helpers  (MUST stay inside the class)
    // =========================
    private fun applyQuietUi(enabled: Boolean) {
        quietEnabled = enabled
        btnQuiet.alpha = if (enabled) 1.0f else 0.65f
        btnQuiet.setBackgroundResource(if (enabled) R.drawable.bg_btn_primary else R.drawable.bg_btn)
        btnQuiet.contentDescription = if (enabled) "Quiet Mode On" else "Quiet Mode Off"
        refreshValueLabels()
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

    private fun toggleEyeProtector() {
        val token = getTokenRaw()
        if (token.isNullOrBlank() || !DesklyClient.state.authorized) {
            toast("Pair Required")
            return
        }

        nightEnabled = !nightEnabled
        if (nightEnabled) {
            nightIntensity = DesklyPrefs.getEyeIntensity(this)
            applyPrimaryMode(PrimaryMode.NIGHT_INTENSITY)
        }

        scheduleNightSend(nightEnabled, nightIntensity)
        updateNightButtonIcon()
        refreshValueLabels()
        updateUiState()
    }

    private fun clearQuietInFlight() {
        quietRequestInFlight = false
        quietTimeoutPosted = false
        quietHandler.removeCallbacksAndMessages(null)
    }

    private fun sendDisplayControl(action: String) {
        val token = getToken() ?: return
        if (!DesklyClient.state.authorized) return
        DesklyClient.sendSecure("display_control", token, JSONObject().put("action", action))
    }

    private fun sendDisplayMode(mode: String) {
        val token = getToken() ?: return
        if (!DesklyClient.state.authorized) return
        DesklyClient.sendSecure("display_mode_set", token, JSONObject().put("mode", mode))
    }

    private fun setupMouseControls() {
        touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop
        touchpadSettings = DesklyPrefs.getTouchpadSettings(this)
        mouseSensitivityPercent = touchpadSettings.cursorSpeedPercent
        seekMouseSensitivity.progress = mouseSensitivityPercent
        seekTouchpadScrollSensitivity.progress = touchpadSettings.scrollSensitivityPercent
        switchTouchpadNaturalScroll.isChecked = touchpadSettings.naturalScroll
        switchTouchpadFeedback.isChecked = touchpadSettings.visualFeedback
        switchMouseAcceleration.isChecked = touchpadSettings.acceleration
        switchMouseLeftHanded.isChecked = touchpadSettings.leftHanded
        updateMouseSensitivityLabel()
        updateScrollSensitivityLabel()
        updateMouseButtonLabels()

        seekMouseSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val safe = progress.coerceIn(TouchpadSettings.MIN_PERCENT, TouchpadSettings.MAX_PERCENT)
                mouseSensitivityPercent = safe
                touchpadSettings = touchpadSettings.copy(cursorSpeedPercent = safe).normalized()
                updateMouseSensitivityLabel()
                if (fromUser) DesklyPrefs.setMouseSensitivity(this@MainActivity, safe)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                DesklyPrefs.setMouseSensitivity(this@MainActivity, mouseSensitivityPercent)
            }
        })

        seekTouchpadScrollSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val safe = progress.coerceIn(TouchpadSettings.MIN_PERCENT, TouchpadSettings.MAX_PERCENT)
                touchpadSettings = touchpadSettings.copy(scrollSensitivityPercent = safe).normalized()
                updateScrollSensitivityLabel()
                if (fromUser) DesklyPrefs.setTouchpadScrollSensitivity(this@MainActivity, safe)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                DesklyPrefs.setTouchpadScrollSensitivity(
                    this@MainActivity,
                    touchpadSettings.scrollSensitivityPercent
                )
            }
        })

        switchTouchpadNaturalScroll.setOnCheckedChangeListener { _, checked ->
            touchpadSettings = touchpadSettings.copy(naturalScroll = checked)
            DesklyPrefs.setTouchpadNaturalScroll(this, checked)
        }

        switchTouchpadFeedback.setOnCheckedChangeListener { _, checked ->
            touchpadSettings = touchpadSettings.copy(visualFeedback = checked)
            DesklyPrefs.setTouchpadVisualFeedback(this, checked)
        }

        switchMouseAcceleration.setOnCheckedChangeListener { _, checked ->
            touchpadSettings = touchpadSettings.copy(acceleration = checked)
            DesklyPrefs.setMouseAcceleration(this, checked)
        }

        switchMouseLeftHanded.setOnCheckedChangeListener { _, checked ->
            touchpadSettings = touchpadSettings.copy(leftHanded = checked)
            DesklyPrefs.setMouseLeftHanded(this, checked)
            updateMouseButtonLabels()
        }

        btnTouchpadHelp.setOnClickListener { showTouchpadHelp() }
        btnMouseLeft.setOnClickListener { sendMouseClick(primaryMouseButton()) }
        btnMouseRight.setOnClickListener { sendMouseClick(secondaryMouseButton()) }
        btnMouseDouble.setOnClickListener { sendMouseClick(primaryMouseButton(), clicks = 2) }
        btnMouseDrag.setOnClickListener { toggleMouseDrag() }

        touchpadSurface.setOnTouchListener { view, event ->
            handleTouchpadEvent(view, event)
        }
    }

    private fun updateMouseSensitivityLabel() {
        if (!this::txtMouseSensitivity.isInitialized) return
        txtMouseSensitivity.text = when {
            mouseSensitivityPercent < 85 -> "Low"
            mouseSensitivityPercent > 125 -> "High"
            else -> "Normal"
        }
    }

    private fun updateScrollSensitivityLabel() {
        if (!this::txtTouchpadScrollSensitivity.isInitialized) return
        txtTouchpadScrollSensitivity.text = when {
            touchpadSettings.scrollSensitivityPercent < 85 -> "Low"
            touchpadSettings.scrollSensitivityPercent > 125 -> "High"
            else -> "Normal"
        }
    }

    private fun showTouchpadHelp() {
        AlertDialog.Builder(this)
            .setTitle("Trackpad")
            .setMessage(
                "Move with one finger.\n" +
                    "Tap for left click.\n" +
                    "Double tap for double click.\n" +
                    "Long press for right click.\n" +
                    "Move two fingers to scroll.\n" +
                    "Pinch with two fingers to zoom."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun handleTouchpadEvent(view: View, event: MotionEvent): Boolean {
        if (!view.isEnabled || !DesklyClient.state.authorized) {
            cancelMouseGestureTimers()
            view.parent?.requestDisallowInterceptTouchEvent(false)
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                touchStartX = event.x
                touchStartY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                touchpadMotionFilter.reset()
                touchStartedAt = System.currentTimeMillis()
                touchMoved = false
                rightClickFired = false
                twoFingerGestureActive = false
                pinchGestureActive = false
                touchpadGestureInterpreter.reset()
                mouseLongPressHandler.removeCallbacks(mouseLongPressRunnable)
                mouseLongPressHandler.postDelayed(mouseLongPressRunnable, MOUSE_LONG_PRESS_MS)
                pulseTouchpadFeedback()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mouseLongPressHandler.removeCallbacks(mouseLongPressRunnable)
                cancelPendingSingleTap()
                touchpadGestureInterpreter.startTwoFingerGesture(
                    averageY = averagePointerY(event),
                    distance = pointerDistance(event)
                )
                touchMoved = true
                twoFingerGestureActive = true
                pinchGestureActive = false
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    mouseLongPressHandler.removeCallbacks(mouseLongPressRunnable)
                    cancelPendingSingleTap()
                    when (val result = touchpadGestureInterpreter.interpretMove(
                        averageY = averagePointerY(event),
                        distance = pointerDistance(event)
                    )) {
                        TouchpadGestureInterpreter.Result.ZoomIn -> {
                            sendTouchpadZoom("zoom_in", "Zoom in")
                            pinchGestureActive = true
                            return true
                        }
                        TouchpadGestureInterpreter.Result.ZoomOut -> {
                            sendTouchpadZoom("zoom_out", "Zoom out")
                            pinchGestureActive = true
                            return true
                        }
                        is TouchpadGestureInterpreter.Result.Scroll -> {
                            if (!pinchGestureActive) {
                                sendMouseScroll(deltaY = applyScrollSettings(result.deltaY))
                                pulseTouchpadFeedback()
                            }
                        }
                        TouchpadGestureInterpreter.Result.None -> Unit
                    }
                    return true
                }

                if (twoFingerGestureActive) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    touchStartX = event.x
                    touchStartY = event.y
                    twoFingerGestureActive = false
                    return true
                }

                val totalDx = event.x - touchStartX
                val totalDy = event.y - touchStartY
                if (abs(totalDx) > touchSlopPx.toFloat() || abs(totalDy) > touchSlopPx.toFloat()) {
                    touchMoved = true
                    mouseLongPressHandler.removeCallbacks(mouseLongPressRunnable)
                    cancelPendingSingleTap()
                }

                val now = System.currentTimeMillis()
                if (now - lastMouseMoveSentAt >= performancePolicy.mouseMoveThrottleMs) {
                    val delta = touchpadMotionFilter.filter(
                        rawDx = event.x - lastTouchX,
                        rawDy = event.y - lastTouchY,
                        speedPercent = mouseSensitivityPercent,
                        accelerationEnabled = touchpadSettings.acceleration
                    )
                    if (delta != TouchpadMotionFilter.Delta.Zero) {
                        sendMouseMove(delta.dx, delta.dy)
                        lastMouseMoveSentAt = now
                        lastTouchX = event.x
                        lastTouchY = event.y
                        pulseTouchpadFeedback()
                    }
                } else {
                    droppedMouseMoves++
                }
                maybeLogInputPerformance(now)
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                mouseLongPressHandler.removeCallbacks(mouseLongPressRunnable)
                twoFingerGestureActive = false
                pinchGestureActive = false
                touchpadGestureInterpreter.reset()
                touchpadMotionFilter.reset()
                val duration = System.currentTimeMillis() - touchStartedAt
                if (!touchMoved && !rightClickFired && duration <= MOUSE_TAP_MAX_MS) {
                    handleTouchpadTap()
                    view.performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                cancelMouseGestureTimers()
                twoFingerGestureActive = false
                pinchGestureActive = false
                touchpadGestureInterpreter.reset()
                touchpadMotionFilter.reset()
                return true
            }
        }

        return true
    }

    private fun handleTouchpadTap() {
        val now = System.currentTimeMillis()
        if (singleTapPending && now - lastTapUpAt <= MOUSE_DOUBLE_TAP_MS) {
            cancelPendingSingleTap()
            lastTapUpAt = 0L
            sendMouseClick(primaryMouseButton(), clicks = 2)
            return
        }

        singleTapPending = true
        lastTapUpAt = now
        mouseTapHandler.postDelayed(mouseSingleTapRunnable, MOUSE_DOUBLE_TAP_MS)
    }

    private fun cancelPendingSingleTap() {
        if (!singleTapPending) return
        singleTapPending = false
        mouseTapHandler.removeCallbacks(mouseSingleTapRunnable)
    }

    private fun cancelMouseGestureTimers() {
        mouseLongPressHandler.removeCallbacks(mouseLongPressRunnable)
        cancelPendingSingleTap()
    }

    private fun primaryMouseButton(): String = if (touchpadSettings.leftHanded) "right" else "left"

    private fun secondaryMouseButton(): String = if (touchpadSettings.leftHanded) "left" else "right"

    private fun updateMouseButtonLabels() {
        if (!this::btnMouseLeft.isInitialized) return
        btnMouseLeft.text = if (touchpadSettings.leftHanded) "Right" else "Left"
        btnMouseRight.text = if (touchpadSettings.leftHanded) "Left" else "Right"
        btnMouseDouble.text = "Double"
        btnMouseDrag.text = if (mouseDragActive) "Drop" else "Drag"
        btnMouseDrag.setBackgroundResource(if (mouseDragActive) R.drawable.bg_btn_primary else R.drawable.bg_btn)
    }

    private fun toggleMouseDrag() {
        if (mouseDragActive) {
            endMouseDrag()
        } else {
            startMouseDrag()
        }
    }

    private fun startMouseDrag() {
        if (!DesklyClient.state.authorized) return
        mouseDragButton = primaryMouseButton()
        if (!sendMouseButton(mouseDragButton, down = true)) return
        mouseDragActive = true
        showActionStatus("Dragging")
        updateMouseButtonLabels()
    }

    private fun endMouseDrag() {
        if (!mouseDragActive) return
        val button = mouseDragButton
        mouseDragActive = false
        sendMouseButton(button, down = false)
        showActionStatus("Dropped")
        updateMouseButtonLabels()
    }

    private fun cancelMouseDrag() {
        if (!mouseDragActive) return
        val button = mouseDragButton
        mouseDragActive = false
        if (DesklyClient.state.authorized) sendMouseButton(button, down = false)
        updateMouseButtonLabels()
    }

    private fun averagePointerY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount.coerceAtLeast(1)
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun applyScrollSettings(deltaY: Int): Int {
        if (deltaY == 0) return 0
        val direction = if (touchpadSettings.naturalScroll) 1 else -1
        val scaled = (deltaY * direction * (touchpadSettings.scrollSensitivityPercent / 100f)).roundToInt()
        return when {
            scaled == 0 && deltaY > 0 -> 1
            scaled == 0 && deltaY < 0 -> -1
            else -> scaled.coerceIn(-10, 10)
        }
    }

    private fun pulseTouchpadFeedback() {
        if (!touchpadSettings.visualFeedback || !this::touchpadSurface.isInitialized || !touchpadSurface.isEnabled) return
        touchpadSurface.animate().cancel()
        touchpadSurface.alpha = 0.82f
        touchpadSurface.scaleX = 0.995f
        touchpadSurface.scaleY = 0.995f
        touchpadSurface.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(90L)
            .start()
    }

    private fun sendMouseMove(dx: Int, dy: Int) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        sentMouseMoves++
        logMouseCommand(MouseProtocol.TYPE_MOVE)
        DesklyClient.sendSecure(
            MouseProtocol.TYPE_MOVE,
            token,
            MouseProtocol.movePayload(dx, dy)
        )
    }

    private fun sendMouseClick(button: String, clicks: Int = 1) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        logMouseCommand(MouseProtocol.TYPE_CLICK)
        DesklyClient.sendSecure(
            MouseProtocol.TYPE_CLICK,
            token,
            MouseProtocol.clickPayload(button, clicks)
        )
    }

    private fun sendMouseButton(button: String, down: Boolean): Boolean {
        val token = getTokenRaw() ?: return false
        if (!DesklyClient.state.authorized) return false
        logMouseCommand(MouseProtocol.TYPE_BUTTON)
        DesklyClient.sendSecure(
            MouseProtocol.TYPE_BUTTON,
            token,
            MouseProtocol.buttonPayload(button, down)
        )
        return true
    }

    private fun sendMouseScroll(deltaY: Int, deltaX: Int = 0) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        logMouseCommand(MouseProtocol.TYPE_SCROLL)
        DesklyClient.sendSecure(
            MouseProtocol.TYPE_SCROLL,
            token,
            MouseProtocol.scrollPayload(deltaY = deltaY, deltaX = deltaX)
        )
    }

    private fun logMouseCommand(type: String) {
        if (type == MouseProtocol.TYPE_MOVE || type == MouseProtocol.TYPE_SCROLL) {
            val now = System.currentTimeMillis()
            if (now - lastMouseMoveLoggedAt < MOUSE_MOVE_LOG_THROTTLE_MS) return
            lastMouseMoveLoggedAt = now
        }

        Log.d("Deskly", "Touchpad command=$type authorized=${DesklyClient.state.authorized}")
    }

    private fun logMouseFailure(command: String, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastMouseFailureLoggedAt < MOUSE_FAILURE_LOG_THROTTLE_MS) return
        lastMouseFailureLoggedAt = now
        Log.w("Deskly", "Touchpad command failed: command=$command message=$message authorized=${DesklyClient.state.authorized}")
    }

    private fun maybeLogInputPerformance(now: Long = System.currentTimeMillis()) {
        if (!performanceDiagnostics) return
        if (now - lastPerfLogAt < 5_000L) return
        lastPerfLogAt = now
        Log.d(
            "Deskly",
            "perf input sentMoves=$sentMouseMoves droppedMoves=$droppedMouseMoves " +
                "mouseThrottleMs=${performancePolicy.mouseMoveThrottleMs} lowPower=${performancePolicy.effectiveLowPower}"
        )
        sentMouseMoves = 0
        droppedMouseMoves = 0
    }

    private fun setupKeyboardControls() {
        edtKeyboardText.imeOptions = EditorInfo.IME_ACTION_SEND
        edtKeyboardText.setSingleLine(false)
        edtKeyboardText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendKeyboardText(edtKeyboardText.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        btnKeyboardSend.setOnClickListener {
            val text = edtKeyboardText.text?.toString().orEmpty()
            sendKeyboardText(text)
        }
        btnKeyboardSendChars.setOnClickListener {
            val text = edtKeyboardText.text?.toString().orEmpty()
            sendKeyboardText(text, perCharacter = true)
        }
        btnKeyboardVoice.setOnClickListener { focusKeyboardForVoiceInput() }

        btnKeyEnter.setOnClickListener { sendKeyboardKey("enter") }
        btnKeyBackspace.setOnClickListener { sendKeyboardKey("backspace") }
        btnKeyEsc.setOnClickListener { sendKeyboardKey("esc") }
        btnKeyTab.setOnClickListener { sendKeyboardKey("tab") }
        btnKeyUp.setOnClickListener { sendKeyboardKey("up") }
        btnKeyLeft.setOnClickListener { sendKeyboardKey("left") }
        btnKeyDown.setOnClickListener { sendKeyboardKey("down") }
        btnKeyRight.setOnClickListener { sendKeyboardKey("right") }

        btnShortcutCopy.setOnClickListener { sendKeyboardShortcut("ctrl", "c") }
        btnShortcutPaste.setOnClickListener { sendKeyboardShortcut("ctrl", "v") }
        btnShortcutSelectAll.setOnClickListener { sendKeyboardShortcut("ctrl", "a") }
        btnShortcutAltTab.setOnClickListener { sendKeyboardShortcut("alt", "tab") }
    }

    private fun focusKeyboardForVoiceInput() {
        edtKeyboardText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val shown = imm?.showSoftInput(edtKeyboardText, InputMethodManager.SHOW_IMPLICIT) == true
        toast(if (shown) "Use the keyboard mic, then Send" else "Voice input depends on your Android keyboard")
    }

    private fun setupShortcutSlots() {
        loadShortcutSlots()
        applyShortcutSlotLabels()

        btnQuickShortcut1.setOnClickListener { sendShortcutSlot(1) }
        btnQuickShortcut2.setOnClickListener { sendShortcutSlot(2) }
        btnQuickShortcut3.setOnClickListener { sendShortcutSlot(3) }
        btnQuickShortcut4.setOnClickListener { sendShortcutSlot(4) }
        btnQuickShortcut5.setOnClickListener { sendShortcutSlot(5) }
        arrayOf(btnQuickShortcut1, btnQuickShortcut2, btnQuickShortcut3, btnQuickShortcut4, btnQuickShortcut5)
            .forEachIndexed { index, button ->
                button.setOnLongClickListener {
                    showShortcutPicker(index + 1)
                    true
                }
            }

        btnQuickShortcutSet1.setOnClickListener { showShortcutPicker(1) }
        btnQuickShortcutSet2.setOnClickListener { showShortcutPicker(2) }
        btnQuickShortcutSet3.setOnClickListener { showShortcutPicker(3) }
        btnQuickShortcutSet4.setOnClickListener { showShortcutPicker(4) }
        btnQuickShortcutSet5.setOnClickListener { showShortcutPicker(5) }
    }

    private fun setupClipboardControls() {
        btnClipboardCopy.setOnClickListener {
            sendClipboardText(edtClipboardText.text?.toString().orEmpty())
        }
    }

    private fun setupMediaControls() {
        btnMediaPrevious.setOnClickListener { sendMediaAction("previous") }
        btnMediaPlayPause.setOnClickListener { sendMediaAction("play_pause") }
        btnMediaNext.setOnClickListener { sendMediaAction("next") }
        btnMediaVolumeDown.setOnClickListener { sendMediaAction("volume_down") }
        btnMediaMute.setOnClickListener { sendMediaAction("mute") }
        btnMediaVolumeUp.setOnClickListener { sendMediaAction("volume_up") }
        btnMediaSeekBackward.setOnClickListener { sendMediaAction("seek_backward") }
        btnMediaFullscreen.setOnClickListener { sendMediaAction("fullscreen") }
        btnMediaSeekForward.setOnClickListener { sendMediaAction("seek_forward") }
        btnVideoList.setOnClickListener { requestVideoList() }
    }

    private fun setupAppShortcuts() {
        btnAppBrowser.setOnClickListener { sendAppSlot(1) }
        btnAppFiles.setOnClickListener { sendAppSlot(2) }
        btnAppNotes.setOnClickListener { sendAppSlot(3) }
        btnAppCalc.setOnClickListener { sendAppSlot(4) }
        btnAppTasks.setOnClickListener { sendAppSlot(5) }
        arrayOf(btnAppBrowser, btnAppFiles, btnAppNotes, btnAppCalc, btnAppTasks)
            .forEachIndexed { index, button ->
                button.setOnLongClickListener {
                    showAppPicker(index + 1)
                    true
                }
            }
        btnAppSet1.setOnClickListener { showAppPicker(1) }
        btnAppSet2.setOnClickListener { showAppPicker(2) }
        btnAppSet3.setOnClickListener { showAppPicker(3) }
        btnAppSet4.setOnClickListener { showAppPicker(4) }
        btnAppSet5.setOnClickListener { showAppPicker(5) }
        btnAppWindows.setOnClickListener { requestAppWindows() }
    }

    private fun setupWebShortcuts() {
        loadWebShortcuts()
        applyWebShortcutLabels()

        val webButtons = arrayOf(btnWeb1, btnWeb2, btnWeb3, btnWeb4, btnWeb5)
        webButtons.forEachIndexed { index, button ->
            button.setOnClickListener { sendWebSlot(index + 1) }
            button.setOnLongClickListener {
                showWebEditor(index + 1)
                true
            }
        }

        btnWebSet1.setOnClickListener { showWebEditor(1) }
        btnWebSet2.setOnClickListener { showWebEditor(2) }
        btnWebSet3.setOnClickListener { showWebEditor(3) }
        btnWebSet4.setOnClickListener { showWebEditor(4) }
        btnWebSet5.setOnClickListener { showWebEditor(5) }

        btnWebBack.setOnClickListener { sendShortcutAction("browser_back") }
        btnWebForward.setOnClickListener { sendShortcutAction("browser_forward") }
        btnWebRefresh.setOnClickListener { sendShortcutAction("refresh") }
        btnWebNewTab.setOnClickListener { sendShortcutAction("new_tab") }
        btnWebCloseTab.setOnClickListener { sendShortcutAction("close_tab") }
        btnWebPrevTab.setOnClickListener { sendShortcutAction("previous_tab") }
        btnWebNextTab.setOnClickListener { sendShortcutAction("next_tab") }
        btnWebPageUp.setOnClickListener { sendShortcutAction("page_scroll_up") }
        btnWebPageDown.setOnClickListener { sendShortcutAction("page_scroll_down") }
        btnWebFullscreen.setOnClickListener { sendShortcutAction("fullscreen") }
    }

    private fun setupPresentationControls() {
        btnSlidesStart.setOnClickListener { sendPresentationAction("start") }
        btnSlidesPrevious.setOnClickListener { sendPresentationAction("previous") }
        btnSlidesNext.setOnClickListener { sendPresentationAction("next") }
        btnSlidesBlack.setOnClickListener { sendPresentationAction("black") }
        btnSlidesExit.setOnClickListener { sendPresentationAction("exit") }
    }

    private fun setupRemoteModes() {
        btnModeMouse.setOnClickListener { applyRemoteMode(RemoteMode.MOUSE) }
        btnModeKeys.setOnClickListener { applyRemoteMode(RemoteMode.KEYS) }
        btnModeApps.setOnClickListener { applyRemoteMode(RemoteMode.APPS) }
        btnModeSlides.setOnClickListener { applyRemoteMode(RemoteMode.SLIDES) }
        btnModeSystem.setOnClickListener { applyRemoteMode(RemoteMode.SYSTEM) }
        applyRemoteMode(remoteMode)
    }

    private fun applyRemoteMode(mode: RemoteMode) {
        remoteMode = mode

        val showMouse = mode == RemoteMode.MOUSE
        val showKeys = mode == RemoteMode.KEYS
        val showApps = mode == RemoteMode.APPS
        val showSlides = mode == RemoteMode.SLIDES
        val showSystem = mode == RemoteMode.SYSTEM

        setVisible(txtSectionTrackpad, showMouse)
        setVisible(touchpadSurface, showMouse)
        setVisible(btnMouseLeft, showMouse)
        setVisible(btnMouseRight, showMouse)
        setVisible(seekMouseSensitivity, showMouse)
        setVisible(txtMouseSensitivity, showMouse)
        setVisible(cardMouse, showMouse)
        setVisible(txtSectionMedia, showMouse)
        setVisible(cardMedia, showMouse)

        setVisible(txtSectionKeyboard, showKeys)
        setVisible(cardKeyboard, showKeys)
        setVisible(txtSectionShortcuts, showKeys)
        setVisible(cardShortcuts, showKeys)
        setVisible(txtSectionWebRemote, showKeys)
        setVisible(cardWebRemote, showKeys)
        setVisible(txtSectionClipboard, showKeys)
        setVisible(cardClipboard, showKeys)

        setVisible(txtSectionApps, showApps)
        setVisible(cardApps, showApps)

        setVisible(txtSectionSlides, showSlides)
        setVisible(cardSlides, showSlides)

        setVisible(txtSectionControls, showSystem)
        setVisible(sectionControlsContent, showSystem)
        setVisible(txtSectionPower, showSystem)
        setVisible(cardPower, showSystem)
        setVisible(txtSectionSystem, showSystem)
        setVisible(cardSystem, showSystem)
        setVisible(spacerAfterTrackpad, false)
        setVisible(spacerAfterApps, showMouse)
        setVisible(spacerAfterSlides, false)
        setVisible(spacerAfterMedia, false)
        setVisible(spacerAfterKeyboard, showKeys)
        setVisible(spacerAfterWebRemote, showKeys)
        setVisible(spacerAfterShortcuts, showKeys)
        setVisible(spacerAfterClipboard, false)

        val buttons = mapOf(
            RemoteMode.MOUSE to btnModeMouse,
            RemoteMode.KEYS to btnModeKeys,
            RemoteMode.APPS to btnModeApps,
            RemoteMode.SLIDES to btnModeSlides,
            RemoteMode.SYSTEM to btnModeSystem
        )
        buttons.forEach { (buttonMode, button) ->
            button.setBackgroundResource(if (buttonMode == mode) R.drawable.bg_btn_primary else R.drawable.bg_btn)
            button.alpha = if (buttonMode == mode) 1.0f else 0.72f
        }
    }

    private fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun sendKeyboardText(text: String, perCharacter: Boolean = false) {
        val chunks = KeyboardProtocol.textChunks(text, perCharacter)
        if (chunks.isEmpty()) return
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        chunks.forEach { chunk ->
            logKeyboardCommand(KeyboardProtocol.TYPE_TEXT)
            DesklyClient.sendSecure(
                KeyboardProtocol.TYPE_TEXT,
                token,
                KeyboardProtocol.textPayload(chunk)
            )
        }
    }

    private fun sendKeyboardKey(key: String, presses: Int = 1) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        logKeyboardCommand(KeyboardProtocol.TYPE_KEY)
        DesklyClient.sendSecure(
            KeyboardProtocol.TYPE_KEY,
            token,
            KeyboardProtocol.keyPayload(key, presses)
        )
    }

    private fun sendKeyboardShortcut(vararg keys: String) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        logKeyboardCommand(KeyboardProtocol.TYPE_SHORTCUT)
        DesklyClient.sendSecure(
            KeyboardProtocol.TYPE_SHORTCUT,
            token,
            KeyboardProtocol.shortcutPayload(keys.asIterable())
        )
    }

    private fun logKeyboardCommand(type: String) {
        Log.d("Deskly", "Keyboard command=$type authorized=${DesklyClient.state.authorized}")
    }

    private fun sendMediaAction(action: String, targetId: String? = selectedVideoTargetId) {
        val normalized = MediaProtocol.normalizeAction(action)
        if (!MediaProtocol.isSupported(normalized)) {
            toast("Unsupported")
            return
        }
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        Log.d("Deskly", "Media command=$normalized authorized=${DesklyClient.state.authorized}")
        DesklyClient.sendSecure(
            MediaProtocol.TYPE,
            token,
            MediaProtocol.payload(normalized, targetId)
        )
    }

    private fun requestVideoList() {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        showActionStatus("Loading")
        DesklyClient.sendSecure(VideoProtocol.TYPE_LIST, token, VideoProtocol.refreshPayload())
    }

    private fun showVideoList(result: VideoProtocol.ListResult, message: String) {
        if (!result.supported || result.videos.isEmpty()) {
            showActionStatus("Media Remote")
            toast(message.ifBlank { "Use Media Remote" })
            return
        }

        val labels = result.videos.map { item ->
            if (item.source.isBlank()) item.title else "${item.source}: ${item.title}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Videos")
            .setItems(labels) { _, which ->
                val item = result.videos.getOrNull(which) ?: return@setItems
                selectedVideoTargetId = item.id
                showVideoControls(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVideoControls(item: VideoProtocol.VideoItem) {
        val actions = arrayOf("Play/Pause", "Seek Back", "Seek Forward", "Fullscreen")
        val ids = arrayOf("play_pause", "seek_backward", "seek_forward", "fullscreen")
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(actions) { _, which ->
                val action = ids.getOrNull(which) ?: return@setItems
                sendMediaAction(action, item.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPresentationAction(action: String) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        Log.d("Deskly", "Presentation command=$action authorized=${DesklyClient.state.authorized}")
        showActionStatus("Sent")
        DesklyClient.sendSecure(
            "presentation_action",
            token,
            JSONObject().put("action", action)
        )
    }

    private fun shortcutSlotKey(slot: Int) = "quick_shortcut_slot_$slot"

    private fun loadShortcutSlots() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        for (slot in 1..5) {
            shortcutSlotActions[slot - 1] = prefs.getString(shortcutSlotKey(slot), null)
                ?.takeIf { id -> ShortcutProtocol.actionLabel(id) != null }
        }
    }

    private fun saveShortcutSlot(slot: Int, actionId: String) {
        if (slot !in 1..5) return
        val normalizedAction = ShortcutProtocol.normalizeAction(actionId)
        if (ShortcutProtocol.actionLabel(normalizedAction) == null) return
        shortcutSlotActions[slot - 1] = normalizedAction
        getSharedPreferences(prefsName, MODE_PRIVATE)
            .edit()
            .putString(shortcutSlotKey(slot), normalizedAction)
            .apply()
        applyShortcutSlotLabels()
        updateUiState()
        showActionStatus("Shortcut set")
    }

    private fun applyShortcutSlotLabels() {
        val buttons = arrayOf(btnQuickShortcut1, btnQuickShortcut2, btnQuickShortcut3, btnQuickShortcut4, btnQuickShortcut5)
        buttons.forEachIndexed { index, button ->
            val actionId = shortcutSlotActions[index]
            val label = actionId?.let { ShortcutProtocol.actionLabel(it) } ?: "+"
            button.text = label.take(14)
        }
    }

    private fun showShortcutPicker(slot: Int) {
        val actions = ShortcutProtocol.supportedActions
        val labels = actions.map { action ->
            val category = when (action.category) {
                ShortcutProtocol.Category.SYSTEM -> "System"
                ShortcutProtocol.Category.BROWSER -> "Browser"
            }
            "$category: ${action.label}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Slot $slot")
            .setItems(labels) { _, which ->
                val action = actions.getOrNull(which) ?: return@setItems
                saveShortcutSlot(slot, action.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendShortcutSlot(slot: Int) {
        if (slot !in 1..5) return
        val actionId = shortcutSlotActions[slot - 1]
        if (actionId == null) {
            showShortcutPicker(slot)
            return
        }
        sendShortcutAction(actionId)
    }

    private fun sendShortcutAction(action: String, showStatus: Boolean = true) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        val normalizedAction = ShortcutProtocol.normalizeAction(action)
        if (!ShortcutProtocol.isSupported(normalizedAction)) {
            toast("Unsupported")
            return
        }
        Log.d("Deskly", "Shortcut command=$normalizedAction authorized=${DesklyClient.state.authorized}")
        if (showStatus) showActionStatus("Sent")
        DesklyClient.sendSecure(
            ShortcutProtocol.TYPE,
            token,
            ShortcutProtocol.payload(normalizedAction)
        )
    }

    private fun sendTouchpadZoom(action: String, status: String) {
        val now = System.currentTimeMillis()
        if (now - lastTouchZoomSentAt < TOUCH_ZOOM_THROTTLE_MS) return
        sendShortcutAction(action, showStatus = false)
        showActionStatus(status)
        lastTouchZoomSentAt = now
    }

    private fun sendClipboardText(text: String) {
        if (!DesklyPrefs.getClipboardSyncEnabled(this)) {
            toast("Clipboard sync is disabled in Settings")
            return
        }
        val payload = ClipboardProtocol.payload(text) ?: return
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        Log.d("Deskly", "Clipboard command=clipboard_set length=${payload.optString("text").length} authorized=${DesklyClient.state.authorized}")
        showActionStatus("Sending")
        DesklyClient.sendSecure(
            ClipboardProtocol.TYPE_SET,
            token,
            payload
        )
    }

    private fun sendAppOpen(slot: Int) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        Log.d("Deskly", "App command=app_open slot=$slot authorized=${DesklyClient.state.authorized}")
        showActionStatus("Opening")
        DesklyClient.sendSecure(
            "app_open",
            token,
            JSONObject().put("slot", slot.coerceIn(1, 5))
        )
    }

    private fun sendAppSlot(slot: Int) {
        if (slot !in 1..5) return
        if (!appShortcutConfigured[slot - 1]) {
            showAppPicker(slot)
            return
        }
        sendAppOpen(slot)
    }

    private fun loadWebShortcuts() {
        for (slot in 1..5) {
            webShortcuts[slot - 1] = DesklyPrefs.getWebShortcut(this, slot)
        }
    }

    private fun applyWebShortcutLabels() {
        val webButtons = arrayOf(btnWeb1, btnWeb2, btnWeb3, btnWeb4, btnWeb5)
        webButtons.forEachIndexed { index, button ->
            button.text = webShortcuts[index]?.name?.take(14) ?: "+"
        }
    }

    private fun showWebEditor(slot: Int) {
        if (slot !in 1..5) return
        val current = webShortcuts[slot - 1]

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Name"
            setSingleLine(true)
            setText(current?.name.orEmpty())
        }
        val urlInput = EditText(this).apply {
            hint = "URL"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(current?.url.orEmpty())
        }
        container.addView(nameInput)
        container.addView(urlInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Web $slot")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                DesklyPrefs.clearWebShortcut(this, slot)
                webShortcuts[slot - 1] = null
                applyWebShortcutLabels()
                updateUiState()
                showActionStatus("Cleared")
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val url = normalizeWebUrl(urlInput.text?.toString().orEmpty())
                if (url == null) {
                    showActionStatus("Invalid URL")
                    return@setOnClickListener
                }

                val label = nameInput.text?.toString()?.trim()
                    ?.take(32)
                    ?.ifBlank { defaultWebLabel(url) }
                    ?: defaultWebLabel(url)

                DesklyPrefs.setWebShortcut(this, slot, label, url)
                webShortcuts[slot - 1] = DesklyPrefs.WebShortcut(label, url)
                applyWebShortcutLabels()
                updateUiState()
                showActionStatus("Web set")
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun sendWebSlot(slot: Int) {
        if (slot !in 1..5) return
        val shortcut = webShortcuts[slot - 1]
        if (shortcut == null) {
            showWebEditor(slot)
            return
        }

        sendWebOpen(shortcut.url)
    }

    private fun sendWebOpen(url: String) {
        val safeUrl = WebProtocol.normalizeUrl(url) ?: return
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        Log.d("Deskly", "Web command=web_open authorized=${DesklyClient.state.authorized}")
        showActionStatus("Opening")
        DesklyClient.sendSecure(
            WebProtocol.TYPE_OPEN,
            token,
            WebProtocol.payload(safeUrl)
        )
    }

    private fun normalizeWebUrl(raw: String): String? {
        return WebProtocol.normalizeUrl(raw)
    }

    private fun defaultWebLabel(url: String): String {
        return WebProtocol.defaultLabel(url)
    }

    private fun requestAppShortcuts() {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        DesklyClient.sendSecure("app_shortcuts_get", token)
    }

    private fun requestAppCatalog() {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        DesklyClient.sendSecure("app_catalog_get", token)
    }

    private fun requestAppWindows() {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        showActionStatus("Loading")
        DesklyClient.sendSecure(AppWindowProtocol.TYPE_WINDOWS_GET, token)
    }

    private fun showAppWindowPicker(windows: List<AppWindowProtocol.WindowItem>) {
        if (windows.isEmpty()) {
            showActionStatus("No windows")
            return
        }

        val labels = windows.map { item ->
            if (item.appName.isBlank()) item.title else "${item.appName}: ${item.title}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Open Windows")
            .setItems(labels) { _, which ->
                val item = windows.getOrNull(which) ?: return@setItems
                sendAppSwitch(item.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendAppSwitch(windowId: String) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        showActionStatus("Switching")
        DesklyClient.sendSecure(
            AppWindowProtocol.TYPE_SWITCH,
            token,
            AppWindowProtocol.switchPayload(windowId)
        )
    }

    private fun showAppPicker(slot: Int) {
        if (!DesklyClient.state.authorized) return
        if (appCatalog.isEmpty()) {
            pendingAppPickerSlot = slot.coerceIn(1, 5)
            requestAppCatalog()
            showActionStatus("Loading apps")
            appPickerHandler.postDelayed({
                if (pendingAppPickerSlot != null && appCatalog.isEmpty()) {
                    pendingAppPickerSlot = null
                    showActionStatus("Update host")
                    Log.w("Deskly", "App catalog did not arrive authorized=${DesklyClient.state.authorized}")
                }
            }, 4000L)
            return
        }

        val labels = appCatalog.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Slot $slot")
            .setItems(labels) { _, which ->
                val item = appCatalog.getOrNull(which) ?: return@setItems
                sendAppShortcutSet(slot, item.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendAppShortcutSet(slot: Int, appId: String) {
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        Log.d("Deskly", "App command=app_shortcut_set slot=$slot authorized=${DesklyClient.state.authorized}")
        DesklyClient.sendSecure(
            "app_shortcut_set",
            token,
            JSONObject()
                .put("slot", slot.coerceIn(1, 5))
                .put("appId", appId)
        )
    }

    private fun applyAppShortcutSlots(slots: JSONArray) {
        val appButtons = arrayOf(btnAppBrowser, btnAppFiles, btnAppNotes, btnAppCalc, btnAppTasks)

        for (i in appShortcutConfigured.indices) {
            appShortcutConfigured[i] = false
            appButtons[i].text = "+"
        }

        for (i in 0 until slots.length()) {
            val item = slots.optJSONObject(i) ?: continue
            val slot = item.optInt("slot", 0)
            if (slot !in 1..5) continue

            val index = slot - 1
            val label = item.optString("label", "App $slot").trim().ifBlank { "App $slot" }
            val configured = item.optBoolean("configured", false)

            appButtons[index].text = label.take(14)
            appShortcutConfigured[index] = configured
        }

        updateUiState()
    }

    private fun applyAppCatalog(apps: JSONArray) {
        appCatalog.clear()
        for (i in 0 until apps.length()) {
            val item = apps.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            val label = item.optString("label", "").trim()
            if (id.isNotBlank() && label.isNotBlank()) {
                appCatalog.add(AppCatalogItem(id, label))
            }
        }

        val pendingSlot = pendingAppPickerSlot
        pendingAppPickerSlot = null
        if (pendingSlot != null && appCatalog.isNotEmpty()) {
            showAppPicker(pendingSlot)
        } else if (pendingSlot != null) {
            showActionStatus("No apps found")
        }
    }

    private fun requestPowerPlan(plan: String) {
        val token = getToken() ?: return
        if (!DesklyClient.state.authorized) return
        DesklyClient.sendSecure("power_plan_set", token, JSONObject().put("plan", plan))
    }

    private fun applyPowerPlanUi() {
        val label = when (currentPowerPlan) {
            "power_saver" -> "Low"
            "balanced" -> "Balanced"
            "high_performance" -> "Max"
            "custom" -> "Custom"
            "unsupported" -> "Unsupported"
            else -> "--"
        }

        if (this::txtPowerPlan.isInitialized) {
            txtPowerPlan.text = "Mode: $label"
        }

        if (this::btnPowerSaver.isInitialized) {
            btnPowerSaver.alpha = if (currentPowerPlan == "power_saver") 1.0f else 0.65f
            btnPowerBalanced.alpha = if (currentPowerPlan == "balanced") 1.0f else 0.65f
            btnPowerHigh.alpha = if (currentPowerPlan == "high_performance") 1.0f else 0.65f
            btnPowerSaver.setBackgroundResource(if (currentPowerPlan == "power_saver") R.drawable.bg_btn_primary else R.drawable.bg_btn)
            btnPowerBalanced.setBackgroundResource(if (currentPowerPlan == "balanced") R.drawable.bg_btn_primary else R.drawable.bg_btn)
            btnPowerHigh.setBackgroundResource(if (currentPowerPlan == "high_performance") R.drawable.bg_btn_primary else R.drawable.bg_btn)
        }
    }

    // =========================
    // Lifecycle
    // =========================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DesklyClient.addListener(this)
        NotificationHelper.createNotificationChannel(this)

        txtStatus = findViewById(R.id.txtStatus)
        txtPcName = findViewById(R.id.txtPcName)
        txtPcAddress = findViewById(R.id.txtPcAddress)
        txtActionStatus = findViewById(R.id.txtActionStatus)
        txtVolumeValue = findViewById(R.id.txtVolumeValue)
        txtBrightnessValue = findViewById(R.id.txtBrightnessValue)
        txtBrightnessSupport = findViewById(R.id.txtBrightnessSupport)
        txtEyeModeValue = findViewById(R.id.txtEyeModeValue)
        txtEyeIntensityValue = findViewById(R.id.txtEyeIntensityValue)
        txtTimerActionChip = findViewById(R.id.txtTimerActionChip)
        txtQuietValue = findViewById(R.id.txtQuietValue)
        txtBuiltInBrightnessValue = findViewById(R.id.txtBuiltInBrightnessValue)
        cardEyeProtector = findViewById(R.id.cardEyeProtector)
        cardMouse = findViewById(R.id.cardMouse)
        cardApps = findViewById(R.id.cardApps)
        cardMedia = findViewById(R.id.cardMedia)
        cardKeyboard = findViewById(R.id.cardKeyboard)
        cardSlides = findViewById(R.id.cardSlides)
        cardShortcuts = findViewById(R.id.cardShortcuts)
        cardWebRemote = findViewById(R.id.cardWebRemote)
        cardClipboard = findViewById(R.id.cardClipboard)
        txtSectionTrackpad = findViewById(R.id.txtSectionTrackpad)
        txtSectionApps = findViewById(R.id.txtSectionApps)
        txtSectionMedia = findViewById(R.id.txtSectionMedia)
        txtSectionKeyboard = findViewById(R.id.txtSectionKeyboard)
        txtSectionSlides = findViewById(R.id.txtSectionSlides)
        txtSectionShortcuts = findViewById(R.id.txtSectionShortcuts)
        txtSectionWebRemote = findViewById(R.id.txtSectionWebRemote)
        txtSectionClipboard = findViewById(R.id.txtSectionClipboard)
        txtSectionControls = findViewById(R.id.txtSectionControls)
        txtSectionPower = findViewById(R.id.txtSectionPower)
        txtSectionSystem = findViewById(R.id.txtSectionSystem)
        sectionControlsContent = findViewById(R.id.sectionControlsContent)
        cardPower = findViewById(R.id.cardPower)
        cardSystem = findViewById(R.id.cardSystem)
        spacerAfterTrackpad = findViewById(R.id.spacerAfterTrackpad)
        spacerAfterApps = findViewById(R.id.spacerAfterApps)
        spacerAfterSlides = findViewById(R.id.spacerAfterSlides)
        spacerAfterMedia = findViewById(R.id.spacerAfterMedia)
        spacerAfterKeyboard = findViewById(R.id.spacerAfterKeyboard)
        spacerAfterShortcuts = findViewById(R.id.spacerAfterShortcuts)
        spacerAfterWebRemote = findViewById(R.id.spacerAfterWebRemote)
        spacerAfterClipboard = findViewById(R.id.spacerAfterClipboard)
        btnSettingsIcon = findViewById(R.id.btnSettingsIcon)
        btnAboutIcon = findViewById(R.id.btnAboutIcon)
        btnModeMouse = findViewById(R.id.btnModeMouse)
        btnModeKeys = findViewById(R.id.btnModeKeys)
        btnModeApps = findViewById(R.id.btnModeApps)
        btnModeSlides = findViewById(R.id.btnModeSlides)
        btnModeSystem = findViewById(R.id.btnModeSystem)

        seekPrimary = findViewById(R.id.seekPrimary)
        btnNight = findViewById(R.id.btnNight)

        seekInternalBrightness = findViewById(R.id.seekInternalBrightness)
        btnQuiet = findViewById(R.id.btnQuiet)

        seekVolume = findViewById(R.id.seekVolume)
        btnMute = findViewById(R.id.btnMute)
        btnDisplayOff = findViewById(R.id.btnDisplayOff)
        btnDisplayOn = findViewById(R.id.btnDisplayOn)
        btnDisplayInternal = findViewById(R.id.btnDisplayInternal)
        btnDisplayDuplicate = findViewById(R.id.btnDisplayDuplicate)
        btnDisplayExtend = findViewById(R.id.btnDisplayExtend)
        btnDisplayExternal = findViewById(R.id.btnDisplayExternal)
        txtPowerPlan = findViewById(R.id.txtPowerPlan)
        btnPowerSaver = findViewById(R.id.btnPowerSaver)
        btnPowerBalanced = findViewById(R.id.btnPowerBalanced)
        btnPowerHigh = findViewById(R.id.btnPowerHigh)

        txtTimerStatus = findViewById(R.id.txtTimerStatus)
        btnTimerMinus = findViewById(R.id.btnTimerMinus)
        btnTimerPlus = findViewById(R.id.btnTimerPlus)
        btnTimerStart = findViewById(R.id.btnTimerStart)
        btnTimerCancel = findViewById(R.id.btnTimerCancel)

        btnLock = findViewById(R.id.btnLock)
        btnSleep = findViewById(R.id.btnSleep)
        btnRestart = findViewById(R.id.btnRestart)
        btnShutdown = findViewById(R.id.btnShutdown)

        touchpadSurface = findViewById(R.id.touchpadSurface)
        btnMouseLeft = findViewById(R.id.btnMouseLeft)
        btnMouseRight = findViewById(R.id.btnMouseRight)
        btnMouseDouble = findViewById(R.id.btnMouseDouble)
        btnMouseDrag = findViewById(R.id.btnMouseDrag)
        seekMouseSensitivity = findViewById(R.id.seekMouseSensitivity)
        txtMouseSensitivity = findViewById(R.id.txtMouseSensitivity)
        seekTouchpadScrollSensitivity = findViewById(R.id.seekTouchpadScrollSensitivity)
        txtTouchpadScrollSensitivity = findViewById(R.id.txtTouchpadScrollSensitivity)
        switchTouchpadNaturalScroll = findViewById(R.id.switchTouchpadNaturalScroll)
        switchTouchpadFeedback = findViewById(R.id.switchTouchpadFeedback)
        switchMouseAcceleration = findViewById(R.id.switchMouseAcceleration)
        switchMouseLeftHanded = findViewById(R.id.switchMouseLeftHanded)
        btnTouchpadHelp = findViewById(R.id.btnTouchpadHelp)
        edtKeyboardText = findViewById(R.id.edtKeyboardText)
        btnKeyboardSend = findViewById(R.id.btnKeyboardSend)
        btnKeyboardSendChars = findViewById(R.id.btnKeyboardSendChars)
        btnKeyboardVoice = findViewById(R.id.btnKeyboardVoice)
        btnKeyEnter = findViewById(R.id.btnKeyEnter)
        btnKeyBackspace = findViewById(R.id.btnKeyBackspace)
        btnKeyEsc = findViewById(R.id.btnKeyEsc)
        btnKeyTab = findViewById(R.id.btnKeyTab)
        btnKeyUp = findViewById(R.id.btnKeyUp)
        btnKeyLeft = findViewById(R.id.btnKeyLeft)
        btnKeyDown = findViewById(R.id.btnKeyDown)
        btnKeyRight = findViewById(R.id.btnKeyRight)
        btnShortcutCopy = findViewById(R.id.btnShortcutCopy)
        btnShortcutPaste = findViewById(R.id.btnShortcutPaste)
        btnShortcutSelectAll = findViewById(R.id.btnShortcutSelectAll)
        btnShortcutAltTab = findViewById(R.id.btnShortcutAltTab)
        btnMediaPrevious = findViewById(R.id.btnMediaPrevious)
        btnMediaPlayPause = findViewById(R.id.btnMediaPlayPause)
        btnMediaNext = findViewById(R.id.btnMediaNext)
        btnMediaVolumeDown = findViewById(R.id.btnMediaVolumeDown)
        btnMediaMute = findViewById(R.id.btnMediaMute)
        btnMediaVolumeUp = findViewById(R.id.btnMediaVolumeUp)
        btnMediaSeekBackward = findViewById(R.id.btnMediaSeekBackward)
        btnMediaFullscreen = findViewById(R.id.btnMediaFullscreen)
        btnMediaSeekForward = findViewById(R.id.btnMediaSeekForward)
        btnVideoList = findViewById(R.id.btnVideoList)
        btnAppBrowser = findViewById(R.id.btnAppBrowser)
        btnAppFiles = findViewById(R.id.btnAppFiles)
        btnAppNotes = findViewById(R.id.btnAppNotes)
        btnAppCalc = findViewById(R.id.btnAppCalc)
        btnAppTasks = findViewById(R.id.btnAppTasks)
        btnAppSet1 = findViewById(R.id.btnAppSet1)
        btnAppSet2 = findViewById(R.id.btnAppSet2)
        btnAppSet3 = findViewById(R.id.btnAppSet3)
        btnAppSet4 = findViewById(R.id.btnAppSet4)
        btnAppSet5 = findViewById(R.id.btnAppSet5)
        btnAppWindows = findViewById(R.id.btnAppWindows)
        btnWeb1 = findViewById(R.id.btnWeb1)
        btnWeb2 = findViewById(R.id.btnWeb2)
        btnWeb3 = findViewById(R.id.btnWeb3)
        btnWeb4 = findViewById(R.id.btnWeb4)
        btnWeb5 = findViewById(R.id.btnWeb5)
        btnWebSet1 = findViewById(R.id.btnWebSet1)
        btnWebSet2 = findViewById(R.id.btnWebSet2)
        btnWebSet3 = findViewById(R.id.btnWebSet3)
        btnWebSet4 = findViewById(R.id.btnWebSet4)
        btnWebSet5 = findViewById(R.id.btnWebSet5)
        btnSlidesStart = findViewById(R.id.btnSlidesStart)
        btnSlidesPrevious = findViewById(R.id.btnSlidesPrevious)
        btnSlidesNext = findViewById(R.id.btnSlidesNext)
        btnSlidesBlack = findViewById(R.id.btnSlidesBlack)
        btnSlidesExit = findViewById(R.id.btnSlidesExit)
        btnQuickShortcut1 = findViewById(R.id.btnQuickShortcut1)
        btnQuickShortcut2 = findViewById(R.id.btnQuickShortcut2)
        btnQuickShortcut3 = findViewById(R.id.btnQuickShortcut3)
        btnQuickShortcut4 = findViewById(R.id.btnQuickShortcut4)
        btnQuickShortcut5 = findViewById(R.id.btnQuickShortcut5)
        btnQuickShortcutSet1 = findViewById(R.id.btnQuickShortcutSet1)
        btnQuickShortcutSet2 = findViewById(R.id.btnQuickShortcutSet2)
        btnQuickShortcutSet3 = findViewById(R.id.btnQuickShortcutSet3)
        btnQuickShortcutSet4 = findViewById(R.id.btnQuickShortcutSet4)
        btnQuickShortcutSet5 = findViewById(R.id.btnQuickShortcutSet5)
        btnWebBack = findViewById(R.id.btnWebBack)
        btnWebForward = findViewById(R.id.btnWebForward)
        btnWebRefresh = findViewById(R.id.btnWebRefresh)
        btnWebNewTab = findViewById(R.id.btnWebNewTab)
        btnWebCloseTab = findViewById(R.id.btnWebCloseTab)
        btnWebPrevTab = findViewById(R.id.btnWebPrevTab)
        btnWebNextTab = findViewById(R.id.btnWebNextTab)
        btnWebPageUp = findViewById(R.id.btnWebPageUp)
        btnWebPageDown = findViewById(R.id.btnWebPageDown)
        btnWebFullscreen = findViewById(R.id.btnWebFullscreen)
        edtClipboardText = findViewById(R.id.edtClipboardText)
        btnClipboardCopy = findViewById(R.id.btnClipboardCopy)

        txtStatus.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnSettingsIcon.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnAboutIcon.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        cardEyeProtector.setOnClickListener { toggleEyeProtector() }
        setupMouseControls()
        setupKeyboardControls()
        setupShortcutSlots()
        setupClipboardControls()
        setupMediaControls()
        setupAppShortcuts()
        setupWebShortcuts()
        setupPresentationControls()
        setupRemoteModes()

        refreshHeaderInfo()
        refreshValueLabels()

        selectedTimerAction = getSharedPreferences(prefsName, MODE_PRIVATE)
            .getString(keyTimerAction, "sleep") ?: "sleep"
        nightIntensity = DesklyPrefs.getEyeIntensity(this)
        refreshHeaderInfo()
        refreshValueLabels()

        renderConnectionStatus()
        applyPrimaryMode(PrimaryMode.BRIGHTNESS)
        updateNightButtonIcon()
        updateMuteIcon()
        applyPowerPlanUi()

        // =========================
        // Primary slider = (EXTERNAL brightness) alebo (NIGHT intensity)
        // =========================
        seekPrimary.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || ignorePrimarySeek) return
                val p = progress.coerceIn(0, 100)
                refreshValueLabels()

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
                if (nightEnabled) nightIntensity = DesklyPrefs.getEyeIntensity(this)

                if (primaryMode == PrimaryMode.NIGHT_INTENSITY) {
                    ignorePrimarySeek = true
                    seekPrimary.progress = nightIntensity.coerceIn(0, 100)
                    ignorePrimarySeek = false
                    refreshValueLabels()
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
                nightIntensity = DesklyPrefs.getEyeIntensity(this)
                ignorePrimarySeek = true
                seekPrimary.progress = nightIntensity.coerceIn(0, 100)
                ignorePrimarySeek = false
                refreshValueLabels()
                scheduleNightSend(true, nightIntensity)
            }

            updateNightButtonIcon()
            refreshValueLabels()
        }

        // =========================
        // Internal slider = INTERNAL brightness (FAST)
        // =========================
        seekInternalBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || ignoreInternalSeek) return
                refreshValueLabels()
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
                refreshValueLabels()

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

        btnDisplayOff.setOnClickListener { sendDisplayControl("turn_off") }
        btnDisplayOn.setOnClickListener { sendDisplayControl("turn_on") }
        btnDisplayInternal.setOnClickListener { sendDisplayMode("internal") }
        btnDisplayDuplicate.setOnClickListener { sendDisplayMode("duplicate") }
        btnDisplayExtend.setOnClickListener { sendDisplayMode("extend") }
        btnDisplayExternal.setOnClickListener { sendDisplayMode("external") }

        btnPowerSaver.setOnClickListener { requestPowerPlan("power_saver") }
        btnPowerBalanced.setOnClickListener { requestPowerPlan("balanced") }
        btnPowerHigh.setOnClickListener { requestPowerPlan("high_performance") }

        btnTimerStart.text = "Start"
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
                put("fadeOutVolume", selectedTimerAction == "shutdown" && DesklyPrefs.getFadeOutShutdown(this@MainActivity))
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
            btnTimerStart.text = "Start"
            updateTimerStatusUi()
        }

        btnLock.setOnClickListener { sendPower(PowerProtocol.TYPE_LOCK) }
        btnSleep.setOnClickListener { confirmPower("Sleep PC?", "Sleep", PowerProtocol.TYPE_SLEEP) }
        btnRestart.setOnClickListener { confirmPower("Restart PC?", "Restart", PowerProtocol.TYPE_RESTART) }
        btnShutdown.setOnClickListener { confirmPower("Shut down PC?", "Shutdown", PowerProtocol.TYPE_SHUTDOWN) }

        updateUiState()
    }

    override fun onResume() {
        super.onResume()
        foregroundActive = true
        refreshPerformancePolicy()

        DesklyClient.addListener(this)

        selectedTimerAction = getSharedPreferences(prefsName, MODE_PRIVATE)
            .getString(keyTimerAction, "sleep") ?: "sleep"
        if (!nightEnabled) nightIntensity = DesklyPrefs.getEyeIntensity(this)
        refreshHeaderInfo()
        refreshValueLabels()
        updateTimerStatusUi()
        requestNotificationPermissionIfNeeded()

        ensureConnectedAndAuthed()

        renderConnectionStatus()
        updateUiState()

    }

    override fun onPause() {
        super.onPause()
        foregroundActive = false
        refreshPerformancePolicy()
        cancelReconnect()
        cancelMouseGestureTimers()
        cancelMouseDrag()
        stopTimerPolling()
        stopLocalTimerUi()
        pendingAppPickerSlot = null
        appPickerHandler.removeCallbacksAndMessages(null)
        actionStatusHandler.removeCallbacksAndMessages(null)
        DesklyClient.removeListener(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (foregroundActive && DesklyPrefs.getVolumeButtonMode(this) == "pc") {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    sendMediaAction("volume_up")
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    sendMediaAction("volume_down")
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refreshPerformancePolicy() {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        performanceDiagnostics = DesklyPrefs.getPerformanceDiagnostics(this)
        performancePolicy = PerformancePolicy(
            lowPowerEnabled = DesklyPrefs.getLowPowerMode(this),
            systemPowerSaveMode = powerManager?.isPowerSaveMode == true,
            foreground = foregroundActive
        )
        DesklyClient.configurePerformance(performancePolicy, performanceDiagnostics)
        if (performanceDiagnostics) {
            Log.d(
                "Deskly",
                "perf policy lowPower=${performancePolicy.lowPowerEnabled} " +
                    "systemSaver=${performancePolicy.systemPowerSaveMode} foreground=${performancePolicy.foreground} " +
                    "heartbeatMs=${performancePolicy.heartbeatMs} timerPollMs=${performancePolicy.timerPollMs}"
            )
        }
    }

    override fun onDestroy() {
        restoreBrightnessOnExitIfNeeded()
        super.onDestroy()
    }

    private fun restoreBrightnessOnExitIfNeeded() {
        if (isChangingConfigurations || restoreBrightnessSent) return
        if (!DesklyPrefs.getRestoreBrightnessOnExit(this)) return
        val token = getTokenRaw() ?: return
        if (!DesklyClient.state.authorized) return
        if (originalBrightnessCache.isEmpty()) return

        restoreBrightnessSent = true
        Log.d("Deskly", "Brightness restore count=${originalBrightnessCache.size} authorized=${DesklyClient.state.authorized}")
        originalBrightnessCache.forEach { (displayId, value) ->
            DesklyClient.sendSecure(
                "brightness_set",
                token,
                JSONObject()
                    .put("displayId", displayId)
                    .put("value", value.coerceIn(0, 100))
            )
        }
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
        refreshValueLabels()
    }

    private fun renderConnectionStatus() {
        val status = connectionStatusText(DesklyClient.state)
        txtStatus.text = status
        txtStatus.setBackgroundResource(
            when (status) {
                "Connected" -> R.drawable.bg_status_connected
                "Reconnecting" -> R.drawable.bg_status_warning
                "Pair Required" -> R.drawable.bg_status_warning
                "Auth Failed" -> R.drawable.bg_status_danger
                else -> R.drawable.bg_status_offline
            }
        )
    }

    private fun connectionStatusText(s: DesklyClient.State): String {
        val saved = DesklyPrefs.getSavedServer(this)
        return ConnectionStatusModel.build(
            state = s.copy(
                connecting = s.connecting || connectInFlight,
                authenticating = s.authenticating || authInFlight
            ),
            savedPcName = getSharedPreferences(prefsName, MODE_PRIVATE).getString(DesklyPrefs.KEY_DEVICE_NAME, null),
            savedIp = saved?.ip,
            savedPort = saved?.port,
            hasToken = !getTokenRaw().isNullOrBlank(),
            authRejected = isAuthRejected(s)
        ).status
    }

    private fun currentConnectionViewState(): ConnectionStatusModel.ViewState {
        val saved = DesklyPrefs.getSavedServer(this)
        return ConnectionStatusModel.build(
            state = DesklyClient.state,
            savedPcName = getSharedPreferences(prefsName, MODE_PRIVATE).getString(DesklyPrefs.KEY_DEVICE_NAME, null),
            savedIp = saved?.ip,
            savedPort = saved?.port,
            hasToken = !getTokenRaw().isNullOrBlank(),
            authRejected = isAuthRejected()
        )
    }

    private fun notificationPcName(): String {
        return currentConnectionViewState().pcName
    }

    private fun notificationConnectionType(): String {
        return when (currentConnectionViewState().connectionType) {
            ConnectionStatusModel.ConnectionType.BLUETOOTH -> "Bluetooth"
            ConnectionStatusModel.ConnectionType.LAN -> "LAN"
        }
    }

    private fun responseMessage(json: JSONObject, fallback: String): String {
        val msg = json.optString("message", "").trim()
        if (msg.isNotBlank()) {
            return when {
                msg.contains("unsupported", ignoreCase = true) -> "Unsupported"
                msg.contains("unavailable", ignoreCase = true) -> "Unsupported"
                msg.contains("unauthorized", ignoreCase = true) -> "Pair Required"
                else -> msg
            }
        }

        val data = json.optJSONObject("data")
        if (data?.optBoolean("supported", true) == false) return "Unsupported"
        return fallback
    }

    private fun updateTimerStatusUi() {
        val action = if (timerRunning) timerActionUi else selectedTimerAction
        val actionLabel = if (action == "shutdown") "Shutdown" else "Sleep"
        txtTimerStatus.text = if (timerRunning) {
            formatTime(timerRemainingSecondsUi)
        } else {
            "${timerMinutes} min"
        }
        if (this::txtTimerActionChip.isInitialized) txtTimerActionChip.text = actionLabel
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
            startTimerPolling()
        } else {
            timerRunning = false
            timerRemainingSecondsUi = 0
            stopTimerPolling()
            stopLocalTimerUi()
            btnTimerStart.text = "Start"
        }

        updateTimerStatusUi()
        updateUiState()
    }

    private fun sendPower(type: String) {
        if (!DesklyPrefs.getPowerActionsEnabled(this)) {
            toast("Power actions are disabled in Settings")
            return
        }
        val token = getToken() ?: return
        if (!DesklyClient.state.authorized) return
        val payload = PowerProtocol.payload(
            type,
            fadeOutVolume = DesklyPrefs.getFadeOutShutdown(this)
        )
        DesklyClient.sendSecure(type, token, payload)
    }

    private fun confirmPower(title: String, actionLabel: String, type: String) {
        if (!DesklyPrefs.getPowerActionsEnabled(this)) {
            toast("Power actions are disabled in Settings")
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(actionLabel) { _, _ -> sendPower(type) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.danger))
        }
        dialog.show()
    }

    private fun updateUiState() {
        val a = DesklyClient.state.authorized

        // keep icons consistent
        if (this::btnNight.isInitialized) updateNightButtonIcon()
        if (this::btnMute.isInitialized) updateMuteIcon()

        val hasExternal = !externalDisplayId.isNullOrBlank()
        val primaryAvailable = primaryMode == PrimaryMode.NIGHT_INTENSITY || hasExternal
        seekPrimary.isEnabled = a && !busy && primaryAvailable
        seekPrimary.alpha = if (a && primaryAvailable) 1.0f else 0.35f

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

        val mouseEnabled = a && !busy
        touchpadSurface.isEnabled = mouseEnabled
        btnMouseLeft.isEnabled = mouseEnabled
        btnMouseRight.isEnabled = mouseEnabled
        btnMouseDouble.isEnabled = mouseEnabled
        btnMouseDrag.isEnabled = mouseEnabled
        seekMouseSensitivity.isEnabled = true
        seekTouchpadScrollSensitivity.isEnabled = true
        switchTouchpadNaturalScroll.isEnabled = true
        switchTouchpadFeedback.isEnabled = true
        switchMouseAcceleration.isEnabled = true
        switchMouseLeftHanded.isEnabled = true
        btnTouchpadHelp.isEnabled = true
        val mouseAlpha = if (mouseEnabled) 1.0f else 0.35f
        touchpadSurface.alpha = mouseAlpha
        btnMouseLeft.alpha = mouseAlpha
        btnMouseRight.alpha = mouseAlpha
        btnMouseDouble.alpha = mouseAlpha
        btnMouseDrag.alpha = mouseAlpha
        updateMouseButtonLabels()

        val keyboardEnabled = a && !busy
        btnKeyboardSend.isEnabled = keyboardEnabled
        btnKeyboardSendChars.isEnabled = keyboardEnabled
        btnKeyboardVoice.isEnabled = true
        btnKeyEnter.isEnabled = keyboardEnabled
        btnKeyBackspace.isEnabled = keyboardEnabled
        btnKeyEsc.isEnabled = keyboardEnabled
        btnKeyTab.isEnabled = keyboardEnabled
        btnKeyUp.isEnabled = keyboardEnabled
        btnKeyLeft.isEnabled = keyboardEnabled
        btnKeyDown.isEnabled = keyboardEnabled
        btnKeyRight.isEnabled = keyboardEnabled
        btnShortcutCopy.isEnabled = keyboardEnabled
        btnShortcutPaste.isEnabled = keyboardEnabled
        btnShortcutSelectAll.isEnabled = keyboardEnabled
        btnShortcutAltTab.isEnabled = keyboardEnabled
        val keyboardAlpha = if (keyboardEnabled) 1.0f else 0.35f
        btnKeyboardSend.alpha = keyboardAlpha
        btnKeyboardSendChars.alpha = keyboardAlpha
        btnKeyboardVoice.alpha = 1.0f
        btnKeyEnter.alpha = keyboardAlpha
        btnKeyBackspace.alpha = keyboardAlpha
        btnKeyEsc.alpha = keyboardAlpha
        btnKeyTab.alpha = keyboardAlpha
        btnKeyUp.alpha = keyboardAlpha
        btnKeyLeft.alpha = keyboardAlpha
        btnKeyDown.alpha = keyboardAlpha
        btnKeyRight.alpha = keyboardAlpha
        btnShortcutCopy.alpha = keyboardAlpha
        btnShortcutPaste.alpha = keyboardAlpha
        btnShortcutSelectAll.alpha = keyboardAlpha
        btnShortcutAltTab.alpha = keyboardAlpha

        val quickShortcutButtons = arrayOf(btnQuickShortcut1, btnQuickShortcut2, btnQuickShortcut3, btnQuickShortcut4, btnQuickShortcut5)
        quickShortcutButtons.forEachIndexed { index, button ->
            val enabled = a && !busy
            button.isEnabled = enabled
            button.alpha = if (!enabled) 0.35f else if (shortcutSlotActions[index] != null) 1.0f else 0.72f
        }
        val quickShortcutSetButtons = arrayOf(
            btnQuickShortcutSet1,
            btnQuickShortcutSet2,
            btnQuickShortcutSet3,
            btnQuickShortcutSet4,
            btnQuickShortcutSet5
        )
        quickShortcutSetButtons.forEach { button ->
            button.isEnabled = a && !busy
            button.alpha = if (a && !busy) 0.78f else 0.35f
        }
        val clipboardEnabled = a && !busy && DesklyPrefs.getClipboardSyncEnabled(this)
        btnClipboardCopy.isEnabled = clipboardEnabled
        btnClipboardCopy.alpha = if (clipboardEnabled) 1.0f else 0.35f

        val mediaEnabled = a && !busy
        btnMediaPrevious.isEnabled = mediaEnabled
        btnMediaPlayPause.isEnabled = mediaEnabled
        btnMediaNext.isEnabled = mediaEnabled
        btnMediaVolumeDown.isEnabled = mediaEnabled
        btnMediaMute.isEnabled = mediaEnabled
        btnMediaVolumeUp.isEnabled = mediaEnabled
        btnMediaSeekBackward.isEnabled = mediaEnabled
        btnMediaFullscreen.isEnabled = mediaEnabled
        btnMediaSeekForward.isEnabled = mediaEnabled
        btnVideoList.isEnabled = mediaEnabled
        val mediaAlpha = if (mediaEnabled) 1.0f else 0.35f
        btnMediaPrevious.alpha = mediaAlpha
        btnMediaPlayPause.alpha = mediaAlpha
        btnMediaNext.alpha = mediaAlpha
        btnMediaVolumeDown.alpha = mediaAlpha
        btnMediaMute.alpha = mediaAlpha
        btnMediaVolumeUp.alpha = mediaAlpha
        btnMediaSeekBackward.alpha = mediaAlpha
        btnMediaFullscreen.alpha = mediaAlpha
        btnMediaSeekForward.alpha = mediaAlpha
        btnVideoList.alpha = mediaAlpha

        val appButtons = arrayOf(btnAppBrowser, btnAppFiles, btnAppNotes, btnAppCalc, btnAppTasks)
        val appSetButtons = arrayOf(btnAppSet1, btnAppSet2, btnAppSet3, btnAppSet4, btnAppSet5)
        appButtons.forEachIndexed { index, button ->
            val enabled = a && !busy
            button.isEnabled = enabled
            button.alpha = if (!enabled) 0.35f else if (appShortcutConfigured[index]) 1.0f else 0.72f
        }
        appSetButtons.forEach { button ->
            button.isEnabled = a && !busy
            button.alpha = if (a && !busy) 0.78f else 0.35f
        }
        btnAppWindows.isEnabled = a && !busy
        btnAppWindows.alpha = if (a && !busy) 1.0f else 0.35f

        val webButtons = arrayOf(btnWeb1, btnWeb2, btnWeb3, btnWeb4, btnWeb5)
        val webSetButtons = arrayOf(btnWebSet1, btnWebSet2, btnWebSet3, btnWebSet4, btnWebSet5)
        webButtons.forEachIndexed { index, button ->
            val enabled = a && !busy
            button.isEnabled = enabled
            button.alpha = if (!enabled) 0.35f else if (webShortcuts[index] != null) 1.0f else 0.72f
        }
        webSetButtons.forEach { button ->
            button.isEnabled = a && !busy
            button.alpha = if (a && !busy) 0.78f else 0.35f
        }
        val webRemoteButtons = arrayOf(
            btnWebBack,
            btnWebForward,
            btnWebRefresh,
            btnWebNewTab,
            btnWebCloseTab,
            btnWebPrevTab,
            btnWebNextTab,
            btnWebPageUp,
            btnWebPageDown,
            btnWebFullscreen
        )
        webRemoteButtons.forEach { button ->
            button.isEnabled = a && !busy
            button.alpha = if (a && !busy) 1.0f else 0.35f
        }

        val slidesEnabled = a && !busy
        val slideButtons = arrayOf(btnSlidesStart, btnSlidesPrevious, btnSlidesNext, btnSlidesBlack, btnSlidesExit)
        slideButtons.forEach { button ->
            button.isEnabled = slidesEnabled
            button.alpha = if (slidesEnabled) 1.0f else 0.35f
        }

        val displayEnabled = a && !busy
        btnDisplayOff.isEnabled = displayEnabled
        btnDisplayOn.isEnabled = displayEnabled
        btnDisplayInternal.isEnabled = displayEnabled
        btnDisplayDuplicate.isEnabled = displayEnabled
        btnDisplayExtend.isEnabled = displayEnabled
        btnDisplayExternal.isEnabled = displayEnabled
        val displayAlpha = if (displayEnabled) 1.0f else 0.35f
        btnDisplayOff.alpha = displayAlpha
        btnDisplayOn.alpha = displayAlpha
        btnDisplayInternal.alpha = displayAlpha
        btnDisplayDuplicate.alpha = displayAlpha
        btnDisplayExtend.alpha = displayAlpha
        btnDisplayExternal.alpha = displayAlpha

        val powerPlansAvailable = currentPowerPlan != "unsupported"
        btnPowerSaver.isEnabled = a && !busy && powerPlansAvailable
        btnPowerBalanced.isEnabled = a && !busy && powerPlansAvailable
        btnPowerHigh.isEnabled = a && !busy && powerPlansAvailable
        applyPowerPlanUi()
        if (!btnPowerSaver.isEnabled) btnPowerSaver.alpha = 0.35f
        if (!btnPowerBalanced.isEnabled) btnPowerBalanced.alpha = 0.35f
        if (!btnPowerHigh.isEnabled) btnPowerHigh.alpha = 0.35f

        btnTimerMinus.isEnabled = a && !busy && !timerRunning
        btnTimerPlus.isEnabled = a && !busy && !timerRunning
        btnTimerStart.isEnabled = a && !busy
        btnTimerCancel.isEnabled = a && !busy

        val powerActionsEnabled = a && !busy && DesklyPrefs.getPowerActionsEnabled(this)
        btnLock.isEnabled = powerActionsEnabled
        btnSleep.isEnabled = powerActionsEnabled
        btnRestart.isEnabled = powerActionsEnabled
        btnShutdown.isEnabled = powerActionsEnabled
        val powerActionsAlpha = if (powerActionsEnabled) 1.0f else 0.35f
        btnLock.alpha = powerActionsAlpha
        btnSleep.alpha = powerActionsAlpha
        btnRestart.alpha = powerActionsAlpha
        btnShutdown.alpha = powerActionsAlpha

        updateTimerStatusUi()
        refreshValueLabels()
    }

    private fun formatTime(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val mm = s / 60
        val ss = s % 60
        return String.format("%d:%02d", mm, ss)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun showActionStatus(text: String) {
        if (!this::txtActionStatus.isInitialized) return
        txtActionStatus.text = text
        txtActionStatus.visibility = View.VISIBLE
        actionStatusHandler.removeCallbacksAndMessages(null)
        actionStatusHandler.postDelayed({
            if (this::txtActionStatus.isInitialized) {
                txtActionStatus.visibility = View.GONE
            }
        }, 1600L)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!DesklyPrefs.getNotificationsEnabled(this)) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return

        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    // =========================
    // DesklyClient callbacks
    // =========================
    override fun onState(state: DesklyClient.State) {
        notifyConnectionTransition(previousConnectionState, state)
        previousConnectionState = state

        renderConnectionStatus()

        if (!state.connected || !state.authorized) {
            cancelMouseGestureTimers()
            cancelMouseDrag()
            twoFingerGestureActive = false
            pinchGestureActive = false
            touchpadGestureInterpreter.reset()
            clearQuietInFlight()
            stopTimerPolling()
            stopLocalTimerUi()
            timerRunning = false
            btnTimerStart.text = "Start"
            afterAuthDone = false
            currentPowerPlan = ""
            supportedPowerPlans.keys.forEach { supportedPowerPlans[it] = false }

            // keď padne conn/auth, display IDs už nemusia sedieť
            internalDisplayId = null
            externalDisplayId = null
            if (!isAuthRejected(state)) {
                scheduleReconnect()
            }
        }

        if (state.authorized && !afterAuthDone) {
            cancelReconnect()
            reconnectAttempts = 0
            afterAuthDone = true
            val token = getTokenRaw()
            if (!token.isNullOrBlank()) afterAuthInit(token)
        }

        updateUiState()

        if (state.connected && !state.authorized && !state.authenticating && !authInFlight && !isAuthRejected(state)) {
            ensureConnectedAndAuthed()
        }
    }

    private fun notifyConnectionTransition(previous: DesklyClient.State?, current: DesklyClient.State) {
        if (previous == null) return

        val pcName = notificationPcName()
        when {
            !previous.authorized && current.authorized -> {
                NotificationHelper.showConnected(this, pcName, notificationConnectionType())
            }
            previous.connected && !current.connected -> {
                NotificationHelper.showDisconnected(this, pcName)
            }
            !current.connected &&
                !current.lastError.isNullOrBlank() &&
                current.lastError != previous.lastError -> {
                NotificationHelper.showConnectionFailed(this, pcName, current.lastError)
            }
        }
    }

    override fun onLog(line: String) {
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

                "mouse_response" -> {
                    if (json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data")
                    val command = data?.optString("command", "mouse")?.ifBlank { "mouse" } ?: "mouse"
                    logMouseFailure(command, responseMessage(json, "Mouse command failed"))
                }

                "keyboard_response" -> {
                    if (json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data")
                    val command = data?.optString("command", "keyboard")?.ifBlank { "keyboard" } ?: "keyboard"
                    Log.w("Deskly", "Keyboard command failed: command=$command message=${responseMessage(json, "Keyboard failed")} authorized=${DesklyClient.state.authorized}")
                }

                "shortcut_response" -> {
                    if (json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data")
                    val action = data?.optString("action", "shortcut")?.ifBlank { "shortcut" } ?: "shortcut"
                    Log.w("Deskly", "Shortcut command failed: action=$action message=${responseMessage(json, "Shortcut failed")} authorized=${DesklyClient.state.authorized}")
                    showActionStatus("Shortcut failed")
                }

                "clipboard_response" -> {
                    if (json.optBoolean("ok", false)) {
                        showActionStatus("Copied")
                        return
                    }
                    Log.w("Deskly", "Clipboard command failed: message=${responseMessage(json, "Clipboard failed")} authorized=${DesklyClient.state.authorized}")
                    showActionStatus("Copy failed")
                }

                "media_response" -> {
                    if (json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data")
                    val command = data?.optString("command", "media")?.ifBlank { "media" } ?: "media"
                    Log.w("Deskly", "Media command failed: command=$command message=${responseMessage(json, "Media failed")} authorized=${DesklyClient.state.authorized}")
                }

                "video_list_response" -> {
                    val result = VideoProtocol.parseListResult(json.optJSONObject("data"))
                    showVideoList(result, responseMessage(json, "Automatic video detection is not available"))
                }

                "presentation_response" -> {
                    if (json.optBoolean("ok", false)) return
                    val data = json.optJSONObject("data")
                    val action = data?.optString("action", "presentation")?.ifBlank { "presentation" } ?: "presentation"
                    Log.w("Deskly", "Presentation command failed: action=$action message=${responseMessage(json, "Presentation failed")} authorized=${DesklyClient.state.authorized}")
                    showActionStatus("Command failed")
                }

                "app_response" -> {
                    val data = json.optJSONObject("data")
                    val command = data?.optString("command", "app")?.ifBlank { "app" } ?: "app"
                    if (json.optBoolean("ok", false)) {
                        if (command == "app_shortcuts_get") {
                            data?.optJSONArray("slots")?.let { applyAppShortcutSlots(it) }
                        } else if (command == "app_catalog_get") {
                            data?.optJSONArray("apps")?.let { applyAppCatalog(it) }
                        } else if (command == "app_shortcut_set") {
                            requestAppShortcuts()
                            showActionStatus("App set")
                        } else if (command == "app_open") {
                            showActionStatus("Opened")
                        } else if (command == AppWindowProtocol.TYPE_WINDOWS_GET) {
                            showAppWindowPicker(AppWindowProtocol.parseWindows(data))
                        } else if (command == AppWindowProtocol.TYPE_SWITCH) {
                            showActionStatus("Switched")
                        }
                        return
                    }

                    val slot = data?.optInt("slot", 0) ?: 0
                    Log.w("Deskly", "App shortcut failed: command=$command slot=$slot message=${responseMessage(json, "App failed")} authorized=${DesklyClient.state.authorized}")
                    showActionStatus("App failed")
                }

                "web_response" -> {
                    if (json.optBoolean("ok", false)) {
                        showActionStatus("Opened")
                        return
                    }
                    Log.w("Deskly", "Web command failed: message=${responseMessage(json, "Web failed")} authorized=${DesklyClient.state.authorized}")
                    showActionStatus("Web failed")
                }

                "response" -> {
                    val msg = json.optString("message", "")
                    if (!json.optBoolean("ok", true) && msg.contains("app_catalog_get", ignoreCase = true)) {
                        pendingAppPickerSlot = null
                        showActionStatus("Update host")
                    }
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
                            if (id.isNotEmpty() && v >= 0) {
                                val safe = v.coerceIn(0, 100)
                                brightnessCache[id] = safe
                                rememberOriginalBrightness(id, safe)
                            }
                        }
                    } else {
                        val v = data.optInt("value", -1)
                        if (did.isNotEmpty() && v >= 0) {
                            val safe = v.coerceIn(0, 100)
                            brightnessCache[did] = safe
                            rememberOriginalBrightness(did, safe)
                        }
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
                        val msg = responseMessage(json, "Quiet failed")
                        toast("Quiet Mode: $msg")
                        updateUiState()
                        return
                    }

                    val data = json.optJSONObject("data")
                    val enabled = data?.optBoolean("enabled", quietEnabled) ?: quietEnabled

                    applyQuietUi(enabled)
                    updateUiState()
                }

                "display_control_response", "display_mode_response" -> {
                    if (!json.optBoolean("ok", false)) {
                        toast(responseMessage(json, "Command failed"))
                    } else {
                        toast("Done")
                    }
                }

                "power_plan_response" -> {
                    val data = json.optJSONObject("data")
                    currentPowerPlan = data?.optString("plan", "")?.ifBlank { "" } ?: ""
                    if (!json.optBoolean("ok", false) && currentPowerPlan.isBlank()) {
                        currentPowerPlan = "unsupported"
                    }

                    supportedPowerPlans.keys.forEach { supportedPowerPlans[it] = false }
                    val plans = data?.optJSONArray("plans")
                    if (plans != null) {
                        for (i in 0 until plans.length()) {
                            val p = plans.optJSONObject(i) ?: continue
                            val id = p.optString("id", "")
                            if (supportedPowerPlans.containsKey(id)) {
                                supportedPowerPlans[id] = p.optBoolean("supported", false)
                            }
                        }
                    }

                    if (!json.optBoolean("ok", true)) {
                        toast(responseMessage(json, "Command failed"))
                    }

                    applyPowerPlanUi()
                    updateUiState()
                }

                "power_response" -> {
                    toast(if (json.optBoolean("ok", false)) "Done" else responseMessage(json, "Command failed"))
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

                        when (type) {
                            "internal" -> if (internalDisplayId == null) internalDisplayId = id
                            "external" -> if (externalDisplayId == null) externalDisplayId = id
                        }
                    }
                    applyCachedBrightnessToUi()
                    updateUiState()
                }
            }
        } catch (_: Exception) {}
    }

    private fun afterAuthInit(token: String) {
        DesklyClient.sendSecure("volume_get", token)
        DesklyClient.sendSecure("display_list", token)
        DesklyClient.sendSecure("brightness_get", token, JSONObject().put("displayId", "all"))
        DesklyClient.sendSecure("night_get", token)
        DesklyClient.sendSecure("quiet_get", token)
        DesklyClient.sendSecure("power_plan_get", token)
        DesklyClient.sendSecure("app_shortcuts_get", token)
        DesklyClient.sendSecure("app_catalog_get", token)
        DesklyClient.sendSecure("sleep_timer_status", token)
    }
}
