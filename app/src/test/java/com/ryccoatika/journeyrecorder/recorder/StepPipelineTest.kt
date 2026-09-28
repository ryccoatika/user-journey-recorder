package com.ryccoatika.journeyrecorder.recorder

import com.ryccoatika.journeyrecorder.data.db.Confidence
import com.ryccoatika.journeyrecorder.data.db.EventType
import com.ryccoatika.journeyrecorder.data.db.JourneyCount
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyEventEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StepPipelineTest {

    private class FakeDao : JourneyDao {
        val events = mutableListOf<JourneyEventEntity>()

        override suspend fun insertEvent(event: JourneyEventEntity): Long {
            events.add(event)
            return events.size.toLong()
        }

        // Unused surface.
        override suspend fun insertJourney(journey: JourneyEntity): Long = 0
        override suspend fun rename(id: Long, name: String) = Unit
        override suspend fun finish(id: Long, endedAt: Long, status: JourneyStatus) = Unit
        override suspend fun setNoElementIds(id: Long, noElementIds: Boolean) = Unit
        override suspend fun deleteJourney(id: Long) = Unit
        override fun observeJourneys(): Flow<List<JourneyEntity>> = emptyFlow()
        override suspend fun getJourney(id: Long): JourneyEntity? = null
        override fun observeJourney(id: Long): Flow<JourneyEntity?> = emptyFlow()
        override suspend fun getUnfinishedJourneys(): List<JourneyEntity> = emptyList()
        override fun observeEvents(journeyId: Long): Flow<List<JourneyEventEntity>> = emptyFlow()
        override suspend fun getEvents(journeyId: Long): List<JourneyEventEntity> = emptyList()
        override fun observeEventCount(journeyId: Long): Flow<Int> = emptyFlow()
        override fun observeEventCounts(): Flow<List<JourneyCount>> = emptyFlow()
        override suspend fun getLastEventTime(journeyId: Long): Long? = null
        override suspend fun redactEvent(eventId: Long) = Unit
    }

    private val emailElement = ElementInfo(
        elementId = "com.target:id/email",
        hint = "Email",
        className = "android.widget.EditText",
        bounds = "[0,100][1080,200]",
        isEditable = true,
    )

    private val buttonElement = ElementInfo(
        elementId = "com.target:id/submit",
        text = "Submit",
        className = "android.widget.Button",
        bounds = "[0,300][1080,400]",
    )

    private fun textCapture(
        text: String,
        uptimeMs: Long,
        beforeNonEmpty: Boolean = false,
        element: ElementInfo = emailElement,
        fieldKey: String? = "id:com.target:id/email",
    ) = RawCapture.Text(
        uptimeMs = uptimeMs,
        wallClockMs = 1_000_000 + uptimeMs,
        screenName = "LoginActivity",
        confidence = Confidence.NORMAL,
        element = element,
        windowId = 1,
        fieldKey = fieldKey,
        text = text,
        beforeTextNonEmpty = beforeNonEmpty,
    )

    private fun clickCapture(uptimeMs: Long, element: ElementInfo = buttonElement) =
        RawCapture.Click(
            uptimeMs = uptimeMs,
            wallClockMs = 1_000_000 + uptimeMs,
            screenName = "LoginActivity",
            confidence = Confidence.NORMAL,
            element = element,
        )

    @Test
    fun `keystrokes coalesce into one TEXT_INPUT step with the final text`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        pipeline.begin(1)
        pipeline.submit(textCapture("h", uptimeMs = 0))
        pipeline.submit(textCapture("he", uptimeMs = 100))
        pipeline.submit(textCapture("hello", uptimeMs = 200))
        // advanceUntilIdle does not advance time for delays living only in
        // backgroundScope (background work never blocks idleness) — advance
        // past the quiet window explicitly.
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, dao.events.size)
        val step = dao.events.single()
        assertEquals(EventType.TEXT_INPUT, step.eventType)
        assertEquals("hello", step.typedText)
        assertEquals(false, step.masked)
        assertEquals(0, step.sequence)
        assertEquals("com.target:id/email", step.elementId)
    }

    @Test
    fun `click flushes pending text first - TEXT_INPUT sequenced before CLICK`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        pipeline.begin(1)
        pipeline.submit(textCapture("qa@test.com", uptimeMs = 0))
        pipeline.submit(clickCapture(uptimeMs = 50))
        pipeline.flushAndEnd()

        assertEquals(2, dao.events.size)
        assertEquals(EventType.TEXT_INPUT, dao.events[0].eventType)
        assertEquals("qa@test.com", dao.events[0].typedText)
        assertEquals(0, dao.events[0].sequence)
        assertEquals(EventType.CLICK, dao.events[1].eventType)
        assertEquals(1, dao.events[1].sequence)
    }

    @Test
    fun `emptying a non-empty field records an explicit clear-text step`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        pipeline.begin(1)
        pipeline.submit(textCapture("", uptimeMs = 0, beforeNonEmpty = true))
        pipeline.flushAndEnd()

        assertEquals(1, dao.events.size)
        val step = dao.events.single()
        assertEquals(EventType.TEXT_INPUT, step.eventType)
        assertEquals("", step.typedText)
        assertEquals(false, step.masked)
    }

    @Test
    fun `identical click pair within 100ms is deduplicated`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        pipeline.begin(1)
        pipeline.submit(clickCapture(uptimeMs = 1_000))
        pipeline.submit(clickCapture(uptimeMs = 1_050)) // echo
        pipeline.submit(clickCapture(uptimeMs = 1_400)) // genuine second tap
        pipeline.flushAndEnd()

        assertEquals(2, dao.events.size)
        assertTrue(dao.events.all { it.eventType == EventType.CLICK })
        assertEquals(listOf(0, 1), dao.events.map { it.sequence })
    }

    @Test
    fun `select right after click on the same element is dropped`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        pipeline.begin(1)
        pipeline.submit(clickCapture(uptimeMs = 1_000))
        pipeline.submit(
            RawCapture.Select(
                uptimeMs = 1_100, // within 300 ms of the click
                wallClockMs = 1_001_100,
                screenName = "LoginActivity",
                confidence = Confidence.NORMAL,
                element = buttonElement,
            ),
        )
        pipeline.flushAndEnd()

        assertEquals(1, dao.events.size)
        assertEquals(EventType.CLICK, dao.events.single().eventType)
    }

    @Test
    fun `password field is masked and typedText never persisted`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        val passwordElement = ElementInfo(
            elementId = "com.target:id/password",
            hint = "Password",
            className = "android.widget.EditText",
            bounds = "[0,100][1080,200]",
            isPassword = true,
            isEditable = true,
        )
        pipeline.begin(1)
        pipeline.submit(
            textCapture(
                "hunter2",
                uptimeMs = 0,
                element = passwordElement,
                fieldKey = "id:com.target:id/password",
            ),
        )
        pipeline.flushAndEnd()

        assertEquals(1, dao.events.size)
        val step = dao.events.single()
        assertEquals(EventType.TEXT_INPUT, step.eventType)
        assertEquals(true, step.masked)
        assertNull(step.typedText)
    }

    @Test
    fun `luhn-valid final text is masked at flush time`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        val cleanField = ElementInfo(
            elementId = "com.target:id/notes",
            hint = "Notes",
            className = "android.widget.EditText",
            isEditable = true,
        )
        pipeline.begin(1)
        pipeline.submit(
            textCapture(
                "4111 1111 1111 1111",
                uptimeMs = 0,
                element = cleanField,
                fieldKey = "id:com.target:id/notes",
            ),
        )
        pipeline.flushAndEnd()

        val step = dao.events.single()
        assertEquals(true, step.masked)
        assertNull(step.typedText)
    }

    @Test
    fun `scroll deltas on the same node are summed into one step`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        val list = ElementInfo(
            elementId = "com.target:id/list",
            className = "androidx.recyclerview.widget.RecyclerView",
            bounds = "[0,0][1080,1920]",
        )
        fun scroll(uptimeMs: Long, dy: Int) = RawCapture.Scroll(
            uptimeMs = uptimeMs,
            wallClockMs = 1_000_000 + uptimeMs,
            screenName = "FeedActivity",
            confidence = Confidence.NORMAL,
            element = list,
            nodeKey = "id:com.target:id/list",
            deltaX = 0,
            deltaY = dy,
            fromIndex = null,
            toIndex = null,
        )
        pipeline.begin(1)
        pipeline.submit(scroll(0, 100))
        pipeline.submit(scroll(100, 150))
        pipeline.submit(scroll(200, 50))
        advanceTimeBy(600) // past the 500 ms quiet timer (see note above)
        runCurrent()

        assertEquals(1, dao.events.size)
        val step = dao.events.single()
        assertEquals(EventType.SCROLL, step.eventType)
        assertEquals("down 300px", step.elementText)
        assertNull(step.typedText)
    }

    @Test
    fun `click on an editable field never persists its content as elementText`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        val cardField = ElementInfo(
            elementId = "com.target:id/card_number",
            text = "4111 1111 1111 1111", // node text of an editable field IS its content
            hint = "Card number",
            className = "android.widget.EditText",
            bounds = "[0,100][1080,200]",
            isEditable = true,
        )
        pipeline.begin(1)
        pipeline.submit(clickCapture(uptimeMs = 0, element = cardField))
        pipeline.flushAndEnd()

        val step = dao.events.single()
        assertEquals(EventType.CLICK, step.eventType)
        assertEquals("Card number", step.elementText)
        assertEquals("com.target:id/card_number", step.elementId)
    }

    @Test
    fun `flushAndEnd closes the journey and later captures are ignored`() = runTest {
        val dao = FakeDao()
        val pipeline = StepPipeline(dao, backgroundScope)
        pipeline.begin(1)
        pipeline.submit(clickCapture(uptimeMs = 0))
        pipeline.flushAndEnd()
        pipeline.submit(clickCapture(uptimeMs = 5_000))
        advanceUntilIdle()

        assertEquals(1, dao.events.size)
    }
}
