package com.m57.hermescontrol.ui.bots

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BotsSizeStoreTest {
    @After
    fun restore() {
        BotsSizeStore.set(BotsSizeStore.DEFAULT)
    }

    @Test
    fun defaultIsLarge() {
        assertEquals(1.12f, BotsSizeStore.scale, 0f)
    }

    @Test
    fun setChangesScale() {
        BotsSizeStore.set(1.25f)
        assertEquals(1.25f, BotsSizeStore.scale, 0f)
    }
}
