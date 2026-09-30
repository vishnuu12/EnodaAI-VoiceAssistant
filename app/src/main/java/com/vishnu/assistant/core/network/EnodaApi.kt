package com.vishnu.assistant.core.network

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The EnodaAI backend contract, expressed as a Retrofit interface.
 * Each method = one endpoint. Retrofit generates the implementation.
 */
interface EnodaApi {

    @POST("api/v1/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}
