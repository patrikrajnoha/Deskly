package com.example.deskly

import org.json.JSONObject

object ClipboardProtocol {
    const val TYPE_SET = "clipboard_set"
    const val MAX_TEXT_LENGTH = 10_000

    fun normalizeText(text: String): String =
        text.take(MAX_TEXT_LENGTH)

    fun payload(text: String): JSONObject? {
        val normalized = normalizeText(text)
        if (normalized.isEmpty()) return null
        return JSONObject().put("text", normalized)
    }
}
