package com.m57.hermescontrol.ui.bots

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotAvatarCacheTest {
    private val original = BotAvatarCache.currentScope

    @After
    fun restore() {
        BotAvatarCache.currentScope = original
    }

    @Test
    fun `switching server drops the previous server's pictures`() {
        var server = "https://a.example/"
        BotAvatarCache.currentScope = { server }
        BotAvatarCache.put("default", "data:image/jpeg;base64,AAA")
        assertEquals(mapOf("default" to "data:image/jpeg;base64,AAA"), BotAvatarCache.snapshot())
        server = "https://b.example/"
        assertTrue(BotAvatarCache.snapshot().isEmpty())
    }
}
