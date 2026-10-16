package com.m57.hermescontrol.notification

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.ProfileContext
import com.m57.hermescontrol.data.model.CronInboxDelivery
import com.m57.hermescontrol.data.model.CronInboxResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.HermesApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.security.MessageDigest

class CronInboxPersistenceTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val api = mockk<HermesApiService>()
    private val values = mutableMapOf<String, Any?>()
    private val connection = MutableStateFlow<ProfileContext?>(ProfileContext("http://gateway:18788", "test", "owner"))
    private val delivery =
        CronInboxDelivery(
            1,
            "receipt",
            "builder",
            "builder-chat",
            "Synthetic alert",
            System.currentTimeMillis() / 1000.0,
            12,
        )

    @Before
    fun setup() {
        mockkObject(AuthManager, ApiClient)
        every { AuthManager.contextFlow } returns connection
        every { AuthManager.isNotifyNewBotMessages() } returns false
        every { ApiClient.hermesApi } returns api
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getLong(any(), any()) } answers { values[firstArg()] as? Long ?: secondArg() }
        every { prefs.getString(any(), any()) } answers { values[firstArg()] as? String ?: secondArg() }
        every { prefs.getStringSet(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (values[firstArg()] as? Set<String>) ?: secondArg()
        }
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } answers {
            values[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.putStringSet(any(), any()) } answers {
            values[firstArg()] = secondArg<Set<String>>()
            editor
        }
        every { editor.commit() } returns true
        coEvery { api.getCronInbox(0, any()) } returns
            Response.success(CronInboxResponse("epoch", 1, deliveries = listOf(delivery)))
        coEvery { api.getCronInbox(1, any()) } returns Response.success(CronInboxResponse("epoch", 1))
    }

    @After
    fun teardown() = unmockkAll()

    @Test
    fun restartUsesCommittedCursorAndLegacyCheckDoesNotRepeatCron() =
        runBlocking {
            CronInboxSync.syncOnce(context)
            CronInboxSync.syncOnce(context)
            coVerify(exactly = 1) { api.getCronInbox(0, any()) }
            coVerify(exactly = 1) { api.getCronInbox(1, any()) }
            assertTrue(CronInboxSync.wasDelivered(context, "builder", "builder-chat", 12, delivery.created))
            assertFalse(CronInboxSync.wasDelivered(context, "research", "builder-chat", 12, delivery.created))
            assertFalse(CronInboxSync.wasDelivered(context, "builder", "other-chat", 12, delivery.created))
            assertFalse(CronInboxSync.wasDelivered(context, "builder", "builder-chat", 12, delivery.created + 1))
        }

    @Test
    fun refusedNotificationDoesNotConsumeDelivery() =
        runBlocking {
            every { AuthManager.isNotifyNewBotMessages() } returns true
            val manager = mockk<NotificationManager>()
            every { context.getSystemService(NotificationManager::class.java) } returns manager
            every { manager.areNotificationsEnabled() } returns false
            CronInboxSync.syncOnce(context)
            CronInboxSync.syncOnce(context)
            coVerify(exactly = 2) { api.getCronInbox(0, any()) }
            assertTrue(values.isEmpty())
        }

    @Test
    fun connectionSwitchDuringFetchCannotAdvanceOrPublish() =
        runBlocking {
            coEvery { api.getCronInbox(0, any()) } answers {
                connection.value = ProfileContext("http://other:18788", "test2", "other")
                Response.success(CronInboxResponse("epoch", 1, deliveries = listOf(delivery)))
            }
            CronInboxSync.syncOnce(context)
            assertTrue(values.isEmpty())
        }

    @Test
    fun paginatedInboxAdvancesAfterEverySuccessfulPage() =
        runBlocking {
            coEvery { api.getCronInbox(0, any()) } returns
                Response.success(
                    CronInboxResponse("epoch", 1, has_more = true, deliveries = listOf(delivery)),
                )
            coEvery { api.getCronInbox(1, any()) } returns Response.success(CronInboxResponse("epoch", 2))
            CronInboxSync.syncOnce(context)
            assertEquals(2L, values.values.filterIsInstance<Long>().single())
        }

    @Test
    fun postedReceiptSurvivesCrashBeforePageCursorCommit() =
        runBlocking {
            every { AuthManager.isNotifyNewBotMessages() } returns true
            val key =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest("http://gateway:18788".toByteArray())
                    .joinToString("") { "%02x".format(it) }
            values["$key.posted"] = setOf("builder|receipt")
            // NotificationManager is deliberately unstubbed: reposting would fail this test.
            CronInboxSync.syncOnce(context)
            assertEquals(1L, values["$key.cursor"])
            assertTrue(CronInboxSync.wasDelivered(context, "builder", "builder-chat", 12, delivery.created))
        }

    @Test
    fun changedDatabaseEpochRefetchesFromTheBeginning() =
        runBlocking {
            val key =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest("http://gateway:18788".toByteArray())
                    .joinToString("") { "%02x".format(it) }
            values["$key.cursor"] = 999L
            values["$key.epoch"] = "previous-database"
            coEvery { api.getCronInbox(999, any()) } returns Response.success(CronInboxResponse("epoch", 1))
            CronInboxSync.syncOnce(context)
            coVerify(exactly = 1) { api.getCronInbox(999, any()) }
            coVerify(exactly = 1) { api.getCronInbox(0, any()) }
            assertEquals(1L, values["$key.cursor"])
        }
}
