package com.m57.hermescontrol.data.ws

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPendingTrackerTest {
    @Test
    fun submittedPromptIsPendingUntilItsTurnCompletes() {
        val tracker = ReplyPendingTracker()

        tracker.onPromptSubmitted("1", "cos")
        assertTrue(tracker.isPending)

        // The gateway's ack arrives before message.start: still pending.
        assertTrue(tracker.onPromptAccepted("1"))
        assertTrue(tracker.isPending)

        tracker.onTurnActivity("cos")
        tracker.onTurnComplete("cos")
        assertFalse(tracker.isPending)
    }

    @Test
    fun oneBotFinishingDoesNotReleaseAnotherBotsTurn() {
        val tracker = ReplyPendingTracker()
        tracker.onPromptSubmitted("1", "cos")
        tracker.onPromptAccepted("1")
        tracker.onTurnActivity("ledger")

        tracker.onTurnComplete("ledger")

        assertTrue(tracker.isPending)
        assertTrue(tracker.isPendingExcept("ledger"))
        tracker.onTurnComplete("cos")
        assertFalse(tracker.isPending)
    }

    @Test
    fun rejectedPromptLeavesOtherTurnsPending() {
        val tracker = ReplyPendingTracker()
        tracker.onTurnActivity("cos")
        tracker.onPromptSubmitted("2", "ledger")

        assertTrue(tracker.onPromptRejected("2"))

        assertTrue(tracker.isPending)
        assertFalse(tracker.onPromptRejected("2"))
    }

    @Test
    fun rejectingTheOnlyPromptClearsPending() {
        val tracker = ReplyPendingTracker()
        tracker.onPromptSubmitted("3", "cos")

        tracker.onPromptRejected("3")

        assertFalse(tracker.isPending)
    }

    @Test
    fun unknownOrMissingSessionOnCompleteFallsBackToClearingEverything() {
        val tracker = ReplyPendingTracker()
        tracker.onTurnActivity("cos")
        tracker.onTurnActivity("ledger")

        tracker.onTurnComplete("runtime-id-never-seen")
        assertFalse(tracker.isPending)

        tracker.onTurnActivity("cos")
        tracker.onTurnComplete(null)
        assertFalse(tracker.isPending)
    }

    @Test
    fun sessionlessActivityIsTrackedLikeTheLegacyFlag() {
        val tracker = ReplyPendingTracker()

        tracker.onTurnActivity(null)
        assertTrue(tracker.isPending)

        tracker.onTurnComplete(null)
        assertFalse(tracker.isPending)
    }

    @Test
    fun acceptingAnUnknownRequestIsANoOp() {
        val tracker = ReplyPendingTracker()

        assertFalse(tracker.onPromptAccepted("not-a-prompt"))
        assertFalse(tracker.isPending)
    }

    @Test
    fun isPendingExceptIgnoresTheCompletedSession() {
        val tracker = ReplyPendingTracker()
        tracker.onTurnActivity("cos")

        assertFalse(tracker.isPendingExcept("cos"))
        assertFalse(tracker.isPendingExcept(null))
    }

    @Test
    fun aTurnThatWentQuietStopsHoldingTheConnection() {
        var now = 0L
        val tracker = ReplyPendingTracker(nowMs = { now })
        tracker.onTurnActivity("cos")
        tracker.onPromptSubmitted("r1", "ask")

        now += ReplyPendingTracker.STALE_MS - 1
        assertTrue(tracker.isPending)
        tracker.onTurnActivity("cos")

        now += 2
        // The unanswered submit expired; the turn that just streamed has not.
        assertTrue(tracker.isPending)
        assertFalse(tracker.isPendingExcept("cos"))

        now += ReplyPendingTracker.STALE_MS
        assertFalse(tracker.isPending)
    }
}
