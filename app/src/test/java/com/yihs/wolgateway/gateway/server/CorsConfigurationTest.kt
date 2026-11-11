package com.yihs.wolgateway.gateway.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorsConfigurationTest {
    @Test
    fun parsesCommaSeparatedFullOrigins() {
        assertEquals(
            listOf("http://localhost:9999", "https://app.example.com"),
            parseCorsOrigins("http://localhost:9999, https://app.example.com"),
        )
    }

    @Test
    fun rejectsMissingSchemePathAndWildcard() {
        assertNull(parseCorsOrigins("localhost:9999"))
        assertNull(parseCorsOrigins("https://app.example.com/dashboard"))
        assertNull(parseCorsOrigins("https://*.example.com"))
    }

    @Test
    fun allowsAnEmptyProductionList() {
        assertEquals(emptyList<String>(), parseCorsOrigins(""))
    }
}
