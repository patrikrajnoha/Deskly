package com.example.deskly

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AppWindowProtocolTest {
    @Test
    fun switchPayloadTrimsWindowId() {
        assertEquals("ABC123", AppWindowProtocol.switchPayload(" ABC123 ").getString("windowId"))
    }

    @Test
    fun parseWindowsSkipsItemsWithoutIdOrTitle() {
        val data = JSONObject().put(
            "windows",
            JSONArray()
                .put(JSONObject().put("id", "1").put("title", "Browser").put("appName", "Chrome"))
                .put(JSONObject().put("id", "").put("title", "No ID"))
                .put(JSONObject().put("id", "2").put("title", ""))
        )

        val windows = AppWindowProtocol.parseWindows(data)

        assertEquals(1, windows.size)
        assertEquals("1", windows.single().id)
        assertEquals("Browser", windows.single().title)
        assertEquals("Chrome", windows.single().appName)
    }
}
