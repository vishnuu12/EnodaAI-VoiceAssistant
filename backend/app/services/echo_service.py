from app.services.ai_service import AIService
from app.services.conversation_memory import ConversationMessage


class EchoService(AIService):
    """Last-resort fallback when NO provider keys are configured.

    Keeps the whole pipeline testable and demonstrable without any
    external account. The orchestrator only adds this when both
    Gemini and Groq keys are missing.
    """

    name = "echo"

    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:

        return (
            "(echo mode - no AI key configured) "
            "You said: "
            + message
        )