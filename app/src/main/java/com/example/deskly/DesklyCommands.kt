package com.example.deskly

object DesklyCommands {
    enum class Group {
        PAIRING,
        DISPLAY,
        AUDIO,
        NIGHT,
        QUIET,
        POWER,
        TIMER,
        MOUSE,
        KEYBOARD,
        SHORTCUT,
        MEDIA,
        VIDEO,
        CLIPBOARD,
        APP,
        WEB,
        PRESENTATION
    }

    enum class AuthRequirement {
        NONE,
        TOKEN
    }

    data class Spec(
        val type: String,
        val responseType: String,
        val group: Group,
        val auth: AuthRequirement = AuthRequirement.TOKEN,
        val legacy: Boolean = false
    )

    val all: List<Spec> = listOf(
        Spec("pair_status", "pair_status_response", Group.PAIRING, AuthRequirement.NONE),
        Spec("pair_request", "pair_response", Group.PAIRING, AuthRequirement.NONE),
        Spec("auth_request", "auth_response", Group.PAIRING, AuthRequirement.NONE),
        Spec("ping_secure", "response", Group.PAIRING),
        Spec("display_list", "display_list_response", Group.DISPLAY),
        Spec("display_control", "display_control_response", Group.DISPLAY),
        Spec("display_mode_set", "display_mode_response", Group.DISPLAY),
        Spec("capabilities_external_brightness", "capabilities_external_brightness_response", Group.DISPLAY),
        Spec("brightness_get", "brightness_response", Group.DISPLAY),
        Spec("brightness_set", "brightness_response", Group.DISPLAY),
        Spec("volume_get", "audio_response", Group.AUDIO),
        Spec("volume_set", "audio_response", Group.AUDIO),
        Spec("mute_toggle", "audio_response", Group.AUDIO),
        Spec("night_get", "night_response", Group.NIGHT),
        Spec("night_set", "night_response", Group.NIGHT),
        Spec("quiet_get", "quiet_response", Group.QUIET),
        Spec("quiet_set", "quiet_response", Group.QUIET),
        Spec("power_plan_get", "power_plan_response", Group.POWER),
        Spec("power_plan_set", "power_plan_response", Group.POWER),
        Spec(PowerProtocol.TYPE_LOCK, "power_response", Group.POWER),
        Spec(PowerProtocol.TYPE_SLEEP, "power_response", Group.POWER),
        Spec(PowerProtocol.TYPE_SHUTDOWN, "power_response", Group.POWER),
        Spec(PowerProtocol.TYPE_RESTART, "power_response", Group.POWER),
        Spec("sleep_timer_set", "sleep_timer_response", Group.TIMER),
        Spec("sleep_timer_cancel", "sleep_timer_response", Group.TIMER),
        Spec("sleep_timer_status", "sleep_timer_status_response", Group.TIMER),
        Spec(MouseProtocol.TYPE_MOVE, "mouse_response", Group.MOUSE),
        Spec(MouseProtocol.TYPE_CLICK, "mouse_response", Group.MOUSE),
        Spec(MouseProtocol.TYPE_SCROLL, "mouse_response", Group.MOUSE),
        Spec(MouseProtocol.TYPE_BUTTON, "mouse_response", Group.MOUSE),
        Spec(KeyboardProtocol.TYPE_TEXT, "keyboard_response", Group.KEYBOARD),
        Spec(KeyboardProtocol.TYPE_KEY, "keyboard_response", Group.KEYBOARD),
        Spec(KeyboardProtocol.TYPE_SHORTCUT, "keyboard_response", Group.KEYBOARD),
        Spec(ShortcutProtocol.TYPE, "shortcut_response", Group.SHORTCUT),
        Spec(MediaProtocol.TYPE, "media_response", Group.MEDIA),
        Spec("media_key", "media_response", Group.MEDIA, legacy = true),
        Spec(VideoProtocol.TYPE_LIST, VideoProtocol.RESPONSE_TYPE, Group.VIDEO),
        Spec(ClipboardProtocol.TYPE_SET, "clipboard_response", Group.CLIPBOARD),
        Spec("app_shortcuts_get", "app_response", Group.APP),
        Spec("app_catalog_get", "app_response", Group.APP),
        Spec("app_shortcut_set", "app_response", Group.APP),
        Spec("app_open", "app_response", Group.APP),
        Spec(AppWindowProtocol.TYPE_WINDOWS_GET, "app_response", Group.APP),
        Spec(AppWindowProtocol.TYPE_SWITCH, "app_response", Group.APP),
        Spec(WebProtocol.TYPE_OPEN, "web_response", Group.WEB),
        Spec("presentation_action", "presentation_response", Group.PRESENTATION)
    )

    private val byType = all.associateBy { it.type }

    fun specFor(type: String?): Spec? =
        byType[normalizeType(type)]

    fun isKnown(type: String?): Boolean =
        specFor(type) != null

    fun responseTypeFor(type: String?): String =
        specFor(type)?.responseType ?: "response"

    fun requiresToken(type: String?): Boolean =
        specFor(type)?.auth == AuthRequirement.TOKEN

    fun isPrivileged(type: String?): Boolean {
        val spec = specFor(type) ?: return false
        return spec.auth == AuthRequirement.TOKEN && spec.group in setOf(
            Group.POWER,
            Group.CLIPBOARD,
            Group.APP,
            Group.WEB,
            Group.MEDIA,
            Group.VIDEO,
            Group.KEYBOARD,
            Group.MOUSE,
            Group.SHORTCUT,
            Group.PRESENTATION
        )
    }

    fun normalizeType(type: String?): String =
        type.orEmpty().trim().lowercase()
}
