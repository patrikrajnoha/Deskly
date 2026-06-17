package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutProtocolTest {
    @Test
    fun normalizesShortcutActionForTransport() {
        assertEquals("zoom_in", ShortcutProtocol.normalizeAction(" Zoom-In "))
        assertEquals("zoom_out", ShortcutProtocol.normalizeAction("zoom out"))
    }

    @Test
    fun identifiesZoomActions() {
        assertTrue(ShortcutProtocol.isZoomAction("zoom_in"))
        assertTrue(ShortcutProtocol.isZoomAction("zoom-reset"))
        assertFalse(ShortcutProtocol.isZoomAction("refresh"))
    }

    @Test
    fun rejectsUnsupportedShortcutActionsBeforeTransport() {
        assertTrue(ShortcutProtocol.isSupported("refresh"))
        assertFalse(ShortcutProtocol.isSupported("open_random_file"))
    }

    @Test
    fun shortcutPayloadUsesNormalizedAction() {
        assertEquals("new_tab", ShortcutProtocol.payload("New Tab").getString("action"))
    }

    @Test
    fun everySupportedShortcutHasWindowsAndMacMappings() {
        ShortcutProtocol.supportedActions.forEach { action ->
            assertTrue(
                "${action.id} missing Windows mapping",
                ShortcutProtocol.mappingFor(action.id, ShortcutProtocol.Platform.WINDOWS) != null
            )
            assertTrue(
                "${action.id} missing macOS mapping",
                ShortcutProtocol.mappingFor(action.id, ShortcutProtocol.Platform.MACOS) != null
            )
        }
    }

    @Test
    fun browserMappingsCoverChromeFirefoxAndOperaCompatibleActionsOnWindows() {
        assertEquals(
            listOf("alt", "left"),
            ShortcutProtocol.mappingFor("browser_back", ShortcutProtocol.Platform.WINDOWS)?.keys
        )
        assertEquals(
            listOf("alt", "right"),
            ShortcutProtocol.mappingFor("browser_forward", ShortcutProtocol.Platform.WINDOWS)?.keys
        )
        assertEquals(
            listOf("ctrl", "tab"),
            ShortcutProtocol.mappingFor("next_tab", ShortcutProtocol.Platform.WINDOWS)?.keys
        )
        assertEquals(
            listOf("ctrl", "shift", "tab"),
            ShortcutProtocol.mappingFor("previous_tab", ShortcutProtocol.Platform.WINDOWS)?.keys
        )
        assertEquals(
            listOf("page_down"),
            ShortcutProtocol.mappingFor("page_scroll_down", ShortcutProtocol.Platform.WINDOWS)?.keys
        )
    }

    @Test
    fun browserSpecificMappingsReuseStableShortcutInfrastructure() {
        val actions = listOf(
            "browser_back",
            "browser_forward",
            "refresh",
            "new_tab",
            "close_tab",
            "next_tab",
            "previous_tab",
            "page_scroll_up",
            "page_scroll_down",
            "fullscreen"
        )

        ShortcutProtocol.Browser.values().forEach { browser ->
            actions.forEach { action ->
                assertTrue(
                    "$browser should map $action",
                    ShortcutProtocol.mappingForBrowser(action, browser, ShortcutProtocol.Platform.WINDOWS) != null
                )
            }
        }
    }

    @Test
    fun zoomWindowsMappingsUseWheelButMacMappingsUseCommandKeys() {
        assertEquals(
            1,
            ShortcutProtocol.mappingFor("zoom_in", ShortcutProtocol.Platform.WINDOWS)?.wheelSteps
        )
        assertEquals(
            -1,
            ShortcutProtocol.mappingFor("zoom_out", ShortcutProtocol.Platform.WINDOWS)?.wheelSteps
        )
        assertEquals(
            listOf("cmd", "plus"),
            ShortcutProtocol.mappingFor("zoom_in", ShortcutProtocol.Platform.MACOS)?.keys
        )
    }
}
