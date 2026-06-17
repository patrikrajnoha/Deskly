package com.example.deskly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebProtocolTest {
    @Test
    fun normalizeUrlPrefixesBareDomainsWithHttps() {
        assertEquals("https://example.com", WebProtocol.normalizeUrl("example.com"))
    }

    @Test
    fun normalizeUrlAllowsHttpAndHttpsOnly() {
        assertEquals("http://example.com/path", WebProtocol.normalizeUrl("http://example.com/path"))
        assertEquals("https://example.com/path", WebProtocol.normalizeUrl("https://example.com/path"))

        assertNull(WebProtocol.normalizeUrl("file:///C:/secret.txt"))
        assertNull(WebProtocol.normalizeUrl("javascript:alert(1)"))
        assertNull(WebProtocol.normalizeUrl("ftp://example.com/file"))
    }

    @Test
    fun normalizeUrlRequiresHost() {
        assertNull(WebProtocol.normalizeUrl("https:///missing-host"))
        assertNull(WebProtocol.normalizeUrl(""))
    }

    @Test
    fun defaultLabelUsesHostWithoutWww() {
        assertEquals("example.com", WebProtocol.defaultLabel("https://www.example.com/path"))
    }

    @Test
    fun payloadContainsNormalizedUrl() {
        assertEquals("https://example.com", WebProtocol.payload("example.com")!!.getString("url"))
    }
}
