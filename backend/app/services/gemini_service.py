import logging

from google import genai
from google.genai import types

from app.services.ai_service import (
    AIService,
    AIServiceError,
    build_system_prompt,
)
from app.services.conversation_memory import ConversationMessage

logger = logging.getLogger("enodaai.ai.gemini")


class GeminiService(AIService):
    """Primary provider: Google Gemini free tier."""

    name = "gemini"

    def __init__(self, api_key: str, model: str):
        self._client = genai.Client(api_key=api_key)
        self._model = model

    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:

        try:
            contents: list[types.Content] = []

            # -----------------------------------------------------
            # Add previous conversation messages
            # -----------------------------------------------------

            for item in history or []:

                role = "user" if item.role == "user" else "model"

                contents.append(
                    types.Content(
                        role=role,
                        parts=[
                            types.Part(
                                text=item.content
                            )
                        ],
                    )
                )

            # -----------------------------------------------------
            # Add the current user message
            # -----------------------------------------------------

            contents.append(
                types.Content(
                    role="user",
                    parts=[
                        types.Part(
                            text=message
                        )
                    ],
                )
            )

            # -----------------------------------------------------
            # Ask Gemini
            # -----------------------------------------------------

            response = await self._client.aio.models.generate_content(
                model=self._model,
                contents=contents,
                config=types.GenerateContentConfig(
                    system_instruction=build_system_prompt(language)
                ),
            )

        except Exception as exc:
            # Any native Google/network error becomes
            # AIServiceError so the orchestrator can fall
            # back to the next provider.
            raise AIServiceError(
                f"gemini request failed: {exc}"
            ) from exc

        text = (response.text or "").strip()

        if not text:
            raise AIServiceError(
                "gemini returned an empty response"
            )

        return text