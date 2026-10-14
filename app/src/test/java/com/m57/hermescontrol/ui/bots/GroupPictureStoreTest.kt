package com.m57.hermescontrol.ui.bots

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupPictureStoreTest {
    @After
    fun clean() {
        GroupPictureStore.remove("Team")
    }

    @Test
    fun setGetRemoveWithoutInit() {
        assertNull(GroupPictureStore.get("Team"))
        GroupPictureStore.set("Team", "data:image/png;base64,AAAA")
        assertEquals("data:image/png;base64,AAAA", GroupPictureStore.get("Team"))
        GroupPictureStore.remove("Team")
        assertNull(GroupPictureStore.get("Team"))
    }
}
