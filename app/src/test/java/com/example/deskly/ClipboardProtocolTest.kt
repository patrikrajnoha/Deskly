package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardProtocolTest {
    @Test
    fun emptyClipboardTextDoesNotCreatePayload() {
        assertNull(ClipboardProtocol.payload(""))
    }

    @Test
    fun clipboardPayloadTruncatesLargeText() {
        val text = "a".repeat(ClipboardProtocol.MAX_TEXT_LENGTH + 5)
        val payload = ClipboardProtocol.payload(text)!!

        assertEquals(ClipboardProtocol.MAX_TEXT_LENGTH, payload.getString("text").length)
    }
}
