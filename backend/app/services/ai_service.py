from abc import ABC, abstractmethod

from app.services.conversation_memory import ConversationMessage


class AIServiceError(Exception):
    """Raised when a provider cannot produce a reply.

    The orchestrator catches this and tries the next provider.
    Providers translate their native exceptions into this type so
    the chain has one common currency for "I failed".
    """


# EnodaAI's personality and behavior rules. Every provider applies
# this. The language instruction below is appended per request.
SYSTEM_PROMPT = (
    "You are EnodaAI, a friendly personal voice assistant. "
    "Keep replies short - usually one to three sentences - because "
    "they will be spoken aloud. Be helpful, precise, and warm."
)


# How to answer, per resolved language.
LANGUAGE_INSTRUCTIONS = {
    "en": "Reply in simple, clear English.",
    "ta": "Reply in Tamil, written in Tamil script.",
    "auto": (
        "Reply in the same language as the user's message. If the "
        "message is Tanglish (Tamil written in English letters), "
        "reply in Tamil script."
    ),
}


def build_system_prompt(language: str) -> str:
    """Combine the personality with the language instruction."""

    instruction = LANGUAGE_INSTRUCTIONS.get(
        language,
        LANGUAGE_INSTRUCTIONS["auto"]
    )

    return f"{SYSTEM_PROMPT} {instruction}"


class AIService(ABC):
    """One AI provider behind a common interface.

    The chat route depends on this abstraction, never on a specific
    provider - swapping providers is a configuration change, not a
    code change.
    """

    name: str  # for log lines

    @abstractmethod
    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:
        """Return a reply for the user's message.

        Args:
            message: Current user message.
            language: Requested language ("en" | "ta" | "auto").
            history: Previous messages from this conversation.

        Raises:
            AIServiceError: If this provider cannot answer.
        """