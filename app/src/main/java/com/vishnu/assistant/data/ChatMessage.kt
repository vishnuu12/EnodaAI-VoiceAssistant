package com.vishnu.assistant.data

enum class ChatRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val role: ChatRole,
    val text: String
)