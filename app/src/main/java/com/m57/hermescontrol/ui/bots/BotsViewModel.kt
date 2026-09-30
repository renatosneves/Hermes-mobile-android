package com.m57.hermescontrol.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.BotAvatarMeta
import com.m57.hermescontrol.data.model.BotRosterMeta
import com.m57.hermescontrol.data.model.CreateProfileRequest
import com.m57.hermescontrol.data.model.GroupChatRoomMeta
import com.m57.hermescontrol.data.model.GroupChatSyncSnapshot
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.ProfilesResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.session.ProfileSwitchCoordinator
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.data.ws.toJsonElement
import com.m57.hermescontrol.ui.common.ToastHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement

enum class BotsTab {
    BOTS,
    GROUPS,
}

data class GroupInfo(
    val name: String,
    val members: List<ProfileInfo>,
    val lastActivity: Long = 0L,
)

data class BotsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val profiles: List<ProfileInfo> = emptyList(),
    val activeProfileName: String? = null,
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val hiddenProfiles: Set<String> = emptySet(),
    val selectedTab: BotsTab = BotsTab.BOTS,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    /** Bot pictures from the server's avatar store, by bot name (data URLs). */
    val avatars: Map<String, String> = emptyMap(),
    /** Bots with a live session waiting on you (approval or question). */
    val needsYou: Set<String> = emptySet(),
    /** Message counts at the time you last opened each bot. */
    val seenCounts: Map<String, Int> = emptyMap(),
    /** Latest line of each bot's conversation, Telegram style, by bot name. */
    val previews: Map<String, String> = emptyMap(),
) {
    /** The row's preview: the fetched latest message, else the roster's short excerpt. */
    fun previewFor(profile: ProfileInfo): String =
        previews[profile.name]
            ?: profile.canonical_session
                ?.preview
                ?.let(BotsPresentation::previewText)
                .orEmpty()

    /** The picture to show for a bot: the server's avatar store first, then an older inline image. */
    fun imageFor(profile: ProfileInfo): String? =
        avatars[profile.name]
            ?: profile
                .botMeta()
                ?.avatar
                ?.image_url
                ?.takeIf { it.isNotBlank() }

    val hasHiddenBots: Boolean
        get() = profiles.any { it.isHidden || it.name in hiddenProfiles }

    val allGroups: List<GroupInfo>
        get() {
            val defaultProfile =
                profiles.find { it.is_default == true || it.name == "default" }
                    ?: profiles.firstOrNull()
            val syncSnapshot = defaultProfile?.groupChatSyncSnapshot()
            val deletedKeys =
                syncSnapshot
                    ?.deleted
                    ?.keys
                    .orEmpty()
                    .map { it.lowercase() }

            val groupNameMap = linkedMapOf<String, String>()

            // 1. Collect from bot metadata (both `groups` array and `group` scalar)
            for (profile in profiles) {
                for (g in profile.botMeta()?.allGroups.orEmpty()) {
                    val trimmed = g.trim()
                    if (trimmed.isNotBlank()) {
                        val lower = trimmed.lowercase()
                        if (!groupNameMap.containsKey(lower)) {
                            groupNameMap[lower] = trimmed
                        }
                    }
                }
            }

            // 2. Collect from cross-device snapshot rooms (Desktop parity)
            val snapshotRooms = syncSnapshot?.rooms.orEmpty()
            for ((key, room) in snapshotRooms) {
                if (room.tombstone == true) continue
                val roomKeyLower = key.lowercase()
                if (deletedKeys.contains(roomKeyLower) ||
                    deletedKeys.contains("name:${room.name?.lowercase()}") ||
                    deletedKeys.contains("id:${room.roomId?.lowercase()}")
                ) {
                    continue
                }
                val roomName =
                    room.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: key.removePrefix("name:").removePrefix("id:").trim()
                if (roomName.isNotBlank()) {
                    val lower = roomName.lowercase()
                    if (!groupNameMap.containsKey(lower)) {
                        groupNameMap[lower] = roomName
                    }
                }
            }

            return groupNameMap.values
                .map { gName ->
                    val lower = gName.lowercase()
                    val matchingRoom =
                        snapshotRooms[gName]
                            ?: snapshotRooms["name:$gName"]
                            ?: snapshotRooms["id:$gName"]
                            ?: snapshotRooms.values.find { it.name.equals(gName, ignoreCase = true) }

                    val roomMemberNames = matchingRoom?.memberNames.orEmpty().map { it.lowercase() }

                    val matchedProfiles =
                        profiles
                            .filter { profile ->
                                val botGroups = profile.botMeta()?.allGroups.orEmpty()
                                botGroups.any { it.equals(gName, ignoreCase = true) } ||
                                    roomMemberNames.contains(profile.name.lowercase()) ||
                                    roomMemberNames.contains(profile.effectiveTitle.lowercase())
                            }.toMutableList()

                    // If room listed members (e.g. remote bots or bots not in local profiles), ensure they are seated
                    if (matchingRoom != null && roomMemberNames.isNotEmpty()) {
                        for (mName in matchingRoom.memberNames) {
                            val exists =
                                matchedProfiles.any {
                                    it.name.equals(mName, ignoreCase = true) ||
                                        it.effectiveTitle.equals(mName, ignoreCase = true)
                                }
                            if (!exists) {
                                val local = profiles.find { it.name.equals(mName, ignoreCase = true) }
                                matchedProfiles.add(local ?: ProfileInfo(name = mName))
                            }
                        }
                    }

                    var groupMembers: List<ProfileInfo> = matchedProfiles

                    // Fallback: If group name was composed from bot names (e.g. "default, scoutbot")
                    if (groupMembers.isEmpty() && gName.contains(",")) {
                        val targetNames = gName.split(",").map { it.trim().lowercase() }
                        groupMembers = profiles.filter { targetNames.contains(it.name.lowercase()) }
                    }

                    val lastAt =
                        matchingRoom?.log?.lastOrNull()?.at
                            ?: matchingRoom?.updatedAt
                            ?: 0L

                    GroupInfo(
                        name = gName,
                        members = groupMembers,
                        lastActivity = lastAt,
                    )
                }.sortedWith(
                    compareByDescending<GroupInfo> { it.lastActivity }
                        .thenBy { it.name },
                )
        }

    val displayGroups: List<GroupInfo>
        get() {
            val query = searchQuery.trim().lowercase()
            return allGroups.filter { group ->
                if (query.isBlank()) return@filter true
                group.name.lowercase().contains(query) ||
                    group.members.any {
                        it.name.lowercase().contains(query) ||
                            it.effectiveTitle.lowercase().contains(query)
                    }
            }
        }

    val activeNowBots: List<ProfileInfo>
        get() {
            val nowSeconds = System.currentTimeMillis() / 1000.0
            return profiles.filter { profile ->
                profile.worker_session != null ||
                    profile.name == activeProfileName ||
                    ((profile.canonical_session?.last_active ?: 0.0) > nowSeconds - 90) ||
                    ((profile.last_session?.last_active ?: 0.0) > nowSeconds - 90)
            }
        }

    val displayProfiles: List<ProfileInfo>
        get() {
            val query = searchQuery.trim().lowercase()
            return profiles
                .filter { profile ->
                    val isHidden = profile.isHidden || profile.name in hiddenProfiles
                    if (!showHidden && isHidden) return@filter false
                    if (query.isBlank()) return@filter true
                    profile.name.lowercase().contains(query) ||
                        profile.effectiveTitle.lowercase().contains(query) ||
                        profile.effectiveDescription.lowercase().contains(query)
                }.sortedWith(
                    compareByDescending<ProfileInfo> { it.name == activeProfileName }
                        .thenByDescending {
                            it.canonical_session?.last_active
                                ?: it.last_session?.last_active
                                ?: 0.0
                        }.thenBy { it.name },
                )
        }
}

class BotsViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    autoLoad: Boolean = true,
) : ViewModel(),
    ToastHost {
    private val _uiState = MutableStateFlow(BotsUiState())
    val uiState: StateFlow<BotsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(hiddenProfiles = AuthManager.getHiddenProfiles().toSet()) }
        if (autoLoad) {
            loadBots()
        }
    }

    /** The roster load in flight; a new request while it runs is folded into it. */
    private var loadJob: Job? = null
    private var markSeenAfterLoad: String? = null

    /** Pictures, "needs you" and previews, each refreshed on its own after a roster load. */
    private var enrichJobs: List<Job> = emptyList()

    fun loadBots(
        isRefresh: Boolean = false,
        thenMarkSeen: String? = null,
    ) {
        // One roster load at a time: a slow server must not stack refreshes on top of each other.
        if (loadJob?.isActive == true) {
            if (thenMarkSeen != null) markSeenAfterLoad = thenMarkSeen
            return
        }
        markSeenAfterLoad = thenMarkSeen
        val server = serverScope()
        _uiState.update {
            if (isRefresh) {
                it.copy(isRefreshing = true, errorMessage = null)
            } else {
                it.copy(isLoading = true, errorMessage = null)
            }
        }
        loadJob =
            viewModelScope.launch(ioDispatcher) {
                try {
                    loadRoster(server)
                } finally {
                    // Never leave the spinner on, whatever happened to the load.
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                }
            }
    }

    private suspend fun loadRoster(server: String) {
        coroutineScope {
            val activeDeferred =
                async(ioDispatcher) {
                    withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                        safeApiCall(retries = 0) { ApiClient.hermesApi.getActiveProfile() }
                    }
                }

            // First try fetching profiles via WebSocket RPC (profiles.list) which includes ui_meta (groups, custom avatars).
            var profilesWithMeta: List<ProfileInfo>? = null
            try {
                val rpcResult =
                    HermesWsClient.request(WsMethods.PROFILES_LIST, timeoutMs = METADATA_TIMEOUT_MS).await()
                val jsonElement =
                    when (rpcResult) {
                        is JsonElement -> rpcResult
                        null -> null
                        else -> rpcResult.toJsonElement()
                    }
                if (jsonElement != null) {
                    val resp = OkHttpProvider.json.decodeFromJsonElement<ProfilesResponse>(jsonElement)
                    if (!resp.profiles.isNullOrEmpty()) {
                        profilesWithMeta = resp.profiles
                    }
                }
            } catch (_: Exception) {
                // Fallback to REST API below
            }

            val profilesResult =
                if (profilesWithMeta != null) {
                    null
                } else {
                    withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                        safeApiCall(retries = 0) { ApiClient.hermesApi.getProfiles() }
                    }
                }
            val activeResult = activeDeferred.await()
            // Switched server while this was in flight: its answer belongs to the old one.
            if (serverScope() != server) return@coroutineScope

            if (profilesWithMeta != null) {
                val activeName = (activeResult as? NetworkResult.Success)?.data?.active
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        profiles = profilesWithMeta,
                        activeProfileName = activeName ?: it.activeProfileName,
                        hiddenProfiles = AuthManager.getHiddenProfiles().toSet(),
                        errorMessage = null,
                    )
                }
            } else if (profilesResult is NetworkResult.Success) {
                val activeName = (activeResult as? NetworkResult.Success)?.data?.active
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        profiles = profilesResult.data.profiles.orEmpty(),
                        activeProfileName = activeName ?: it.activeProfileName,
                        hiddenProfiles = AuthManager.getHiddenProfiles().toSet(),
                        errorMessage = null,
                    )
                }
            } else {
                val err =
                    (profilesResult as? NetworkResult.Failure)?.error?.message
                        ?: "Failed to load bots"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = err,
                    )
                }
            }
        }
        // The rest only runs while the answer is still for this server.
        if (serverScope() != server) return
        if (_uiState.value.errorMessage == null) afterRosterLoaded(_uiState.value.profiles)
        markSeenAfterLoad?.let { name ->
            markSeenAfterLoad = null
            _uiState.value.profiles
                .firstOrNull { it.name == name }
                ?.let(::markSeen)
        }
    }

    private fun serverScope(): String = runCatching { AuthManager.baseUrl() }.getOrDefault("")

    /**
     * Pictures, "needs you", previews and unread baselines. Each runs on its own, so a slow
     * picture never holds up "needs you"; one still running from the last refresh is left
     * to finish rather than started again.
     */
    private fun afterRosterLoaded(profiles: List<ProfileInfo>) {
        recordSeenBaselines(profiles)
        if (enrichJobs.any { it.isActive }) return
        enrichJobs =
            listOf(
                viewModelScope.launch(ioDispatcher) { refreshNeedsYou(profiles) },
                viewModelScope.launch(ioDispatcher) { refreshAvatars(profiles) },
                viewModelScope.launch(ioDispatcher) { refreshPreviews(profiles) },
            )
    }

    /** Message count each preview was fetched at, so only bots with new messages are re-read. */
    private val previewCounts = mutableMapOf<String, Int?>()

    private suspend fun refreshPreviews(profiles: List<ProfileInfo>) {
        val stale =
            profiles.filter { p ->
                val sessionId = p.canonical_session?.let { it.resolved_id ?: it.id }
                !sessionId.isNullOrBlank() &&
                    (p.name !in previewCounts || previewCounts[p.name] != BotsPresentation.messageCount(p))
            }
        if (stale.isEmpty()) return
        val fetched =
            coroutineScope {
                stale
                    .map { p ->
                        async(ioDispatcher) {
                            val sessionId = p.canonical_session?.let { it.resolved_id ?: it.id }.orEmpty()
                            val result =
                                withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                                    safeApiCall(retries = 0) {
                                        ApiClient.hermesApi.getSessionMessages(
                                            sessionId,
                                            limit = PREVIEW_PAGE,
                                            order = "latest",
                                            profile = p.name,
                                        )
                                    }
                                }
                            val preview =
                                (result as? NetworkResult.Success)
                                    ?.data
                                    ?.messages
                                    ?.let(BotsPresentation::latestPreview)
                            if (result is NetworkResult.Success) {
                                previewCounts[p.name] =
                                    BotsPresentation.messageCount(p)
                            }
                            p.name to preview
                        }
                    }.map { it.await() }
            }.filter { it.second != null }
                .associate { it.first to it.second!! }
        if (fetched.isNotEmpty()) _uiState.update { it.copy(previews = it.previews + fetched) }
    }

    private fun recordSeenBaselines(profiles: List<ProfileInfo>) {
        val seen = BotSeenStore.all()
        for (profile in profiles) {
            val count = BotsPresentation.messageCount(profile) ?: continue
            if (profile.name !in seen) BotSeenStore.put(profile.name, count)
        }
        _uiState.update { it.copy(seenCounts = BotSeenStore.all()) }
    }

    /** Marks a bot's conversation as read up to its current message count. */
    fun markSeen(profile: ProfileInfo) {
        val count = BotsPresentation.messageCount(profile) ?: return
        if (_uiState.value.seenCounts[profile.name] == count) return
        BotSeenStore.put(profile.name, count)
        _uiState.update { it.copy(seenCounts = BotSeenStore.all()) }
    }

    private suspend fun refreshAvatars(profiles: List<ProfileInfo>) {
        val loaded = BotAvatarCache.load(profiles).filterKeys { name -> profiles.any { it.name == name } }
        if (loaded != _uiState.value.avatars) _uiState.update { it.copy(avatars = loaded) }
    }

    private suspend fun refreshNeedsYou(profiles: List<ProfileInfo>) {
        val waiting =
            runCatching {
                val result =
                    HermesWsClient
                        .request(
                            WsMethods.SESSION_ACTIVE_LIST,
                            timeoutMs = METADATA_TIMEOUT_MS,
                            suppressErrorEvent = true,
                        ).await()
                        .asJsonObject()
                result
                    ?.get("sessions")
                    ?.let { it as? JsonArray }
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { it.string("status") == "waiting" }
                    .flatMap { listOfNotNull(it.string("session_key"), it.string("id")) }
                    .toSet()
            }.getOrNull() ?: return
        val names = BotsPresentation.needsYou(profiles, waiting)
        if (names != _uiState.value.needsYou) _uiState.update { it.copy(needsYou = names) }
    }

    /**
     * Stores (or clears, when [image] is null) a bot's picture in the server's avatar store,
     * the same place the desktop app reads it from.
     */
    private suspend fun saveAvatarImage(
        name: String,
        image: String?,
    ): AvatarSave =
        try {
            val params =
                if (image == null) {
                    mapOf("name" to name, "asset" to "avatar", "clear" to true)
                } else {
                    mapOf("name" to name, "asset" to "avatar", "data" to image)
                }
            HermesWsClient.request(WsMethods.PROFILES_SET_ASSET, params, suppressErrorEvent = true).await()
            BotAvatarCache.put(name, image)
            _uiState.update {
                it.copy(avatars = if (image == null) it.avatars - name else it.avatars + (name to image))
            }
            AvatarSave.Stored
        } catch (e: HermesWsClient.HermesRpcException) {
            if (e.code == RPC_METHOD_NOT_FOUND) AvatarSave.Unsupported else AvatarSave.Failed(e.message.orEmpty())
        } catch (e: Exception) {
            AvatarSave.Failed(e.message.orEmpty())
        }

    /** Asks Hermes's image generator for a picture; the data URL, or an error to show. */
    suspend fun generateAvatar(prompt: String): Result<String> =
        try {
            val result =
                HermesWsClient
                    .request(
                        WsMethods.IMAGE_GENERATE,
                        mapOf("prompt" to prompt, "aspect_ratio" to "square", "max_bytes" to 8_000_000),
                        timeoutMs = GENERATE_TIMEOUT_MS,
                        suppressErrorEvent = true,
                    ).await()
                    .asJsonObject()
            val data = result?.string("image_data")
            when {
                result?.bool("available") == false -> {
                    Result.failure(IllegalStateException(GENERATE_UNAVAILABLE))
                }

                result?.bool("success") == true && !data.isNullOrBlank() -> {
                    Result.success(data)
                }

                else -> {
                    Result.failure(IllegalStateException(result?.string("error") ?: "Generation failed"))
                }
            }
        } catch (e: HermesWsClient.HermesRpcException) {
            Result.failure(
                IllegalStateException(if (e.code == RPC_METHOD_NOT_FOUND) GENERATE_UNAVAILABLE else e.message),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedTab(tab: BotsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleShowHidden() {
        _uiState.update { it.copy(showHidden = !it.showHidden) }
    }

    suspend fun selectBot(bot: ProfileInfo): Boolean {
        val result = ProfileSwitchCoordinator.switchProfile(bot.name)
        return result is NetworkResult.Success
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    override fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun createBot(
        name: String,
        title: String,
        description: String,
        shape: String,
        color: String,
        imageUrl: String? = null,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val req =
                CreateProfileRequest(
                    name = name,
                    description = description.ifBlank { null },
                    clone_from_default = false,
                )
            val result = safeApiCall { ApiClient.hermesApi.createProfile(req) }
            if (result is NetworkResult.Success) {
                // Configure UI metadata (avatar, custom title) and bot SOUL via RPC
                val saved = if (imageUrl != null) saveAvatarImage(name, imageUrl) else AvatarSave.Stored
                val botMeta =
                    BotRosterMeta(
                        title = title.ifBlank { null },
                        description = description.ifBlank { null },
                        avatar =
                            BotAvatarMeta(
                                shape = shape,
                                color = color,
                                // Older servers have no avatar store: keep the picture inline there.
                                image_url = imageUrl.takeIf { saved == AvatarSave.Unsupported },
                            ),
                    )
                val botSoul = composeBotSoul(name, title, description)
                val error = configureAndCheck(name, botMeta, soul = botSoul)
                reportSaveProblems(error, saved)
                loadBots()
                onSuccess()
            } else {
                val err =
                    (result as? NetworkResult.Failure)?.error?.message
                        ?: "Failed to create bot"
                _uiState.update { it.copy(errorMessage = err) }
            }
        }
    }

    fun updateBotMeta(
        name: String,
        title: String,
        description: String,
        shape: String,
        color: String,
        imageUrl: String? = null,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val bot = _uiState.value.profiles.find { it.name == name }
            val existingMeta = bot?.botMeta() ?: BotRosterMeta()
            val existingInline = existingMeta.avatar?.image_url?.takeIf { it.isNotBlank() }
            val shown = bot?.let { _uiState.value.imageFor(it) }
            val saved = if (imageUrl != shown) saveAvatarImage(name, imageUrl) else null
            val inlineImage =
                when (saved) {
                    // Now in the avatar store, so drop any older inline copy.
                    AvatarSave.Stored -> null

                    AvatarSave.Unsupported -> imageUrl

                    is AvatarSave.Failed, null -> existingInline
                }
            val updatedMeta =
                existingMeta.copy(
                    title = title.ifBlank { null },
                    description = description.ifBlank { null },
                    // Keep fields this editor doesn't touch (e.g. icon).
                    avatar =
                        (existingMeta.avatar ?: BotAvatarMeta()).copy(
                            shape = shape,
                            color = color,
                            image_url = inlineImage,
                        ),
                )
            val error = configureAndCheck(name, updatedMeta)
            reportSaveProblems(error, saved)
            loadBots()
            onSuccess()
        }
    }

    fun deleteBot(
        name: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = safeApiCall { ApiClient.hermesApi.deleteProfile(name) }
            if (result is NetworkResult.Success) {
                loadBots()
                onSuccess()
            } else {
                val err =
                    (result as? NetworkResult.Failure)?.error?.message
                        ?: "Failed to delete bot"
                _uiState.update { it.copy(errorMessage = err) }
            }
        }
    }

    fun createGroupChat(
        groupName: String,
        botNames: List<String>,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val currentProfiles = _uiState.value.profiles
            // 1. Update bot metadata on each member bot
            for (name in botNames) {
                val bot = currentProfiles.find { it.name == name } ?: continue
                val existingGroups = bot.botMeta()?.allGroups.orEmpty()
                if (!existingGroups.any { it.equals(groupName, ignoreCase = true) }) {
                    val updatedMeta =
                        (bot.botMeta() ?: BotRosterMeta()).copy(
                            groups = existingGroups + groupName,
                            group = existingGroups.firstOrNull() ?: groupName,
                        )
                    try {
                        wsClientConfigureBot(name, updatedMeta).await()
                    } catch (_: Exception) {
                    }
                }
            }

            // 2. Also register room in hermes-bots-groups sync snapshot on default profile
            try {
                val defaultProfile =
                    currentProfiles.find { it.is_default == true || it.name == "default" }
                        ?: currentProfiles.firstOrNull()
                if (defaultProfile != null) {
                    val existingSnapshot =
                        defaultProfile.groupChatSyncSnapshot()
                            ?: GroupChatSyncSnapshot(version = 3, rooms = emptyMap())
                    val roomKey = "name:$groupName"
                    val newRoom =
                        GroupChatRoomMeta(
                            name = groupName,
                            members = botNames.map { JsonPrimitive(it) },
                            updatedAt = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis(),
                        )
                    val updatedRooms = existingSnapshot.rooms.orEmpty() + (roomKey to newRoom)
                    val updatedDeleted =
                        existingSnapshot.deleted.orEmpty().filterKeys {
                            !it.equals(roomKey, ignoreCase = true) && !it.equals(groupName, ignoreCase = true)
                        }
                    val newSnapshot =
                        existingSnapshot.copy(
                            version = 3,
                            updatedAt = System.currentTimeMillis(),
                            rooms = updatedRooms,
                            deleted = updatedDeleted,
                        )
                    HermesWsClient
                        .request(
                            WsMethods.PROFILES_CONFIGURE,
                            mapOf(
                                "name" to defaultProfile.name,
                                "ui_meta" to mapOf("hermes-bots-groups" to newSnapshot.toMap()),
                            ),
                        ).await()
                }
            } catch (_: Exception) {
            }

            loadBots()
            onSuccess()
        }
    }

    fun disbandGroupChat(
        groupName: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val currentProfiles = _uiState.value.profiles
            // 1. Remove from all member bots' metadata
            for (bot in currentProfiles) {
                val existingGroups = bot.botMeta()?.allGroups.orEmpty()
                if (existingGroups.any { it.equals(groupName, ignoreCase = true) }) {
                    val filtered = existingGroups.filterNot { it.equals(groupName, ignoreCase = true) }
                    val updatedMeta =
                        (bot.botMeta() ?: BotRosterMeta()).copy(
                            groups = filtered,
                            group = filtered.firstOrNull(),
                        )
                    try {
                        wsClientConfigureBot(bot.name, updatedMeta).await()
                    } catch (_: Exception) {
                    }
                }
            }

            // 2. Remove room from hermes-bots-groups snapshot and register tombstone in deleted map
            try {
                val defaultProfile =
                    currentProfiles.find { it.is_default == true || it.name == "default" }
                        ?: currentProfiles.firstOrNull()
                if (defaultProfile != null) {
                    val existingSnapshot = defaultProfile.groupChatSyncSnapshot()
                    if (existingSnapshot != null) {
                        val updatedRooms =
                            existingSnapshot.rooms.orEmpty().filterKeys { key ->
                                val room = existingSnapshot.rooms?.get(key)
                                !key.equals(groupName, ignoreCase = true) &&
                                    !key.equals("name:$groupName", ignoreCase = true) &&
                                    !key.equals("id:$groupName", ignoreCase = true) &&
                                    !(room?.name.equals(groupName, ignoreCase = true))
                            }
                        val deletedMap =
                            existingSnapshot.deleted.orEmpty() + ("name:$groupName" to System.currentTimeMillis())
                        val newSnapshot =
                            existingSnapshot.copy(
                                version = 3,
                                updatedAt = System.currentTimeMillis(),
                                rooms = updatedRooms,
                                deleted = deletedMap,
                            )
                        HermesWsClient
                            .request(
                                WsMethods.PROFILES_CONFIGURE,
                                mapOf(
                                    "name" to defaultProfile.name,
                                    "ui_meta" to mapOf("hermes-bots-groups" to newSnapshot.toMap()),
                                ),
                            ).await()
                    }
                }
            } catch (_: Exception) {
            }

            loadBots()
            onSuccess()
        }
    }

    private fun composeBotSoul(
        name: String,
        title: String,
        description: String,
    ): String {
        val displayName = title.ifBlank { name }
        val lines = mutableListOf<String>()
        lines.add("# $displayName")
        lines.add("")
        if (title.isNotBlank()) lines.add("**Role:** $title")
        if (description.isNotBlank()) lines.add("**Mission:** $description")
        lines.add("")
        lines.add("You are $displayName, a persistent named agent (profile `$name`) on this machine.")
        lines.add("You keep your own memory, skills, and conversation history across sessions.")
        return lines.joinToString("\n")
    }

    /** Saves the bot's look and details; an error to show, or null when the server kept them. */
    private suspend fun configureAndCheck(
        name: String,
        meta: BotRosterMeta,
        soul: String? = null,
    ): String? =
        try {
            val result = wsClientConfigureBot(name, meta, soul).await().asJsonObject()
            val applied = result?.get("applied") as? JsonObject
            if ((applied?.get("ui_meta") as? JsonPrimitive)?.booleanOrNull == false) {
                "the server didn't keep the bot's look"
            } else {
                null
            }
        } catch (e: Exception) {
            e.message ?: "no answer from Hermes"
        }

    private fun reportSaveProblems(
        configureError: String?,
        saved: AvatarSave?,
    ) {
        val message =
            when {
                saved is AvatarSave.Failed -> "Couldn't save the picture: ${saved.reason.ifBlank { "unknown error" }}"
                configureError != null -> "Couldn't save changes: $configureError"
                else -> null
            }
        if (message != null) _uiState.update { it.copy(toastMessage = message) }
    }

    private fun wsClientConfigureBot(
        name: String,
        meta: BotRosterMeta,
        soul: String? = null,
    ): kotlinx.coroutines.CompletableDeferred<Any?> {
        val metaMap =
            buildMap<String, Any> {
                meta.title?.let { put("title", it) }
                meta.description?.let { put("description", it) }
                meta.avatar?.let { av ->
                    put(
                        "avatar",
                        buildMap<String, Any> {
                            av.shape?.let { put("shape", it) }
                            av.color?.let { put("color", it) }
                            av.icon?.let { put("icon", it) }
                            // Always sent so removing an image clears it; readers treat "" as no image.
                            put("image_url", av.image_url.orEmpty())
                        },
                    )
                }
                if (!meta.groups.isNullOrEmpty()) {
                    put("groups", meta.groups)
                }
            }

        val params =
            buildMap<String, Any> {
                put("name", name)
                put("ui_meta", mapOf("hermes-bots" to metaMap))
                soul?.let { put("soul", it) }
            }

        return HermesWsClient.request(
            WsMethods.PROFILES_CONFIGURE,
            params,
        )
    }
}

/** Outcome of saving a bot picture to the server's avatar store. */
sealed interface AvatarSave {
    data object Stored : AvatarSave

    /** The server predates the avatar store. */
    data object Unsupported : AvatarSave

    data class Failed(
        val reason: String,
    ) : AvatarSave
}

private const val RPC_METHOD_NOT_FOUND = -32601

/** Newest messages read per bot for its preview (tool rows sit between the text ones). */
private const val PREVIEW_PAGE = 6
private const val GENERATE_TIMEOUT_MS = 180_000L

/** Roster, status, preview and picture reads: short, so a slow server can't stall the list. */
internal const val METADATA_TIMEOUT_MS = 10_000L
internal const val GENERATE_UNAVAILABLE = "No image generator is set up on Hermes"

private fun Any?.asJsonObject(): JsonObject? =
    when (this) {
        null -> null
        is JsonObject -> this
        else -> runCatching { toJsonElement() as? JsonObject }.getOrNull()
    }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
