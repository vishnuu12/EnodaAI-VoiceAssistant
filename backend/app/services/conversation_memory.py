from dataclasses import dataclass, field
from typing import Dict, List


@dataclass
class ConversationMessage:
    role: str
    content: str


@dataclass
class Conversation:
    messages: List[ConversationMessage] = field(default_factory=list)


class ConversationMemory:
    """
    Simple in-memory conversation store.

    This is intentionally lightweight for Phase 10.
    Later we can replace it with Redis or a database
    without changing the API contract significantly.
    """

    def __init__(self) -> None:
        self._conversations: Dict[str, Conversation] = {}

    def get_history(
        self,
        conversation_id: str
    ) -> List[ConversationMessage]:
        conversation = self._conversations.get(
            conversation_id
        )

        if conversation is None:
            return []

        return list(conversation.messages)

    def add_message(
        self,
        conversation_id: str,
        role: str,
        content: str
    ) -> None:

        conversation = self._conversations.setdefault(
            conversation_id,
            Conversation()
        )

        conversation.messages.append(
            ConversationMessage(
                role=role,
                content=content
            )
        )

    def clear(
        self,
        conversation_id: str
    ) -> None:
        self._conversations.pop(
            conversation_id,
            None
        )


conversation_memory = ConversationMemory()