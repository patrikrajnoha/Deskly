package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardProtocolTest {
    @Test
    fun unicodeTextCanBeSentAsBlock() {
        val chunks = KeyboardProtocol.textChunks("ľščťžýáíé €", perCharacter = false)

        assertEquals(listOf("ľščťžýáíé €"), chunks)
    }

    @Test
    fun unicodeTextCanBeSentPerCharacterWithoutBreakingSurrogatePairs() {
        val chunks = KeyboardProtocol.textChunks("A😀ľ", perCharacter = true)

        assertEquals(listOf("A", "😀", "ľ"), chunks)
    }

    @Test
    fun textIsCappedBeforeSending() {
        val chunks = KeyboardProtocol.textChunks("x".repeat(KeyboardProtocol.MAX_TEXT_CHARS + 50), perCharacter = false)

        assertEquals(KeyboardProtocol.MAX_TEXT_CHARS, chunks.single().length)
    }

    @Test
    fun specialKeysNormalizeForPcHost() {
        assertEquals("page_down", KeyboardProtocol.normalizeKey(" Page-Down "))
        assertEquals("arrow_left", KeyboardProtocol.normalizeKey("Arrow Left"))
    }

    @Test
    fun keyboardModeUsesShortcutProtocolActions() {
        assertEquals(ShortcutProtocol.TYPE, KeyboardProtocol.TYPE_SHORTCUT_ACTION)
        assertTrue(
            ShortcutProtocol.mappingFor("close_window", ShortcutProtocol.Platform.WINDOWS)
                ?.keys
                ?.isNotEmpty() == true
        )
    }
}
