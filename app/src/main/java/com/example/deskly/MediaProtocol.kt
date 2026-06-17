package com.example.deskly

import org.json.JSONObject

object MediaProtocol {
    const val TYPE = "media_action"

    val supportedActions = listOf(
        "play_pause",
        "previous",
        "next",
        "volume_down",
        "volume_up",
        "mute",
        "seek_backward",
        "seek_forward",
        "fullscreen"
    )

    fun normalizeAction(action: String): String =
        action.trim().lowercase().replace("-", "_").replace(" ", "_").let {
            when (it) {
                "play", "pause", "media_play_pause" -> "play_pause"
                "prev", "media_previous", "previous_track" -> "previous"
                "media_next", "next_track" -> "next"
                "vol_down" -> "volume_down"
                "vol_up" -> "volume_up"
                "volume_mute", "media_mute" -> "mute"
                "rewind", "backward" -> "seek_backward"
                "forward" -> "seek_forward"
                else -> it
            }
        }

    fun isSupported(action: String): Boolean =
        normalizeAction(action) in supportedActions

    fun payload(action: String): JSONObject =
        JSONObject().put("action", normalizeAction(action))
}
