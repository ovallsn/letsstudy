package com.oriol.letsstudy.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

interface LocalStudyEngine : AutoCloseable {
    suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String? = null): String
    override fun close()
}

fun interface LocalStudyEngineFactory {
    suspend fun create(modelFile: File): LocalStudyEngine
}

class LiteRtLocalStudyEngineFactory(
    private val cacheDirectory: File,
) : LocalStudyEngineFactory {
    override suspend fun create(modelFile: File): LocalStudyEngine = withContext(Dispatchers.IO) {
        val engine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDirectory.absolutePath,
            ),
        )
        try {
            engine.initialize()
            LiteRtLocalStudyEngine(engine)
        } catch (error: Exception) {
            engine.close()
            throw error
        }
    }
}

private class LiteRtLocalStudyEngine(
    private val engine: Engine,
) : LocalStudyEngine {
    override suspend fun generate(prompt: String, maxOutputTokens: Int, responseFormatSchema: String?): String = withContext(Dispatchers.IO) {
        require(maxOutputTokens > 0) { "maxOutputTokens must be positive" }
        val output = StringBuilder()
        engine.createConversation(ConversationConfig(enableResponseFormat = responseFormatSchema != null)).use { conversation ->
            conversation.sendMessageAsync(
                text = prompt,
                maxOutputToken = maxOutputTokens,
                responseFormat = responseFormatSchema?.let(ResponseFormat::json),
            ).collect { message ->
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .forEach { output.append(it.text) }
            }
        }
        output.toString().trim().also {
            if (it.isEmpty()) throw LocalModelException("The on-device model returned an empty response.")
        }
    }

    override fun close() = engine.close()
}
