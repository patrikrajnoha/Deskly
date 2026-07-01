package com.example.deskly

import android.content.Context

object DesklyPrefs {
    private const val PREFS_NAME = "deskly_prefs"

    const val KEY_IP = "server_ip"
    const val KEY_PORT = "server_port"
    const val KEY_DEVICE = "server_device_key"
    const val KEY_DEVICE_RAW = "server_device_raw_id"
    const val KEY_DEVICE_NAME = "server_device_name"
    const val KEY_EYE_MODE = "eye_protector_mode"
    const val KEY_EYE_INTENSITY = "eye_protector_intensity"
    const val KEY_SCREEN_DIM = "eye_protector_screen_dim"
    const val KEY_FADE_OUT_SHUTDOWN = "fade_out_volume_shutdown"
    const val KEY_AUTO_CONNECT = "auto_connect"
    const val KEY_MOUSE_SENSITIVITY = "mouse_sensitivity"
    const val KEY_TOUCHPAD_SCROLL_SENSITIVITY = "touchpad_scroll_sensitivity"
    const val KEY_TOUCHPAD_NATURAL_SCROLL = "touchpad_natural_scroll"
    const val KEY_TOUCHPAD_VISUAL_FEEDBACK = "touchpad_visual_feedback"
    const val KEY_MOUSE_ACCELERATION = "mouse_acceleration"
    const val KEY_MOUSE_LEFT_HANDED = "mouse_left_handed"
    const val KEY_GYRO_SENSITIVITY = "gyro_sensitivity"
    const val KEY_RESTORE_BRIGHTNESS_ON_EXIT = "restore_brightness_on_exit"
    const val KEY_LOW_POWER_MODE = "low_power_mode"
    const val KEY_PERFORMANCE_DIAGNOSTICS = "performance_diagnostics"
    const val KEY_CLIPBOARD_SYNC_ENABLED = "clipboard_sync_enabled"
    const val KEY_POWER_ACTIONS_ENABLED = "power_actions_enabled"
    const val KEY_VOLUME_BUTTON_MODE = "volume_button_mode"
    const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

    private const val LEGACY_KEY_TOKEN = "auth_token"
    private const val DEFAULT_EYE_MODE = "night_shift"
    private const val DEFAULT_EYE_INTENSITY = 60
    private const val DEFAULT_SCREEN_DIM = 20
    private const val DEFAULT_CLIPBOARD_SYNC_ENABLED = false
    private const val DEFAULT_POWER_ACTIONS_ENABLED = true
    private const val DEFAULT_VOLUME_BUTTON_MODE = "phone"
    private const val DEFAULT_NOTIFICATIONS_ENABLED = true

    data class SavedServer(
        val ip: String,
        val port: Int,
        val deviceKey: String,
        val rawId: String?
    )

    data class WebShortcut(
        val name: String,
        val url: String
    )

    data class EyeMode(
        val id: String,
        val name: String,
        val kelvin: Int,
        val icon: String,
        val special: Boolean = false
    ) {
        val displayText: String
            get() = "$name - ${kelvin}K"
    }

    val eyeModes = listOf(
        EyeMode("candlelight", "Candlelight", 1800, ""),
        EyeMode("dawn", "Dawn", 2000, ""),
        EyeMode("incandescent", "Incandescent", 2700, ""),
        EyeMode("night_shift", "Night Shift", 3200, ""),
        EyeMode("forest", "Forest", 3300, ""),
        EyeMode("fluorescent", "Fluorescent", 3400, ""),
        EyeMode("sunlight", "Sunlight", 4500, ""),
        EyeMode("eclipse", "Eclipse / Deep Red", 500, "", special = true)
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun tokenKey(deviceKey: String) = "auth_token__${deviceKey}"

    fun getDeviceKey(context: Context): String {
        val p = prefs(context)
        val saved = p.getString(KEY_DEVICE, null)
        if (!saved.isNullOrBlank()) return saved

        val ip = p.getString(KEY_IP, null)?.trim().orEmpty()
        val port = p.getString(KEY_PORT, "5050")?.trim().orEmpty()
        return "manual_${ip}:${port}"
    }

    fun getSavedServer(context: Context): SavedServer? {
        val p = prefs(context)
        val ip = p.getString(KEY_IP, "")?.trim().orEmpty()
        val port = p.getString(KEY_PORT, "5050")?.trim().orEmpty().toIntOrNull()
        if (ip.isBlank() || port == null || port !in 1..65535) return null

        return SavedServer(
            ip = ip,
            port = port,
            deviceKey = getDeviceKey(context),
            rawId = p.getString(KEY_DEVICE_RAW, null)?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    fun saveManualServer(context: Context, ip: String, port: Int, keepRawIdForMigration: Boolean) {
        val edit = prefs(context).edit()
            .putString(KEY_IP, ip)
            .putString(KEY_PORT, port.toString())
            .putString(KEY_DEVICE, "manual_${ip}:${port}")

        if (!keepRawIdForMigration) edit.remove(KEY_DEVICE_RAW)
        edit.apply()
    }

    fun saveBluetoothDevice(context: Context, address: String, name: String?) {
        val safeAddress = address.trim().uppercase()
        prefs(context).edit()
            .putString(KEY_IP, "")
            .putString(KEY_PORT, "5050")
            .putString(KEY_DEVICE, BluetoothProtocol.deviceKey(safeAddress))
            .putString(KEY_DEVICE_RAW, safeAddress)
            .putString(KEY_DEVICE_NAME, name?.trim()?.take(80)?.takeIf { it.isNotBlank() } ?: "Bluetooth PC")
            .apply()
    }

    fun getToken(context: Context): String? {
        val p = prefs(context)
        val deviceKey = getDeviceKey(context)

        p.getString(tokenKey(deviceKey), null)?.let { token ->
            if (token.isNotBlank()) return token
        }

        val rawId = p.getString(KEY_DEVICE_RAW, null)?.trim().orEmpty()
        if (rawId.isNotBlank()) {
            val rawToken = p.getString(tokenKey(rawId), null)
            if (!rawToken.isNullOrBlank()) {
                p.edit()
                    .putString(tokenKey(deviceKey), rawToken)
                    .remove(tokenKey(rawId))
                    .apply()
                return rawToken
            }
        }

        val ip = p.getString(KEY_IP, null)?.trim().orEmpty()
        val port = p.getString(KEY_PORT, "5050")?.trim().orEmpty()
        val manualKey = "manual_${ip}:${port}"
        if (ip.isNotBlank() && manualKey != deviceKey) {
            val manualToken = p.getString(tokenKey(manualKey), null)
            if (!manualToken.isNullOrBlank()) {
                p.edit()
                    .putString(tokenKey(deviceKey), manualToken)
                    .apply()
                return manualToken
            }
        }

        val legacy = p.getString(LEGACY_KEY_TOKEN, null)
        if (!legacy.isNullOrBlank()) {
            p.edit()
                .putString(tokenKey(deviceKey), legacy)
                .remove(LEGACY_KEY_TOKEN)
                .apply()
            return legacy
        }

        return null
    }

    fun setTokenForCurrentDevice(context: Context, token: String?) {
        val p = prefs(context)
        val key = tokenKey(getDeviceKey(context))
        val edit = p.edit()
        if (token.isNullOrBlank()) edit.remove(key) else edit.putString(key, token)
        edit.apply()
    }

    fun hasToken(context: Context) = !getToken(context).isNullOrBlank()

    fun getEyeMode(context: Context): EyeMode {
        val id = prefs(context).getString(KEY_EYE_MODE, DEFAULT_EYE_MODE) ?: DEFAULT_EYE_MODE
        return eyeModes.firstOrNull { it.id == id } ?: eyeModes.first { it.id == DEFAULT_EYE_MODE }
    }

    fun setEyeMode(context: Context, modeId: String) {
        val safeId = eyeModes.firstOrNull { it.id == modeId }?.id ?: DEFAULT_EYE_MODE
        prefs(context).edit().putString(KEY_EYE_MODE, safeId).apply()
    }

    fun getEyeIntensity(context: Context): Int {
        return prefs(context).getInt(KEY_EYE_INTENSITY, DEFAULT_EYE_INTENSITY).coerceIn(0, 100)
    }

    fun setEyeIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_EYE_INTENSITY, value.coerceIn(0, 100)).apply()
    }

    fun getScreenDim(context: Context): Int {
        return prefs(context).getInt(KEY_SCREEN_DIM, DEFAULT_SCREEN_DIM).coerceIn(0, 100)
    }

    fun setScreenDim(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SCREEN_DIM, value.coerceIn(0, 100)).apply()
    }

    fun getFadeOutShutdown(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_FADE_OUT_SHUTDOWN, true)
    }

    fun setFadeOutShutdown(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FADE_OUT_SHUTDOWN, enabled).apply()
    }

    fun getAutoConnect(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_CONNECT, true)
    }

    fun setAutoConnect(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun getMouseSensitivity(context: Context): Int {
        return prefs(context).getInt(KEY_MOUSE_SENSITIVITY, TouchpadSettings.DEFAULT_CURSOR_SPEED_PERCENT)
            .coerceIn(TouchpadSettings.MIN_PERCENT, TouchpadSettings.MAX_PERCENT)
    }

    fun setMouseSensitivity(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_MOUSE_SENSITIVITY, value.coerceIn(TouchpadSettings.MIN_PERCENT, TouchpadSettings.MAX_PERCENT))
            .apply()
    }

    fun getTouchpadSettings(context: Context): TouchpadSettings {
        val p = prefs(context)
        return TouchpadSettings(
            cursorSpeedPercent = p.getInt(KEY_MOUSE_SENSITIVITY, TouchpadSettings.DEFAULT_CURSOR_SPEED_PERCENT),
            scrollSensitivityPercent = p.getInt(KEY_TOUCHPAD_SCROLL_SENSITIVITY, TouchpadSettings.DEFAULT_SCROLL_SENSITIVITY_PERCENT),
            naturalScroll = p.getBoolean(KEY_TOUCHPAD_NATURAL_SCROLL, TouchpadSettings.DEFAULT_NATURAL_SCROLL),
            visualFeedback = p.getBoolean(KEY_TOUCHPAD_VISUAL_FEEDBACK, TouchpadSettings.DEFAULT_VISUAL_FEEDBACK),
            acceleration = p.getBoolean(KEY_MOUSE_ACCELERATION, TouchpadSettings.DEFAULT_ACCELERATION),
            leftHanded = p.getBoolean(KEY_MOUSE_LEFT_HANDED, TouchpadSettings.DEFAULT_LEFT_HANDED),
            gyroSensitivityPercent = p.getInt(KEY_GYRO_SENSITIVITY, TouchpadSettings.DEFAULT_GYRO_SENSITIVITY_PERCENT)
        ).normalized()
    }

    fun setTouchpadScrollSensitivity(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_TOUCHPAD_SCROLL_SENSITIVITY, value.coerceIn(TouchpadSettings.MIN_PERCENT, TouchpadSettings.MAX_PERCENT))
            .apply()
    }

    fun setTouchpadNaturalScroll(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOUCHPAD_NATURAL_SCROLL, enabled).apply()
    }

    fun setTouchpadVisualFeedback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOUCHPAD_VISUAL_FEEDBACK, enabled).apply()
    }

    fun setMouseAcceleration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MOUSE_ACCELERATION, enabled).apply()
    }

    fun setMouseLeftHanded(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MOUSE_LEFT_HANDED, enabled).apply()
    }

    fun setGyroSensitivity(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_GYRO_SENSITIVITY, value.coerceIn(TouchpadSettings.MIN_PERCENT, TouchpadSettings.MAX_PERCENT))
            .apply()
    }

    fun getRestoreBrightnessOnExit(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_RESTORE_BRIGHTNESS_ON_EXIT, false)
    }

    fun setRestoreBrightnessOnExit(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RESTORE_BRIGHTNESS_ON_EXIT, enabled).apply()
    }

    fun getLowPowerMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_LOW_POWER_MODE, false)
    }

    fun setLowPowerMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOW_POWER_MODE, enabled).apply()
    }

    fun getPerformanceDiagnostics(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PERFORMANCE_DIAGNOSTICS, false)
    }

    fun setPerformanceDiagnostics(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERFORMANCE_DIAGNOSTICS, enabled).apply()
    }

    fun getClipboardSyncEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CLIPBOARD_SYNC_ENABLED, DEFAULT_CLIPBOARD_SYNC_ENABLED)
    }

    fun setClipboardSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLIPBOARD_SYNC_ENABLED, enabled).apply()
    }

    fun getPowerActionsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_POWER_ACTIONS_ENABLED, DEFAULT_POWER_ACTIONS_ENABLED)
    }

    fun setPowerActionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_POWER_ACTIONS_ENABLED, enabled).apply()
    }

    fun getVolumeButtonMode(context: Context): String {
        val saved = prefs(context).getString(KEY_VOLUME_BUTTON_MODE, DEFAULT_VOLUME_BUTTON_MODE)
        return normalizeVolumeButtonMode(saved)
    }

    fun setVolumeButtonMode(context: Context, mode: String) {
        prefs(context).edit()
            .putString(KEY_VOLUME_BUTTON_MODE, normalizeVolumeButtonMode(mode))
            .apply()
    }

    fun normalizeVolumeButtonMode(mode: String?): String {
        return if (mode == "pc") "pc" else DEFAULT_VOLUME_BUTTON_MODE
    }

    fun getNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getWebShortcut(context: Context, slot: Int): WebShortcut? {
        if (slot !in 1..5) return null
        val p = prefs(context)
        val name = p.getString(webNameKey(slot), null)?.trim().orEmpty()
        val url = p.getString(webUrlKey(slot), null)?.trim().orEmpty()
        if (name.isBlank() || url.isBlank()) return null
        return WebShortcut(name, url)
    }

    fun setWebShortcut(context: Context, slot: Int, name: String, url: String) {
        if (slot !in 1..5) return
        prefs(context).edit()
            .putString(webNameKey(slot), name.trim().take(32))
            .putString(webUrlKey(slot), url.trim().take(2000))
            .apply()
    }

    fun clearWebShortcut(context: Context, slot: Int) {
        if (slot !in 1..5) return
        prefs(context).edit()
            .remove(webNameKey(slot))
            .remove(webUrlKey(slot))
            .apply()
    }

    private fun webNameKey(slot: Int) = "web_shortcut_name_$slot"
    private fun webUrlKey(slot: Int) = "web_shortcut_url_$slot"
}
