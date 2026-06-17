package com.example.deskly

import org.json.JSONArray
import org.json.JSONObject

object KeyboardProtocol {
    const val TYPE_TEXT = "keyboard_text"
    const val TYPE_KEY = "keyboard_key"
    const val TYPE_SHORTCUT = "keyboard_shortcut"
    const val TYPE_SHORTCUT_ACTION = ShortcutProtocol.TYPE
    const val MAX_TEXT_CHARS = 2000

    fun textPayload(text: String): JSONObject =
        JSONObject().put("text", text.take(MAX_TEXT_CHARS))

    fun keyPayload(key: String, presses: Int = 1): JSONObject =
        JSONObject()
            .put("key", normalizeKey(key))
            .put("presses", presses.coerceIn(1, 10))

    fun shortcutPayload(keys: Iterable<String>): JSONObject {
        val arr = JSONArray()
        keys.map(::normalizeKey)
            .filter { it.isNotBlank() }
            .take(4)
            .forEach { arr.put(it) }
        return JSONObject().put("keys", arr)
    }

    fun shortcutActionPayload(action: String): JSONObject =
        JSONObject().put("action", ShortcutProtocol.normalizeAction(action))

    fun normalizeKey(key: String): String =
        key.trim().lowercase().replace("-", "_").replace(" ", "_")

    fun textChunks(text: String, perCharacter: Boolean): List<String> {
        val capped = text.take(MAX_TEXT_CHARS)
        if (!perCharacter || capped.isEmpty()) return if (capped.isEmpty()) emptyList() else listOf(capped)

        val chunks = mutableListOf<String>()
        var index = 0
        while (index < capped.length) {
            val codePoint = capped.codePointAt(index)
            chunks += String(Character.toChars(codePoint))
            index += Character.charCount(codePoint)
        }
        return chunks
    }
}
