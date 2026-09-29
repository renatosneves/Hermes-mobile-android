package com.m57.hermescontrol.ui.fleet

import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanColumn
import com.m57.hermescontrol.data.model.KanbanProfile
import com.m57.hermescontrol.data.model.KanbanTask
import com.m57.hermescontrol.data.model.TaskProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFleetSourceTest {
    private val profiles =
        listOf(
            KanbanProfile(name = "cos"),
            KanbanProfile(name = "inbox"),
            KanbanProfile(name = "work"),
            KanbanProfile(name = "default", isDefault = true),
        )

    private fun board(vararg tasks: KanbanTask) =
        KanbanBoardResponse(columns = tasks.groupBy { it.status }.map { (name, list) -> KanbanColumn(name, list) })

    private fun task(
        id: String,
        status: String,
        assignee: String? = "inbox",
        createdAt: Long = id.hashCode().toLong(),
    ) = KanbanTask(id = id, title = "Task $id", status = status, assignee = assignee, createdAt = createdAt)

    @Test
    fun `maps statuses to fleet columns and drops archived`() {
        val source = LiveFleetSource()
        source.apply(
            board(
                task("a", "todo"),
                task("b", "running"),
                task("c", "blocked"),
                task("d", "review"),
                task("e", "done"),
                task("f", "archived"),
            ),
            profiles,
            orchestrator = "cos",
        )
        val snap = source.snapshot()
        assertEquals(1, snap.inColumn(FleetColumn.QUEUED).size)
        assertEquals(1, snap.inColumn(FleetColumn.WORKING).size)
        assertEquals(2, snap.inColumn(FleetColumn.NEEDS_YOU).size)
        assertEquals(1, snap.inColumn(FleetColumn.DONE).size)
        assertEquals(5, snap.tasks.size)
        assertTrue(snap.inColumn(FleetColumn.NEEDS_YOU).all { it.ask != null })
    }

    @Test
    fun `bots come from profiles with the orchestrator as hub and unused default hidden`() {
        val source = LiveFleetSource()
        source.apply(board(task("a", "todo", assignee = "ledger")), profiles, orchestrator = "cos")
        val bots = source.snapshot().bots
        assertEquals(listOf("cos", "inbox", "ledger", "work"), bots.map { it.id })
        assertEquals("cos", bots.single { it.isHub }.id)
    }

    @Test
    fun `first poll is quiet and later changes become events and packets`() {
        val source = LiveFleetSource()
        source.apply(board(task("a", "todo")), profiles, "cos")
        assertTrue(source.snapshot().events.isEmpty())
        assertTrue(source.snapshot().packets.isEmpty())

        source.advance(1_000)
        source.apply(board(task("a", "running", assignee = "work"), task("b", "blocked")), profiles, "cos")
        val snap = source.snapshot()
        val packets = snap.packets.map { it.from to it.to }
        assertTrue(("inbox" to "work") in packets)
        assertTrue((FLEET_ROUTER_ID to "inbox") in packets)
        assertEquals(2, snap.events.size)
        assertEquals("work", snap.tasks.first { it.title == "Task a" }.owner)
    }

    @Test
    fun `needs-you transition flies to you and packets expire`() {
        val source = LiveFleetSource()
        source.apply(board(task("a", "running")), profiles, "cos")
        source.apply(board(task("a", "review")), profiles, "cos")
        assertTrue(source.snapshot().packets.any { it.to == FLEET_YOU_ID })
        source.advance(5_000)
        assertTrue(source.snapshot().packets.isEmpty())
    }

    @Test
    fun `task ids stay stable across polls`() {
        val source = LiveFleetSource()
        source.apply(board(task("a", "todo"), task("b", "todo")), profiles, "cos")
        val first = source.snapshot().tasks.associate { it.title to it.id }
        source.apply(board(task("b", "running"), task("a", "done")), profiles, "cos")
        val second = source.snapshot().tasks.associate { it.title to it.id }
        assertEquals(first, second)
    }

    @Test
    fun `progress uses step counts or is unknown`() {
        val source = LiveFleetSource()
        source.apply(
            board(
                task("a", "running").copy(progress = TaskProgress(done = 1, total = 4)),
                task("b", "running"),
            ),
            profiles,
            "cos",
        )
        val byTitle = source.snapshot().tasks.associateBy { it.title }
        assertEquals(0.25f, byTitle.getValue("Task a").progress, 0.001f)
        assertEquals(LiveFleetSource.PROGRESS_UNKNOWN, byTitle.getValue("Task b").progress, 0f)
    }

    @Test
    fun `done column keeps the latest five but counts all`() {
        val source = LiveFleetSource()
        val done = (1..8).map { task("d$it", "done", createdAt = it.toLong()) }.toTypedArray()
        source.apply(board(*done), profiles, "cos")
        val snap = source.snapshot()
        assertEquals(5, snap.inColumn(FleetColumn.DONE).size)
        assertEquals(8, snap.doneToday)
    }
}
