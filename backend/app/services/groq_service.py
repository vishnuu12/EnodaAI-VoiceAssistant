import logging

from groq import AsyncGroq

from app.services.ai_service import (
    AIService,
    AIServiceError,
    build_system_prompt,
)
from app.services.conversation_memory import ConversationMessage

logger = logging.getLogger("enodaai.ai.groq")


class GroqService(AIService):
    """Fallback provider: Groq free tier (fast open models)."""

    name = "groq"

    def __init__(self, api_key: str, model: str):
        self._client = AsyncGroq(api_key=api_key)
        self._model = model

    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:

        try:
            messages = [
                {
                    "role": "system",
                    "content": build_system_prompt(language),
                }
            ]

            # -----------------------------------------------------
            # Add previous conversation messages
            # -----------------------------------------------------

            for item in history or []:

                role = (
                    "user"
                    if item.role == "user"
                    else "assistant"
                )

                messages.append(
                    {
                        "role": role,
                        "content": item.content,
                    }
                )

            # -----------------------------------------------------
            # Add current user message
            # -----------------------------------------------------

            messages.append(
                {
                    "role": "user",
                    "content": message,
                }
            )

            # -----------------------------------------------------
            # Ask Groq
            # -----------------------------------------------------

            completion = await self._client.chat.completions.create(
                model=self._model,
                messages=messages,
            )

        except Exception as exc:
            raise AIServiceError(
                f"groq request failed: {exc}"
            ) from exc

        text = (
            completion.choices[0].message.content or ""
        ).strip()

        if not text:
            raise AIServiceError(
                "groq returned an empty response"
            )

        return text