package com.m57.hermescontrol.ui.fleet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetSimulatorTest {
    private fun run(
        sim: FleetSimulator,
        ms: Long,
    ) {
        var elapsed = 0L
        while (elapsed < ms) {
            sim.tick(FleetSimulator.TICK_MS)
            elapsed += FleetSimulator.TICK_MS
        }
    }

    @Test
    fun `seed opens with work in every live column`() {
        val snap = FleetSimulator().snapshot()
        assertTrue(snap.inColumn(FleetColumn.QUEUED).isNotEmpty())
        assertTrue(snap.inColumn(FleetColumn.WORKING).isNotEmpty())
        assertTrue(snap.inColumn(FleetColumn.NEEDS_YOU).isNotEmpty())
        assertEquals(BotActivity.WAITING, snap.activityOf("inbox"))
    }

    @Test
    fun `approving a waiting task moves it to done`() {
        val sim = FleetSimulator()
        val waiting = sim.snapshot().inColumn(FleetColumn.NEEDS_YOU).first()
        val doneBefore = sim.snapshot().doneToday

        assertTrue(sim.approve(waiting.id))

        val after = sim.snapshot()
        val task = after.tasks.first { it.id == waiting.id }
        assertEquals(FleetColumn.DONE, task.column)
        assertEquals("Approved by you", task.outcome)
        assertEquals(doneBefore + 1, after.doneToday)
        assertFalse(sim.approve(waiting.id))
    }

    @Test
    fun `sending back returns the task to working`() {
        val sim = FleetSimulator()
        val waiting = sim.snapshot().inColumn(FleetColumn.NEEDS_YOU).first()

        assertTrue(sim.sendBack(waiting.id))

        assertEquals(
            FleetColumn.WORKING,
            sim
                .snapshot()
                .tasks
                .first { it.id == waiting.id }
                .column,
        )
    }

    @Test
    fun `multi-bot tasks hand off along their route`() {
        val sim = FleetSimulator()
        val template = FleetSimulator.DEMO_TEMPLATES.first { it.route.size == 3 && it.ask == null }
        val task = sim.submit(template)

        run(sim, 25_000)

        val events = sim.snapshot().events.map { e -> e.parts.filterIsInstance<FeedPart.Bot>().map { it.botId } }
        assertTrue(events.any { it == listOf(template.route[0], template.route[1]) })
        val finished = sim.snapshot().tasks.firstOrNull { it.id == task.id }
        assertTrue(finished == null || finished.column == FleetColumn.DONE || finished.hop > 0)
    }

    @Test
    fun `simulation keeps a bounded board`() {
        val sim = FleetSimulator()
        run(sim, 10 * 60_000)
        val snap = sim.snapshot()
        assertTrue(snap.tasks.count { it.column != FleetColumn.DONE } <= 8)
        assertTrue(snap.inColumn(FleetColumn.DONE).size <= 5)
        assertTrue(snap.events.size <= 30)
    }

    @Test
    fun `map positions cover every bot plus you and the router`() {
        val positions = mapPositions(FleetSimulator.DEMO_BOTS)
        assertEquals(FleetSimulator.DEMO_BOTS.size + 2, positions.size)
        positions.values.forEach {
            assertTrue(it.x in 0f..400f)
            assertTrue(it.y in 0f..440f)
        }
    }
}
