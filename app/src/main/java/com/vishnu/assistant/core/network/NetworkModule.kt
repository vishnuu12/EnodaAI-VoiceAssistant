package com.vishnu.assistant.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds and holds the single Retrofit instances.
 *
 * 1. enodaApi -> the public EnodaAI FastAPI backend (optional).
 * 2. groqApi -> direct LLM access via Groq's OpenAI-compatible
 *    endpoint, so Online mode works even when the backend sleeps.
 */
object NetworkModule {

    private const val BACKEND_URL =
        "https://enodaai.onrender.com/"

    private const val GROQ_BASE_URL =
        "https://api.groq.com/openai/v1/"

    private val okHttpClient: OkHttpClient by lazy {

        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val enodaApi: EnodaApi by lazy {

        Retrofit.Builder()
            .baseUrl(BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EnodaApi::class.java)
    }

    val groqApi: GroqApi by lazy {

        Retrofit.Builder()
            .baseUrl(GROQ_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }

    val webSearchService: WebSearchService by lazy {

        WebSearchService()
    }
}
