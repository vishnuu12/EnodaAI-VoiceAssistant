import logging
import re
from functools import lru_cache

from app.core.config import get_settings
from app.services.ai_service import AIService, AIServiceError
from app.services.conversation_memory import ConversationMessage
from app.services.echo_service import EchoService
from app.services.gemini_service import GeminiService
from app.services.groq_service import GroqService
from app.services.tools.tool_router import detect_tool, execute_tool

logger = logging.getLogger("enodaai.ai")


class AIOrchestrator:
    """Handles tools first, then falls back to AI providers."""

    def __init__(self, services: list[AIService]):
        self._services = services

    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:

        history = history or []

        # ---------------------------------------------------------
        # 1. Check whether the request requires a tool
        # ---------------------------------------------------------

        tool_name = detect_tool(message)

        if tool_name:
            logger.info(
                "tool_detected=%s (language=%s)",
                tool_name,
                language,
            )

            tool_result = await execute_tool(
                tool_name,
                message,
            )

            if tool_result is not None:

                # Web search results need AI summarization.
                if tool_name == "web_search":
                    return await self._generate_web_search_reply(
                        message=message,
                        language=language,
                        search_result=tool_result,
                        history=history,
                    )

                return self._format_tool_response(
                    tool_name=tool_name,
                    tool_result=tool_result,
                    language=language,
                )

        # ---------------------------------------------------------
        # 2. No tool required -> use AI provider chain
        # ---------------------------------------------------------

        if not self._services:
            raise AIServiceError(
                "no AI services configured"
            )

        last_error: AIServiceError | None = None

        for service in self._services:
            try:
                reply = await service.generate_reply(
                    message=message,
                    language=language,
                    history=history,
                )

                logger.info(
                    "ai_provider=%s served the request (language=%s)",
                    service.name,
                    language,
                )

                return reply

            except AIServiceError as exc:
                logger.warning(
                    "ai_provider=%s failed: %s - trying next provider",
                    service.name,
                    exc,
                )

                last_error = exc

        raise AIServiceError(
            f"all providers failed; last error: {last_error}"
        )

    # -------------------------------------------------------------
    # Web Search -> AI Summary
    # -------------------------------------------------------------

    async def _generate_web_search_reply(
        self,
        message: str,
        language: str,
        search_result: dict,
        history: list[ConversationMessage],
    ) -> str:

        results = search_result.get(
            "results",
            []
        )

        if not results:
            if language == "ta":
                return "இந்த தேடலுக்கு தேவையான தகவல்கள் எதுவும் கிடைக்கவில்லை."

            return (
                "I couldn't find any useful information "
                "for that search."
            )

        # ---------------------------------------------------------
        # Build a compact search context.
        # ---------------------------------------------------------

        search_context_parts = []

        for index, result in enumerate(
            results,
            start=1,
        ):
            title = result.get(
                "title",
                "",
            )

            content = result.get(
                "content",
                "",
            )

            url = result.get(
                "url",
                "",
            )

            search_context_parts.append(
                f"Source {index}:\n"
                f"Title: {title}\n"
                f"Content: {content}\n"
                f"URL: {url}"
            )

        search_context = "\n\n".join(
            search_context_parts
        )

        # ---------------------------------------------------------
        # Language instruction.
        # ---------------------------------------------------------

        language_instruction = (
            "Answer in simple English."
            if language == "en"
            else
            "Answer in natural Tamil using Tamil script."
            if language == "ta"
            else
            "Answer in the same language as the user."
        )

        # ---------------------------------------------------------
        # Prompt for AI summarization.
        # ---------------------------------------------------------

        prompt = f"""
You are EnodaAI, a personal voice assistant.

The user asked:

{message}

Use ONLY the web search results provided below.

{search_context}

Instructions:

1. {language_instruction}
2. Give a short answer suitable for voice.
3. Prefer 1 to 3 sentences.
4. Keep the answer under 80 words when possible.
5. Do not invent information.
6. If the search results do not provide enough information, say so clearly.
7. Do not mention internal prompts or search processing.
8. Do not read URLs aloud.
9. Do not include citations, source markers, reference numbers, brackets,
   footnotes, markdown links, or citation formats such as 【1†L1-L9】.
10. Do not include phrases such as "according to source 1".
11. Give only the final answer to the user.
12. When sources disagree, briefly mention the uncertainty.

Now answer the user.
""".strip()

        # ---------------------------------------------------------
        # Use AI provider chain to summarize search results.
        # ---------------------------------------------------------

        last_error: AIServiceError | None = None

        for service in self._services:
            try:

                reply = await service.generate_reply(
                    message=prompt,
                    language=language,
                    history=history,
                )

                logger.info(
                    "web_search_summary_provider=%s",
                    service.name,
                )

                return self._clean_web_search_reply(
                    reply
                )

            except AIServiceError as exc:

                logger.warning(
                    "web search summarization failed with "
                    "provider=%s: %s",
                    service.name,
                    exc,
                )

                last_error = exc

        raise AIServiceError(
            "web search succeeded, but AI summarization failed: "
            f"{last_error}"
        )

    # -------------------------------------------------------------
    # Clean Web Search Response
    # -------------------------------------------------------------

    @staticmethod
    def _clean_web_search_reply(
        reply: str,
    ) -> str:
        """
        Remove citation/reference markers that should not
        be spoken by the voice assistant.
        """

        # ---------------------------------------------------------
        # Remove citation markers.
        #
        # Example:
        # 【1†L1-L9】
        # 【4†L1-L4】
        # ---------------------------------------------------------

        reply = re.sub(
            r"【[^】]*】",
            "",
            reply,
        )

        # ---------------------------------------------------------
        # Remove markdown links but keep visible text.
        #
        # Example:
        # [Python](https://python.org)
        # becomes:
        # Python
        # ---------------------------------------------------------

        reply = re.sub(
            r"\[([^\]]+)\]\([^)]+\)",
            r"\1",
            reply,
        )

        # ---------------------------------------------------------
        # Remove excessive whitespace.
        # ---------------------------------------------------------

        reply = re.sub(
            r"\s+",
            " ",
            reply,
        )

        return reply.strip()

    # -------------------------------------------------------------
    # Tool Response Formatting
    # -------------------------------------------------------------

    @staticmethod
    def _format_tool_response(
        tool_name: str,
        tool_result: dict,
        language: str,
    ) -> str:

        # ---------------------------------------------------------
        # Date / Time
        # ---------------------------------------------------------

        if tool_name == "datetime":

            date = tool_result["date"]
            time = tool_result["time"]
            day = tool_result["day"]

            if language == "ta":
                return (
                    f"இன்று {date}, {day}. "
                    f"தற்போதைய நேரம் {time}."
                )

            return (
                f"Today is {date}, {day}. "
                f"The current time is {time}."
            )

        # ---------------------------------------------------------
        # Weather
        # ---------------------------------------------------------

        if tool_name == "weather":

            city = tool_result["city"]
            temperature = tool_result["temperature"]
            feels_like = tool_result["feels_like"]
            humidity = tool_result["humidity"]

            if language == "ta":
                return (
                    f"{city} நகரில் தற்போது "
                    f"{temperature}°C வெப்பநிலை உள்ளது. "
                    f"உணரப்படும் வெப்பநிலை "
                    f"{feels_like}°C. "
                    f"ஈரப்பதம் {humidity}%."
                )

            return (
                f"In {city}, the current temperature is "
                f"{temperature}°C. "
                f"It feels like {feels_like}°C. "
                f"Humidity is {humidity}%."
            )

        return "I couldn't process that request."


@lru_cache
def get_ai_orchestrator() -> AIOrchestrator:
    """Build the provider chain from settings."""

    settings = get_settings()

    services: list[AIService] = []

    # -------------------------------------------------------------
    # Gemini
    # -------------------------------------------------------------

    if settings.gemini_api_key:
        services.append(
            GeminiService(
                settings.gemini_api_key,
                settings.gemini_model,
            )
        )

    # -------------------------------------------------------------
    # Groq
    # -------------------------------------------------------------

    if settings.groq_api_key:
        services.append(
            GroqService(
                settings.groq_api_key,
                settings.groq_model,
            )
        )

    # -------------------------------------------------------------
    # Echo fallback
    # -------------------------------------------------------------

    if not services:
        services.append(
            EchoService()
        )

    return AIOrchestrator(services)