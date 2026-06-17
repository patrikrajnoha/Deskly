package com.example.deskly

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoProtocolTest {
    @Test
    fun refreshPayloadUsesStableAction() {
        val payload = VideoProtocol.refreshPayload()

        assertEquals("refresh", payload.getString("action"))
    }

    @Test
    fun unsupportedFallbackDoesNotAdvertiseVideoDetection() {
        val fallback = VideoProtocol.unsupportedFallback()

        assertFalse(fallback.getBoolean("supported"))
        assertEquals(0, fallback.getJSONArray("videos").length())
        assertEquals("media_remote", fallback.getString("fallback"))
    }

    @Test
    fun parseListResultKeepsMetadataAndFallback() {
        val data = JSONObject()
            .put("supported", true)
            .put("fallback", "media_remote")
            .put(
                "videos",
                JSONArray().put(
                    JSONObject()
                        .put("id", "browser-1")
                        .put("title", "Demo")
                        .put("source", "Chrome")
                        .put("playbackState", "playing")
                        .put("controllable", true)
                )
            )

        val result = VideoProtocol.parseListResult(data)

        assertTrue(result.supported)
        assertEquals("media_remote", result.fallback)
        assertEquals(1, result.videos.size)
        assertEquals("Chrome", result.videos.single().source)
        assertTrue(result.videos.single().controllable)
    }

    @Test
    fun parseMissingDataFallsBackToMediaRemote() {
        val result = VideoProtocol.parseListResult(null)

        assertFalse(result.supported)
        assertEquals(emptyList<VideoProtocol.VideoItem>(), result.videos)
        assertEquals("media_remote", result.fallback)
    }
}
