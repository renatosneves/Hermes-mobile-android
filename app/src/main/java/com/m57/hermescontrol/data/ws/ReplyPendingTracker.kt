package com.m57.hermescontrol.data.ws

/**
 * Tracks which agent turns are in flight, per session, so the background
 * connection is only released once EVERY turn has finished.
 *
 * Before this, a single boolean was cleared by any `message.complete`: with
 * two bots working at once (e.g. Chief of Staff on a long turn and Ledger on a
 * short one), the short turn finishing in the background dropped the socket
 * and retired the foreground service while the long turn was still running,
 * so its reply never reached the phone.
 *
 * Lifecycle of one prompt:
 *  1. [onPromptSubmitted] when `prompt.submit` is sent (keyed by RPC id).
 *  2. [onPromptAccepted] when the gateway answers: the turn now belongs to
 *     its session until it completes.
 *     [onPromptRejected] when the gateway errors: nothing is pending for it.
 *  3. [onTurnActivity] for streaming events (also covers turns started from
 *     other devices on a subscribed session).
 *  4. [onTurnComplete] when `message.complete` arrives for the session.
 *
 * Safety net: a completion whose session is unknown (null id, or an id never
 * seen here, e.g. a runtime/stored id mismatch) clears everything, which is
 * exactly the previous behaviour. The tracker can therefore only keep the
 * connection longer when it is sure another turn is still running, never
 * strand it open on an unmatched id.
 *
 * Expiry: a turn that shows no activity for [STALE_MS] no longer counts (a
 * completion lost to a dropped socket would otherwise keep the background
 * connection and its notification up for good, draining the battery).
 *
 * Thread-safe: called from the OkHttp reader thread, the send path and the
 * event collector.
 */
internal class ReplyPendingTracker(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /** Prompt submits awaiting the gateway's answer, with when they were sent. */
    private val submitSessionByRequestId = HashMap<String, Pair<String?, Long>>()

    /** Sessions with a turn in flight, with their last sign of life. */
    private val busySessions = HashMap<String, Long>()

    /** Legacy flag for turns whose session id is unknown (null): when it was last seen, or null. */
    private var anonymousTurnAt: Long? = null

    @get:Synchronized
    val isPending: Boolean
        get() {
            expireStale()
            return submitSessionByRequestId.isNotEmpty() || busySessions.isNotEmpty() || anonymousTurnAt != null
        }

    @Synchronized
    fun onPromptSubmitted(
        requestId: String,
        sessionId: String?,
    ) {
        submitSessionByRequestId[requestId] = sessionId to nowMs()
    }

    /** @return true when [requestId] was a tracked prompt submit. */
    @Synchronized
    fun onPromptAccepted(requestId: String): Boolean {
        val submit = submitSessionByRequestId.remove(requestId) ?: return false
        markBusy(submit.first)
        return true
    }

    /** @return true when [requestId] was a tracked prompt submit. */
    @Synchronized
    fun onPromptRejected(requestId: String): Boolean = submitSessionByRequestId.remove(requestId) != null

    @Synchronized
    fun onTurnActivity(sessionId: String?) {
        markBusy(sessionId)
    }

    @Synchronized
    fun onTurnComplete(sessionId: String?) {
        if (sessionId == null || busySessions.remove(sessionId) == null) clear()
    }

    /** True when a turn other than [sessionId]'s is still in flight. */
    @Synchronized
    fun isPendingExcept(sessionId: String?): Boolean {
        if (sessionId == null) return false
        expireStale()
        return submitSessionByRequestId.isNotEmpty() ||
            anonymousTurnAt != null ||
            busySessions.keys.any { it != sessionId }
    }

    @Synchronized
    fun clear() {
        submitSessionByRequestId.clear()
        busySessions.clear()
        anonymousTurnAt = null
    }

    private fun markBusy(sessionId: String?) {
        if (sessionId.isNullOrBlank()) anonymousTurnAt = nowMs() else busySessions[sessionId] = nowMs()
    }

    private fun expireStale() {
        val cutoff = nowMs() - STALE_MS
        submitSessionByRequestId.values.removeAll { it.second < cutoff }
        busySessions.values.removeAll { it < cutoff }
        if ((anonymousTurnAt ?: Long.MAX_VALUE) < cutoff) anonymousTurnAt = null
    }

    companion object {
        /** Long enough for a slow tool step to stay quiet, short enough not to hold the radio for hours. */
        const val STALE_MS = 15 * 60_000L
    }
}
