package com.example.deskly

import org.json.JSONObject

object MouseProtocol {
    const val TYPE_MOVE = "mouse_move"
    const val TYPE_CLICK = "mouse_click"
    const val TYPE_SCROLL = "mouse_scroll"
    const val TYPE_BUTTON = "mouse_button"

    fun movePayload(dx: Int, dy: Int): JSONObject =
        JSONObject()
            .put("dx", dx.coerceIn(-5000, 5000))
            .put("dy", dy.coerceIn(-5000, 5000))

    fun clickPayload(button: String, clicks: Int = 1): JSONObject =
        JSONObject()
            .put("button", normalizeButton(button))
            .put("clicks", clicks.coerceIn(1, 3))

    fun scrollPayload(deltaY: Int, deltaX: Int = 0): JSONObject =
        JSONObject()
            .put("deltaX", deltaX.coerceIn(-10, 10))
            .put("deltaY", deltaY.coerceIn(-10, 10))

    fun buttonPayload(button: String, down: Boolean): JSONObject =
        JSONObject()
            .put("button", normalizeButton(button))
            .put("down", down)

    fun normalizeButton(button: String): String {
        return when (button.trim().lowercase()) {
            "right", "secondary" -> "right"
            "middle" -> "middle"
            else -> "left"
        }
    }
}
