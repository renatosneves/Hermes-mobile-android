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
 * Thread-safe: called from the OkHttp reader thread, the send path and the
 * event collector.
 */
internal class ReplyPendingTracker {
    private val submitSessionByRequestId = HashMap<String, String?>()
    private val busySessions = HashSet<String>()

    /** Legacy flag for turns whose session id is unknown (null). */
    private var anonymousTurn = false

    @get:Synchronized
    val isPending: Boolean
        get() = submitSessionByRequestId.isNotEmpty() || busySessions.isNotEmpty() || anonymousTurn

    @Synchronized
    fun onPromptSubmitted(
        requestId: String,
        sessionId: String?,
    ) {
        submitSessionByRequestId[requestId] = sessionId
    }

    /** @return true when [requestId] was a tracked prompt submit. */
    @Synchronized
    fun onPromptAccepted(requestId: String): Boolean {
        if (!submitSessionByRequestId.containsKey(requestId)) return false
        markBusy(submitSessionByRequestId.remove(requestId))
        return true
    }

    /** @return true when [requestId] was a tracked prompt submit. */
    @Synchronized
    fun onPromptRejected(requestId: String): Boolean {
        if (!submitSessionByRequestId.containsKey(requestId)) return false
        submitSessionByRequestId.remove(requestId)
        return true
    }

    @Synchronized
    fun onTurnActivity(sessionId: String?) {
        markBusy(sessionId)
    }

    @Synchronized
    fun onTurnComplete(sessionId: String?) {
        if (sessionId == null || !busySessions.remove(sessionId)) clear()
    }

    /** True when a turn other than [sessionId]'s is still in flight. */
    @Synchronized
    fun isPendingExcept(sessionId: String?): Boolean {
        if (sessionId == null) return false
        return submitSessionByRequestId.isNotEmpty() ||
            anonymousTurn ||
            busySessions.any { it != sessionId }
    }

    @Synchronized
    fun clear() {
        submitSessionByRequestId.clear()
        busySessions.clear()
        anonymousTurn = false
    }

    private fun markBusy(sessionId: String?) {
        if (sessionId.isNullOrBlank()) anonymousTurn = true else busySessions.add(sessionId)
    }
}
