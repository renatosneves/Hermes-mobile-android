package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.data.ws.toJsonElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.concurrent.ConcurrentHashMap

/** Bot pictures from the server's avatar store, shared by the Bots home and group rooms. */
object BotAvatarCache {
    private val images = ConcurrentHashMap<String, String>()

    /** When a picture last failed to load; it isn't asked for again until [RETRY_AFTER_MS] passes. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** Server the cached pictures came from; profile names like "default" repeat across servers. */
    @Volatile private var scope: String? = null

    /** Which server we're talking to; overridable in tests. */
    internal var currentScope: () -> String = { runCatching { AuthManager.baseUrl() }.getOrDefault("") }

    @Synchronized
    private fun ensureScope() {
        val now = currentScope()
        if (now != scope) {
            images.clear()
            failedAt.clear()
            scope = now
        }
    }

    fun snapshot(): Map<String, String> {
        ensureScope()
        return images.toMap()
    }

    fun put(
        name: String,
        image: String?,
    ) {
        ensureScope()
        if (image == null) images.remove(name) else images[name] = image
    }

    /** Clock for the retry back-off; overridable in tests. */
    internal var now: () -> Long = System::currentTimeMillis

    /**
     * Pictures for [profiles], fetching any the server has that we don't yet, a few at a time.
     * A picture that failed is left alone for a while instead of being asked for every refresh.
     */
    suspend fun load(profiles: List<ProfileInfo>): Map<String, String> {
        ensureScope()
        val wanted =
            profiles.filter { profile ->
                if (profile.has_avatar == false) images.remove(profile.name)
                profile.has_avatar == true &&
                    !images.containsKey(profile.name) &&
                    failedAt[profile.name]?.let { now() - it < RETRY_AFTER_MS } != true
            }
        val gate = Semaphore(MAX_PARALLEL)
        coroutineScope {
            wanted.forEach { profile ->
                launch {
                    gate.withPermit {
                        val image = fetch(profile.name)
                        if (image != null) {
                            images[profile.name] = image
                            failedAt.remove(profile.name)
                        } else {
                            failedAt[profile.name] = now()
                        }
                    }
                }
            }
        }
        return snapshot()
    }

    private suspend fun fetch(name: String): String? =
        runCatching {
            val result =
                HermesWsClient
                    .request(
                        WsMethods.PROFILES_GET_ASSET,
                        mapOf("name" to name, "asset" to "avatar"),
                        timeoutMs = METADATA_TIMEOUT_MS,
                        suppressErrorEvent = true,
                    ).await()
            val obj = (result as? JsonObject) ?: (result?.toJsonElement() as? JsonObject)
            val found = (obj?.get("found") as? JsonPrimitive)?.booleanOrNull == true
            (obj?.get("data") as? JsonPrimitive)?.content?.takeIf { found && it.isNotBlank() }
        }.getOrNull()

    private const val MAX_PARALLEL = 3
    private const val RETRY_AFTER_MS = 5 * 60_000L
}
