package com.oriol.letsstudy.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalModelManagerTest {
    @Test
    fun missingModelIsReportedWithoutOpeningEngine() {
        var openCalls = 0
        val manager = manager(File("missing-model.litertlm")) { openCalls++; FakeEngine() }

        assertEquals(ModelState.NotDownloaded, manager.state.value)
        assertThrows(ModelUnavailableException::class.java) { runBlocking { manager.ensureLoaded() } }
        assertEquals(0, openCalls)
    }

    @Test
    fun engineIsOpenedOnlyOnceAndReused() {
        runBlocking {
            val model = File.createTempFile("model", ".litertlm")
            var openCalls = 0
            val engine = FakeEngine()
            val manager = manager(model) { openCalls++; engine }

            manager.ensureLoaded()
            manager.ensureLoaded()

            assertEquals(1, openCalls)
            assertEquals(ModelState.Ready, manager.state.value)
            model.delete()
        }
    }

    @Test
    fun openFailureBecomesActionableFailedState() {
        runBlocking {
            val model = File.createTempFile("model", ".litertlm")
            val manager = manager(model) { throw IllegalStateException("native engine failed") }

            assertThrows(LocalModelException::class.java) { runBlocking { manager.ensureLoaded() } }
            assertTrue(manager.state.value is ModelState.Failed)
            model.delete()
        }
    }

    @Test
    fun closeReleasesLoadedEngine() {
        runBlocking {
            val model = File.createTempFile("model", ".litertlm")
            val engine = FakeEngine()
            val manager = manager(model) { engine }

            manager.ensureLoaded()
            manager.close()

            assertEquals(1, engine.closeCalls)
            model.delete()
        }
    }

    private fun manager(model: File, create: () -> LocalStudyEngine) = LocalModelManager(
        modelFile = model,
        ioDispatcher = Dispatchers.Unconfined,
        engineFactory = LocalStudyEngineFactory { create() },
    )

    private class FakeEngine : LocalStudyEngine {
        var closeCalls = 0
        override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?): String = "{}"
        override fun close() { closeCalls++ }
    }
}
