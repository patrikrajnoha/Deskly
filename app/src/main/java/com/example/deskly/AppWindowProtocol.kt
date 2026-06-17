package com.example.deskly

import org.json.JSONArray
import org.json.JSONObject

object AppWindowProtocol {
    const val TYPE_WINDOWS_GET = "app_windows_get"
    const val TYPE_SWITCH = "app_switch"

    data class WindowItem(
        val id: String,
        val title: String,
        val appName: String
    )

    fun switchPayload(windowId: String): JSONObject =
        JSONObject().put("windowId", windowId.trim())

    fun parseWindows(data: JSONObject?): List<WindowItem> {
        val windows = data?.optJSONArray("windows") ?: JSONArray()
        return buildList {
            for (index in 0 until windows.length()) {
                val item = windows.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isBlank() || title.isBlank()) continue
                add(
                    WindowItem(
                        id = id,
                        title = title,
                        appName = item.optString("appName").trim()
                    )
                )
            }
        }
    }
}
