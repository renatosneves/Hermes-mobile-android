package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.data.ws.toJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.concurrent.ConcurrentHashMap

/** Bot pictures from the server's avatar store, shared by the Bots home and group rooms. */
object BotAvatarCache {
    private val images = ConcurrentHashMap<String, String>()

    fun snapshot(): Map<String, String> = images.toMap()

    fun put(
        name: String,
        image: String?,
    ) {
        if (image == null) images.remove(name) else images[name] = image
    }

    /** Pictures for [profiles], fetching any the server has that we don't yet. */
    suspend fun load(profiles: List<ProfileInfo>): Map<String, String> {
        for (profile in profiles) {
            if (profile.has_avatar == false) images.remove(profile.name)
            if (profile.has_avatar != true || images.containsKey(profile.name)) continue
            fetch(profile.name)?.let { images[profile.name] = it }
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
                        suppressErrorEvent = true,
                    ).await()
            val obj = (result as? JsonObject) ?: (result?.toJsonElement() as? JsonObject)
            val found = (obj?.get("found") as? JsonPrimitive)?.booleanOrNull == true
            (obj?.get("data") as? JsonPrimitive)?.content?.takeIf { found && it.isNotBlank() }
        }.getOrNull()
}
