package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class DesklyProtocolTest {
    @Test
    fun currentProtocolVersionIsStable() {
        assertEquals(1, DesklyProtocol.VERSION)
    }

    @Test
    fun protocolVersionValidationIsBackwardAware() {
        assertTrue(DesklyProtocol.isSupportedVersion(null))
        assertTrue(DesklyProtocol.isSupportedVersion(1))
        assertFalse(DesklyProtocol.isSupportedVersion(99))
    }

    @Test
    fun requestSerializationAlwaysIncludesProtocolVersionAndObjectPayload() {
        val request = DesklyProtocol.request("media_action", JSONObject().put("action", "play_pause"))

        assertEquals("media_action", request.getString("type"))
        assertEquals(1, request.getInt("protocolVersion"))
        assertEquals("play_pause", request.getJSONObject("payload").getString("action"))
    }

    @Test
    fun knownCommandRegistryCoversCurrentSemanticAndLegacyCommands() {
        assertTrue(DesklyProtocol.isKnownRequestType("media_action"))
        assertTrue(DesklyProtocol.isKnownRequestType("shortcut_action"))
        assertTrue(DesklyProtocol.isKnownRequestType("media_key"))
        assertEquals("media_response", DesklyProtocol.responseTypeFor("media_key"))
        assertTrue(DesklyProtocol.isKnownRequestType(VideoProtocol.TYPE_LIST))
        assertEquals(VideoProtocol.RESPONSE_TYPE, DesklyProtocol.responseTypeFor(VideoProtocol.TYPE_LIST))
    }

    @Test
    fun unknownCommandsUseGenericResponseFallback() {
        assertFalse(DesklyProtocol.isKnownRequestType("launch_missiles"))
        assertEquals("response", DesklyProtocol.responseTypeFor("launch_missiles"))
        assertFalse(DesklyProtocol.requiresToken("launch_missiles"))
        assertFalse(DesklyProtocol.isPrivileged("launch_missiles"))
    }

    @Test
    fun privilegedCommandsRequireTokenButPairingCommandsDoNot() {
        assertFalse(DesklyProtocol.requiresToken("pair_request"))
        assertFalse(DesklyProtocol.isPrivileged("pair_request"))

        assertTrue(DesklyProtocol.requiresToken("clipboard_set"))
        assertTrue(DesklyProtocol.isPrivileged("clipboard_set"))
        assertTrue(DesklyProtocol.isPrivileged("power_shutdown"))
        assertTrue(DesklyProtocol.isPrivileged("app_open"))
        assertTrue(DesklyProtocol.isPrivileged(VideoProtocol.TYPE_LIST))
    }

    @Test
    fun everyRegisteredCommandHasAStableResponseType() {
        DesklyCommands.all.forEach { spec ->
            assertTrue(spec.type.isNotBlank())
            assertTrue("${spec.type} response missing", spec.responseType.isNotBlank())
            assertEquals(spec.responseType, DesklyProtocol.responseTypeFor(spec.type))
        }
    }
}
