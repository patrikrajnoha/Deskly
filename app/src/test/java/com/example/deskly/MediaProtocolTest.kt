package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProtocolTest {
    @Test
    fun normalizesLegacyMediaNamesToSemanticActions() {
        assertEquals("play_pause", MediaProtocol.normalizeAction("media-play-pause"))
        assertEquals("previous", MediaProtocol.normalizeAction("media_previous"))
        assertEquals("next", MediaProtocol.normalizeAction("next_track"))
        assertEquals("mute", MediaProtocol.normalizeAction("volume_mute"))
        assertEquals("seek_backward", MediaProtocol.normalizeAction("rewind"))
    }

    @Test
    fun exposesSupportedMediaActionsOnly() {
        assertTrue(MediaProtocol.isSupported("play_pause"))
        assertTrue(MediaProtocol.isSupported("volume_up"))
        assertTrue(MediaProtocol.isSupported("fullscreen"))
        assertFalse(MediaProtocol.isSupported("launch_random_app"))
    }
}
