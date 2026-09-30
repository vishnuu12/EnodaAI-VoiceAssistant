import asyncio

import pytest

from app.services.ai_orchestrator import AIOrchestrator
from app.services.ai_service import AIService, AIServiceError
from app.services.conversation_memory import ConversationMessage


class StubService(AIService):

    def __init__(self, name: str, reply: str | None):
        self.name = name
        self.reply = reply
        self.received_history = None

    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:

        self.received_history = history

        if self.reply is None:
            raise AIServiceError(
                f"{self.name} failed"
            )

        return self.reply


def test_falls_back_to_second_provider():
    chain = AIOrchestrator(
        [
            StubService("primary", None),
            StubService("backup", "backup reply"),
        ]
    )

    reply = asyncio.run(
        chain.generate_reply("hi", "ta")
    )

    assert reply == "backup reply"


def test_raises_when_all_providers_fail():
    chain = AIOrchestrator(
        [
            StubService("primary", None),
            StubService("backup", None),
        ]
    )

    with pytest.raises(AIServiceError):
        asyncio.run(
            chain.generate_reply("hi")
        )


def test_first_provider_wins_when_healthy():
    chain = AIOrchestrator(
        [
            StubService("primary", "primary reply"),
            StubService("backup", "backup reply"),
        ]
    )

    reply = asyncio.run(
        chain.generate_reply("hi")
    )

    assert reply == "primary reply"


def test_language_reaches_the_provider():
    primary = StubService(
        "primary",
        "primary reply"
    )

    chain = AIOrchestrator([primary])

    reply = asyncio.run(
        chain.generate_reply(
            "வணக்கம்",
            "ta"
        )
    )

    assert reply == "primary reply"


def test_history_reaches_the_provider():
    primary = StubService(
        "primary",
        "history reply"
    )

    chain = AIOrchestrator([primary])

    history = [
        ConversationMessage(
            role="user",
            content="My name is Vishnu."
        ),
        ConversationMessage(
            role="assistant",
            content="Nice to meet you, Vishnu!"
        ),
    ]

    reply = asyncio.run(
        chain.generate_reply(
            message="What is my name?",
            language="en",
            history=history,
        )
    )

    assert reply == "history reply"
    assert primary.received_history == history