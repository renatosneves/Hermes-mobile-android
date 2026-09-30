package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.VoiceLiveSessionRequest
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.voice.VoiceLiveHost
import com.m57.hermescontrol.voice.VoiceLivePlanner
import com.m57.hermescontrol.voice.VoiceReply
import kotlinx.serialization.json.JsonArray

/** Connects GPT-Live to the open chat: requests go in as your messages, replies come back out. */
class ChatVoiceLiveHost(
    private val viewModel: ChatViewModel,
) : VoiceLiveHost {
    /** Assistant messages already on screen when the current request went in. */
    @Volatile private var baseline: Set<String> = emptySet()

    override suspend fun checkAvailable(): Pair<Boolean, String?> =
        when (val result = safeApiCall(retries = 0) { ApiClient.hermesApi.voiceLiveStatus() }) {
            is NetworkResult.Success -> {
                val status = result.data
                if (status?.available == true) true to null else false to (status?.reason ?: NOT_SET_UP)
            }

            is NetworkResult.Failure -> {
                // An older Hermes without the endpoint answers 404.
                val error = result.error
                false to if (error is NetworkError.Http && error.code == 404) NOT_SUPPORTED else error.message
            }
        }

    override suspend fun exchangeOffer(
        sdp: String,
        history: JsonArray,
    ): String =
        when (
            val result =
                safeApiCall(retries = 0) {
                    ApiClient.hermesApi.createVoiceLiveSession(VoiceLiveSessionRequest(sdp = sdp, history = history))
                }
        ) {
            is NetworkResult.Success -> {
                result.data
                    ?.transport
                    ?.sdp
                    ?.takeIf { result.data.ok && it.isNotBlank() }
                    ?: error("GPT-Live session creation failed")
            }

            is NetworkResult.Failure -> {
                error(result.error.message)
            }
        }

    override fun seedHistory(): JsonArray =
        VoiceLivePlanner.toLiveHistory(
            viewModel.uiState.value.messages
                .filter { (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) && it.toolName == null }
                .map { (it.role == MessageRole.USER) to it.content },
        )

    override fun submit(
        prompt: String,
        voiceContext: String,
    ) {
        baseline = assistantIds()
        check(viewModel.sendVoiceMessage(prompt, voiceContext)) { "Hermes did not accept the request" }
    }

    override fun isBusy(): Boolean = viewModel.uiState.value.isAgentTyping

    override fun interrupt() = viewModel.interruptSession()

    override fun replySince(sinceMs: Long): VoiceReply? {
        viewModel.streamingState.value.streamingMessage
            ?.takeIf { it.role == MessageRole.ASSISTANT && it.id !in baseline && it.content.isNotBlank() }
            ?.let { return VoiceReply(it.id, it.content, pending = true) }
        return viewModel.uiState.value.messages
            .lastOrNull { isReply(it) && it.id !in baseline }
            ?.let { VoiceReply(it.id, it.content, pending = it.isStreaming) }
    }

    override fun activeTool(): String? =
        viewModel.uiState.value.messages
            .lastOrNull { it.toolStatus == ToolStatus.RUNNING }
            ?.toolName
            ?.replace('_', ' ')

    private fun assistantIds(): Set<String> =
        buildSet {
            viewModel.uiState.value.messages
                .filter(::isReply)
                .forEach { add(it.id) }
            viewModel.streamingState.value.streamingMessage
                ?.let { add(it.id) }
        }

    private fun isReply(message: ChatMessage) =
        message.role == MessageRole.ASSISTANT && message.toolName == null && message.content.isNotBlank()

    private companion object {
        const val NOT_SET_UP = "GPT-Live isn't set up on Hermes yet: it needs an OpenAI API key."
        const val NOT_SUPPORTED = "This Hermes server doesn't offer GPT-Live voice yet."
    }
}
