package com.vishnu.assistant.core.network

import com.google.gson.annotations.SerializedName

/**
 * Kotlin twins of the backend's Pydantic models
 * (app/schemas/chat.py).
 *
 * Field names match the backend JSON keys.
 */
data class ChatRequest(
    val text: String,
    val language: String? = null,

    @SerializedName("conversation_id")
    val conversationId: String? = null
)

data class ChatResponse(
    val reply: String
)