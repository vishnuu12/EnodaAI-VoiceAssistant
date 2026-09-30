package com.vishnu.assistant.data

import android.util.Log
import com.vishnu.assistant.core.network.ChatRequest
import com.vishnu.assistant.core.network.EnodaApi
import com.vishnu.assistant.core.network.GroqChatRequest
import com.vishnu.assistant.core.network.GroqMessage
import com.vishnu.assistant.core.network.NetworkModule
import com.vishnu.assistant.utils.LocalBrain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for conversations.
 *
 * Routing:
 *
 * OFFLINE mode
 *   -> LocalBrain answers from the device (time, date, battery,
 *      math, small talk). App opening and calls are already
 *      handled locally before the repository is reached.
 *
 * ONLINE mode
 *   1. Groq LLM (when a Groq API key is set in Settings),
 *      with fresh DuckDuckGo web results attached as context
 *      when the question looks search-worthy.
 *   2. Otherwise the EnodaAI FastAPI backend (which itself does
 *      Gemini -> Groq with Tavily web search).
 *   3. If both fail, the LocalBrain fallback keeps the user
 *      informed with a friendly, spoken-style message.
 */
class ChatRepository(
    private val api: EnodaApi = NetworkModule.enodaApi,
    private val groqApi:
    com.vishnu.assistant.core.network.GroqApi =
        NetworkModule.groqApi,

    private val webSearchService:
    com.vishnu.assistant.core.network.WebSearchService =
        NetworkModule.webSearchService
) {

    /** Outcome of one request: either the reply, or a friendly error. */
    sealed class ChatResult {
        data class Success(val reply: String) : ChatResult()
        data class Failure(val message: String) : ChatResult()
    }

    /**
     * User preferences snapshot, supplied per request.
     */
    data class ChatSettings(
        val online: Boolean,
        val groqApiKey: String,
        val webSearchEnabled: Boolean
    )

    /**
     * Short conversation memory for the LLM.
     */
    private val history = mutableListOf<GroqMessage>()

    fun clearHistory() {
        synchronized(history) {
            history.clear()
        }
    }

    fun resetForNewConversation() {
        clearHistory()
    }

    /**
     * English -> "en"
     * Tamil   -> "ta"
     */
    suspend fun sendMessage(
        text: String,
        language: String,
        conversationId: String,
        settings: ChatSettings,
        localBrain: LocalBrain
    ): ChatResult = withContext(Dispatchers.IO) {

        val tamil = language.equals("ta", ignoreCase = true)

        // -----------------------------------------------------
        // 1. Offline mode: answer from the device.
        // -----------------------------------------------------

        if (!settings.online) {

            val brainLanguage =
                if (tamil) {
                    LocalBrain.Language.TAMIL
                } else {
                    LocalBrain.Language.ENGLISH
                }

            val localAnswer = localBrain.answer(
                message = text,
                language = brainLanguage
            )

            val reply = localAnswer ?: if (tamil) {
                "இப்போ Offline mode-ல இருக்கேன். இணையம் " +
                    "இல்லாம இந்த கேள்விக்கு பதில் சொல்ல " +
                    "முடியாது. Online mode-க்கு மாறவும், அல்லது " +
                    "app திறக்க, call பண்ண, நேரம் தேதி கேக்கவும்."
            } else {
                "I am in Offline mode right now, so I cannot " +
                    "answer this without the internet. Switch to " +
                    "Online mode, or ask me to open apps, make " +
                    "calls, or tell the time and date."
            }

            return@withContext ChatResult.Success(reply)
        }

        // -----------------------------------------------------
        // 2. Online with a Groq key: web search + direct LLM.
        // -----------------------------------------------------

        if (settings.groqApiKey.isNotEmpty()) {

            return@withContext try {

                val reply = generateGroqReply(
                    text = text,
                    tamil = tamil,
                    settings = settings
                )

                ChatResult.Success(reply)

            } catch (exception: Exception) {

                Log.w(
                    "EnodaAI",
                    "Groq failed, trying backend",
                    exception
                )

                askBackendFallback(
                    text = text,
                    language = language,
                    conversationId = conversationId,
                    tamil = tamil
                )
            }
        }

        // -----------------------------------------------------
        // 3. Online without a Groq key: the EnodaAI backend.
        // -----------------------------------------------------

        askBackendFallback(
            text = text,
            language = language,
            conversationId = conversationId,
            tamil = tamil
        )
    }

    /* ---------------------------------------------------------
     * Groq direct path
     * --------------------------------------------------------- */

    private suspend fun generateGroqReply(
        text: String,
        tamil: Boolean,
        settings: ChatSettings
    ): String {

        val userContent = StringBuilder(text)

        // Web search grounding: voice request -> result search
        // -> voice response.
        var searched = false

        if (
            settings.webSearchEnabled &&
            looksSearchWorthy(text)
        ) {

            val results = webSearchService.search(text)

            if (results.isNotEmpty()) {

                searched = true

                val context = results
                    .take(5)
                    .joinToString("\n") { result ->

                        "Title: ${result.title}\n" +
                            "URL: ${result.url}\n" +
                            "Snippet: ${result.snippet}"
                    }

                userContent.append("\n\n[WEB SEARCH RESULTS]\n")
                userContent.append(context)
                userContent.append("\n[END WEB SEARCH RESULTS]")
            }
        }

        val systemPrompt = buildString {

            append(
                "You are EnodaAI, a warm and friendly voice " +
                    "assistant in the Tamil Nadu style. "
            )

            if (tamil) {
                append(
                    "Reply in Tamil (native script), using simple " +
                        "spoken-style Tamil like a Tamil Nadu local. " +
                        "Light Tanglish words are fine. "
                )
            } else {
                append(
                    "Reply in English with a light Indian English " +
                        "flavour, and mix a natural Tamil word or two " +
                        "only when it helps warmth. "
                )
            }

            append(
                "Your reply is SPOKEN ALOUD, so keep it short - " +
                    "2 to 4 sentences, no markdown, no lists, no URLs. "
            )

            if (searched) {
                append(
                    "Use the WEB SEARCH RESULTS as your primary " +
                        "facts and briefly mention the source name. " +
                        "If the results do not answer the question, " +
                        "say so honestly. "
                )
            } else {
                append(
                    "If you are unsure or the answer needs fresh " +
                        "internet data, say so honestly. "
                )
        }
        }

        val messages = mutableListOf<GroqMessage>()

        messages.add(
            GroqMessage(
                role = "system",
                content = systemPrompt
            )
        )

        synchronized(history) {
            messages.addAll(history.takeLast(HISTORY_LIMIT))
        }

        messages.add(
            GroqMessage(
                role = "user",
                content = userContent.toString()
            )
        )

        val response = groqApi.chatCompletion(
            authorization = "Bearer ${settings.groqApiKey}",
            request = GroqChatRequest(
                model = GROQ_MODEL,
                messages = messages,
                temperature = 0.6,
                maxTokens = 500
            )
        )

        val reply = response.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?: throw IllegalStateException("Empty reply from Groq")

        // Remember this turn for follow-up questions.
        synchronized(history) {

            history.add(
                GroqMessage(
                    role = "user",
                    content = text
                )
            )

            history.add(
                GroqMessage(
                    role = "assistant",
                    content = reply
                )
            )

            while (history.size > HISTORY_LIMIT * 2) {
                history.removeAt(0)
            }
        }

        return reply
    }

    /**
     * Heuristics for when a web search actually adds value.
     */
    private fun looksSearchWorthy(text: String): Boolean {

        val lower = text.lowercase()

        val englishHints = listOf(
            "search", "latest", "news", "today", "price",
            "weather", "who is", "what is", "when is",
            "where is", "score", "release", "current",
            "best", "top", "how to", "recipe", "nearest",
            "rupees", "rate", "stock", "live"
        )

        val tamilHints = listOf(
            "தேடு", "செய்தி", "இன்றைய", "விலை", "வானிலை",
            "மழை", "யார்", "என்ன", "எப்போ", "எங்கே",
            "சமீபத்திய", "நடப்பு", "ரூபாய்"
        )

        val tanglishHints = listOf(
            "eppo", "evlo", "yaru", "enna vila", "seithi",
            "mazhai", "innaiku", "latest", "news"
        )

        return englishHints.any { lower.contains(it) } ||
            tamilHints.any { text.contains(it) } ||
            tanglishHints.any { lower.contains(it) }
    }

    /* ---------------------------------------------------------
     * Backend path
     * --------------------------------------------------------- */

    private suspend fun askBackendFallback(
        text: String,
        language: String,
        conversationId: String,
        tamil: Boolean
    ): ChatResult {

        return try {

            Log.d(
                "EnodaAI",
                "Sending chat - conversationId=$conversationId"
            )

            val response = api.chat(
                ChatRequest(
                    text = text,
                    language = language,
                    conversationId = conversationId
                )
            )

            ChatResult.Success(response.reply)

        } catch (exception: Exception) {

            Log.w(
                "EnodaAI",
                "Backend unreachable",
                exception
            )

            ChatResult.Failure(
                if (tamil) {
                    "சேவையகத்தை அடைய முடியவில்லை. Settings-ல " +
                        "Groq API key கொடுங்க, அப்பதான் நேரடியா " +
                        "பதில் சொல்ல முடியும்."
                } else {
                    "Could not reach the EnodaAI server. Add a " +
                        "free Groq API key in Settings for direct " +
                        "AI answers, or switch to Offline mode."
                }
            )
        }
    }

    companion object {

        private const val GROQ_MODEL = "llama-3.3-70b-versatile"

        private const val HISTORY_LIMIT = 12
    }
}
