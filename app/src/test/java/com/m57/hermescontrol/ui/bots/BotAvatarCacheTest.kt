package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.ws.HermesWsClient
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotAvatarCacheTest {
    private val original = BotAvatarCache.currentScope

    private val originalClock = BotAvatarCache.now

    @After
    fun restore() {
        BotAvatarCache.currentScope = original
        BotAvatarCache.now = originalClock
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

    @Test
    fun `a picture that failed isn't asked for again until the back-off passes`() =
        runTest {
            BotAvatarCache.currentScope = { "https://backoff.example/" }
            var clock = 1_000_000L
            BotAvatarCache.now = { clock }
            mockkObject(HermesWsClient)
            try {
                every { HermesWsClient.request(any(), any(), any(), any()) } answers {
                    CompletableDeferred<Any?>().apply { completeExceptionally(IllegalStateException("timed out")) }
                }
                val bots = listOf(ProfileInfo(name = "slow", has_avatar = true))
                BotAvatarCache.load(bots)
                BotAvatarCache.load(bots)
                verify(exactly = 1) { HermesWsClient.request(any(), any(), any(), any()) }
                clock += 6 * 60_000L
                BotAvatarCache.load(bots)
                verify(exactly = 2) { HermesWsClient.request(any(), any(), any(), any()) }
            } finally {
                unmockkObject(HermesWsClient)
            }
        }
}
