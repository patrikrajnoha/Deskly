package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Test

class MouseProtocolTest {
    @Test
    fun normalizesMouseButtons() {
        assertEquals("right", MouseProtocol.normalizeButton("secondary"))
        assertEquals("middle", MouseProtocol.normalizeButton("middle"))
        assertEquals("left", MouseProtocol.normalizeButton("unknown"))
    }

    @Test
    fun exposesDistinctMouseCommandTypes() {
        assertEquals("mouse_move", MouseProtocol.TYPE_MOVE)
        assertEquals("mouse_click", MouseProtocol.TYPE_CLICK)
        assertEquals("mouse_scroll", MouseProtocol.TYPE_SCROLL)
        assertEquals("mouse_button", MouseProtocol.TYPE_BUTTON)
    }
}
