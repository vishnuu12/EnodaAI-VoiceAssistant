package com.vishnu.assistant.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Groq's OpenAI-compatible chat-completions endpoint.
 *
 * The app talks to Groq directly, so the assistant keeps working
 * (LLM + web-grounded answers) even when the EnodaAI backend is
 * asleep. The API key is supplied per request from Settings.
 */
interface GroqApi {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}

/* ------------------------------------------------------------------
 * Request
 * ------------------------------------------------------------------ */

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.6,
    @SerializedName("max_tokens")
    val maxTokens: Int = 500
)

data class GroqMessage(
    val role: String,
    val content: String
)

/* ------------------------------------------------------------------
 * Response
 * ------------------------------------------------------------------ */

data class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList()
)

data class GroqChoice(
    val message: GroqMessage? = null
)
