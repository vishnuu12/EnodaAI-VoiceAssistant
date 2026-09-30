from fastapi import APIRouter, Depends, HTTPException

from app.schemas.chat import ChatRequest, ChatResponse
from app.services.ai_orchestrator import (
    AIOrchestrator,
    get_ai_orchestrator,
)
from app.services.ai_service import AIServiceError
from app.services.conversation_memory import conversation_memory
from app.services.language_service import resolve_language

router = APIRouter(tags=["chat"])


@router.post("/chat", response_model=ChatResponse)
async def chat(
    payload: ChatRequest,
    orchestrator: AIOrchestrator = Depends(get_ai_orchestrator),
) -> ChatResponse:
    """The assistant: recognized speech in, AI reply out.

    The reply language is resolved from the message's script and
    the app's mode hint.

    When conversation_id is provided:
    1. Previous conversation history is loaded.
    2. History + current message are sent to the AI.
    3. User message and AI reply are stored in memory.

    503 means every provider failed - an upstream problem, not
    the client's fault.
    """

    # ---------------------------------------------------------
    # 1. Resolve response language
    # ---------------------------------------------------------

    language = resolve_language(
        payload.text,
        payload.language,
    )

    # ---------------------------------------------------------
    # 2. Load previous conversation history
    # ---------------------------------------------------------

    history = []

    if payload.conversation_id:
        history = conversation_memory.get_history(
            payload.conversation_id
        )

    try:
        # -----------------------------------------------------
        # 3. Generate AI response using conversation history
        # -----------------------------------------------------

        reply = await orchestrator.generate_reply(
            message=payload.text,
            language=language,
            history=history,
        )

        # -----------------------------------------------------
        # 4. Save conversation messages
        # -----------------------------------------------------

        if payload.conversation_id:

            conversation_memory.add_message(
                conversation_id=payload.conversation_id,
                role="user",
                content=payload.text,
            )

            conversation_memory.add_message(
                conversation_id=payload.conversation_id,
                role="assistant",
                content=reply,
            )

    except AIServiceError as exc:
        raise HTTPException(
            status_code=503,
            detail=str(exc),
        ) from exc

    return ChatResponse(reply=reply)