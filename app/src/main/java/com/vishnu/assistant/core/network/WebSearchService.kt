package com.vishnu.assistant.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Keyless web search used in Online mode.
 *
 * DuckDuckGo's HTML endpoint is fetched and the top results are
 * extracted. The snippets are then handed to the LLM as context so
 * the spoken answer is grounded in fresh web results
 * (voice request -> result search -> voice response).
 */
class WebSearchService(
    private val client: OkHttpClient = defaultClient()
) {

    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String
    )

    suspend fun search(
        query: String,
        maxResults: Int = 5
    ): List<SearchResult> = withContext(Dispatchers.IO) {

        val cleaned = query.trim()

        if (cleaned.isEmpty()) {
            return@withContext emptyList()
        }

        val url =
            "https://html.duckduckgo.com/html/?q=" +
                java.net.URLEncoder.encode(
                    cleaned,
                    "UTF-8"
                )

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "en-IN,en;q=0.9,ta;q=0.8")
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                response.body?.string() ?: ""
            }
        } catch (exception: Exception) {
            return@withContext emptyList()
        }

        parseResults(body, maxResults)
    }

    /**
     * Extracts result anchors and snippets from the DuckDuckGo
     * HTML page. Deliberately defensive: any layout change simply
     * yields fewer (or no) results instead of crashing.
     */
    private fun parseResults(
        html: String,
        maxResults: Int
    ): List<SearchResult> {

        val results = mutableListOf<SearchResult>()

        val anchorPattern = Regex(
            pattern = """<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val snippetPattern = Regex(
            pattern = """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val snippets = snippetPattern
            .findAll(html)
            .map { cleanHtml(it.groupValues[1]) }
            .toList()

        anchorPattern.findAll(html).forEachIndexed { index, match ->

            if (index >= maxResults) {
                return@forEachIndexed
            }

            val rawUrl = match.groupValues[1]
            val title = cleanHtml(match.groupValues[2])

            val realUrl = decodeDuckDuckGoUrl(rawUrl)

            if (title.isBlank() || realUrl.isBlank()) {
                return@forEachIndexed
            }

            results.add(
                SearchResult(
                    title = title,
                    url = realUrl,
                    snippet = snippets.getOrElse(index) { "" }
                )
            )
        }

        return results
    }

    /**
     * DuckDuckGo wraps outbound links as
     * //duckduckgo.com/l/?uddg=<encoded-url>&rut=...
     */
    private fun decodeDuckDuckGoUrl(rawUrl: String): String {

        return try {

            if (rawUrl.contains("uddg=")) {

                val encoded = rawUrl
                    .substringAfter("uddg=")
                    .substringBefore("&")

                URLDecoder.decode(encoded, "UTF-8")
            } else {
                rawUrl
            }
        } catch (exception: Exception) {
            rawUrl
        }
    }

    private fun cleanHtml(value: String): String {

        // Strip tags, then let Android's built-in HTML
        // unescaping handle every entity form.
        val stripped = value
            .replace(Regex("<[^>]*>"), "")
            .trim()

        val unescaped = try {
            android.text.Html
                .fromHtml(stripped, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString()
        } catch (exception: Exception) {
            stripped
        }

        return unescaped.replace(Regex("\\s+"), " ").trim()
    }

    companion object {

        fun defaultClient(): OkHttpClient {

            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }
    }
}
