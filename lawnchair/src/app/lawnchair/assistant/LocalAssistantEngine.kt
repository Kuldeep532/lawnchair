package app.lawnchair.assistant

import android.content.Context
import com.llamatik.library.platform.LlamaBridge
import java.io.File

/**
 * Minimal on-device chat wrapper around Llamatik/llama.cpp.
 * It loads a downloaded GGUF only; no cloud AI endpoint is used.
 */
object LocalAssistantEngine {
    @Volatile
    private var loadedPath: String? = null

    val isLoaded: Boolean
        get() = loadedPath != null

    @Synchronized
    fun loadIfAvailable(context: Context): Boolean {
        val modelFile = AssistantModelStore.localModelFile(context)
        if (!modelFile.isFile || modelFile.length() < 50_000_000L) return false
        if (loadedPath == modelFile.absolutePath) return true

        return try {
            LlamaBridge.updateGenerateParams(
                temperature = 0.7f,
                maxTokens = 256,
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.1f,
                contextLength = 2048,
                numThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4),
                useMmap = true,
                flashAttention = false,
                batchSize = 128,
                gpuLayers = 0,
            )
            val ready = LlamaBridge.initGenerateModel(modelFile.absolutePath)
            if (ready) loadedPath = modelFile.absolutePath
            ready
        } catch (_: Throwable) {
            loadedPath = null
            false
        }
    }

    fun reply(userMessage: String): String {
        check(isLoaded) { "The local model is not ready yet." }
        val systemPrompt =
            "You are Nexus Assistant, a concise and helpful offline assistant on Android. " +
            "Answer in the language used by the user. Do not claim to have performed actions " +
            "unless the app has confirmed them. If you are unsure, say so."
        val prompt = LlamaBridge.applyChatTemplate(
            listOf("system" to systemPrompt, "user" to userMessage),
            addAssistantPrefix = true,
        ) ?: "$systemPrompt\n\nUser: $userMessage\nAssistant:"
        return LlamaBridge.generate(prompt).trim().ifBlank {
            "I could not create a response. Please try asking in a simpler way."
        }
    }

    @Synchronized
    fun release() {
        if (loadedPath != null) {
            runCatching { LlamaBridge.shutdown() }
            loadedPath = null
        }
    }
}
