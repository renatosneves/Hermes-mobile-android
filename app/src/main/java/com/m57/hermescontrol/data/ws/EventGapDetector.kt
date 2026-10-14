package com.m57.hermescontrol.data.ws

/**
 * Tells a subscriber of [HermesWsClient.events] that it fell so far behind that the oldest
 * buffered events were dropped. A dropped "turn finished" or "approval needed" would otherwise
 * leave a chat looking stuck; the subscriber reloads from the server instead.
 */
class EventGapDetector(
    private val capacity: Int,
    private val published: () -> Long,
) {
    private var baseline = published()
    private var received = 0L

    /** Call once per event received; true when events were lost since the last check. */
    fun onReceived(): Boolean {
        received++
        val behind = published() - baseline - received
        if (behind <= capacity) return false
        baseline = published()
        received = 0
        return true
    }
}
