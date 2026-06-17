package com.example.deskly

import org.json.JSONObject
import java.net.URI

object WebProtocol {
    const val TYPE_OPEN = "web_open"
    private const val MAX_URL_LENGTH = 2000

    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim().take(MAX_URL_LENGTH)
        if (trimmed.isBlank()) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host

        if (scheme != "http" && scheme != "https") return null
        if (host.isNullOrBlank()) return null

        return withScheme
    }

    fun defaultLabel(url: String): String {
        val normalized = normalizeUrl(url) ?: return "Website"
        val host = runCatching { URI(normalized).host }.getOrNull().orEmpty()
        return host
            .removePrefix("www.")
            .take(32)
            .ifBlank { "Website" }
    }

    fun payload(url: String): JSONObject? {
        val normalized = normalizeUrl(url) ?: return null
        return JSONObject().put("url", normalized)
    }
}
