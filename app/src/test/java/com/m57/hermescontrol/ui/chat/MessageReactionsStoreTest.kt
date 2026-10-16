package com.m57.hermescontrol.ui.chat

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageReactionsStoreTest {
    private fun msg(
        content: String = "hi",
        rowId: Long? = null,
        restId: String? = null,
    ) = ChatMessage(
        id = "local-id",
        role = MessageRole.ASSISTANT,
        content = content,
        timestamp = 42L,
        serverRowId = rowId,
        restId = restId,
    )

    @Test
    fun prefersServerRowId() {
        assertEquals("s1:r7", likeKey("s1", msg(rowId = 7, restId = "x")))
    }

    @Test
    fun fallsBackToRestId() {
        assertEquals("s1:cabc", likeKey("s1", msg(restId = "abc")))
    }

    @Test
    fun fallsBackToTimestampAndContentHash() {
        assertEquals("s1:t42:${"hi".hashCode()}", likeKey("s1", msg()))
    }

    @Test
    fun scopedBySession() {
        assertNotEquals(likeKey("a", msg(rowId = 1)), likeKey("b", msg(rowId = 1)))
    }

    private class FakePrefs(
        val data: MutableMap<String, Set<String>> = mutableMapOf(),
    ) {
        val prefs: SharedPreferences = mockk()

        init {
            val key = slot<String>()
            every { prefs.getStringSet(capture(key), any()) } answers
                { data[key.captured] ?: secondArg<Set<String>?>() }
            every { prefs.contains(any()) } answers { firstArg<String>() in data }
            every { prefs.edit() } answers { editor() }
        }

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Set<String>?>()
            val editor = mockk<SharedPreferences.Editor>()
            every { editor.putStringSet(any(), any()) } answers {
                pending[firstArg()] = secondArg<Set<String>?>()
                editor
            }
            every { editor.remove(any()) } answers {
                pending[firstArg()] = null
                editor
            }
            every { editor.apply() } answers {
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            }
            return editor
        }
    }

    @Test
    fun tapbackReplacesAndRemoves() {
        val store = MessageReactionsStore(FakePrefs().prefs)
        store.set("k", "👍")
        store.set("k", "❤️")
        assertEquals("❤️", store.reactionFor("k"))
        assertEquals(mapOf("k" to "❤️"), store.reactions.value)
        store.set("k", null)
        assertNull(store.reactionFor("k"))
    }

    @Test
    fun ignoresUnknownEmoji() {
        val store = MessageReactionsStore(FakePrefs().prefs)
        store.set("k", "🤖")
        assertNull(store.reactionFor("k"))
    }

    @Test
    fun migratesLegacyLikes() {
        val prefs = FakePrefs(mutableMapOf("keys" to setOf("s:r1", "s:r2")))
        val store = MessageReactionsStore(prefs.prefs)
        assertEquals(mapOf("s:r1" to "👍", "s:r2" to "👍"), store.reactions.value)
        assertEquals(false, prefs.data.containsKey("keys"))
        assertEquals(mapOf("s:r1" to "👍", "s:r2" to "👍"), MessageReactionsStore(prefs.prefs).reactions.value)
    }

    @Test
    fun persistsRoundTrip() {
        val prefs = FakePrefs()
        MessageReactionsStore(prefs.prefs).apply {
            set("a", "😂")
            set("b", "✅")
        }
        assertEquals(mapOf("a" to "😂", "b" to "✅"), MessageReactionsStore(prefs.prefs).reactions.value)
    }
}
