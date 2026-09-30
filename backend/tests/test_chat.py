from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app
from app.services.ai_orchestrator import (
    AIOrchestrator,
    get_ai_orchestrator,
)
from app.services.ai_service import AIService, AIServiceError
from app.services.conversation_memory import conversation_memory
from app.services.conversation_memory import ConversationMessage

settings = get_settings()
client = TestClient(app)

CHAT_URL = f"{settings.api_v1_prefix}/chat"


class FakeAIService(AIService):
    """Never touches a real provider.

    Replies with whatever we want or simulates an outage.
    Records the language and conversation history so tests
    can assert that they reach the provider.
    """

    name = "fake"

    def __init__(self, reply: str | None):
        self._reply = reply
        self.received_language: str | None = None
        self.received_history: list[ConversationMessage] | None = None

    async def generate_reply(
        self,
        message: str,
        language: str = "auto",
        history: list[ConversationMessage] | None = None,
    ) -> str:

        self.received_language = language
        self.received_history = history

        if self._reply is None:
            raise AIServiceError(
                "simulated provider outage"
            )

        return self._reply


def _use_fake(reply: str | None) -> FakeAIService:
    fake = FakeAIService(reply)

    app.dependency_overrides[get_ai_orchestrator] = (
        lambda: AIOrchestrator([fake])
    )

    return fake


def teardown_function():
    app.dependency_overrides.clear()


def test_chat_returns_ai_reply():
    _use_fake("Hello! How can I help you today?")

    response = client.post(
        CHAT_URL,
        json={"text": "hi"},
    )

    assert response.status_code == 200
    assert (
        response.json()["reply"]
        == "Hello! How can I help you today?"
    )


def test_chat_rejects_empty_text():
    response = client.post(
        CHAT_URL,
        json={"text": ""},
    )

    assert response.status_code == 422


def test_chat_rejects_missing_text():
    response = client.post(
        CHAT_URL,
        json={},
    )

    assert response.status_code == 422


def test_chat_returns_503_when_all_providers_fail():
    _use_fake(None)

    response = client.post(
        CHAT_URL,
        json={"text": "hi"},
    )

    assert response.status_code == 503


def test_tamil_script_resolves_to_tamil():
    fake = _use_fake("வணக்கம்")

    response = client.post(
        CHAT_URL,
        json={
            "text": "நீ எப்படி இருக்கிறாய்?"
        },
    )

    assert response.status_code == 200
    assert fake.received_language == "ta"


def test_english_hint_resolves_to_english():
    fake = _use_fake("Hello")

    response = client.post(
        CHAT_URL,
        json={
            "text": "hello there",
            "language": "en-IN",
        },
    )

    assert response.status_code == 200
    assert fake.received_language == "en"


def test_no_hint_latin_is_auto():
    fake = _use_fake("Hello")

    response = client.post(
        CHAT_URL,
        json={
            "text": "hello there"
        },
    )

    assert response.status_code == 200
    assert fake.received_language == "auto"


def test_conversation_history_is_loaded():
    conversation_id = "test-conversation-1"

    conversation_memory.clear(conversation_id)

    conversation_memory.add_message(
        conversation_id=conversation_id,
        role="user",
        content="My name is Vishnu.",
    )

    conversation_memory.add_message(
        conversation_id=conversation_id,
        role="assistant",
        content="Nice to meet you, Vishnu!",
    )

    fake = _use_fake(
        "Your name is Vishnu."
    )

    response = client.post(
        CHAT_URL,
        json={
            "text": "What is my name?",
            "conversation_id": conversation_id,
        },
    )

    assert response.status_code == 200

    assert fake.received_history == [
        ConversationMessage(
            role="user",
            content="My name is Vishnu.",
        ),
        ConversationMessage(
            role="assistant",
            content="Nice to meet you, Vishnu!",
        ),
    ]

    conversation_memory.clear(conversation_id)


def test_conversation_messages_are_saved():
    conversation_id = "test-conversation-2"

    conversation_memory.clear(conversation_id)

    _use_fake("Nice to meet you!")

    response = client.post(
        CHAT_URL,
        json={
            "text": "My name is Vishnu.",
            "conversation_id": conversation_id,
        },
    )

    assert response.status_code == 200

    history = conversation_memory.get_history(
        conversation_id
    )

    assert history == [
        ConversationMessage(
            role="user",
            content="My name is Vishnu.",
        ),
        ConversationMessage(
            role="assistant",
            content="Nice to meet you!",
        ),
    ]

    conversation_memory.clear(conversation_id)