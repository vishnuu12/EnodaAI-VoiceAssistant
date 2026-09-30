from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    text: str = Field(min_length=1, max_length=500)
    language: str | None = Field(default=None, max_length=10)
    conversation_id: str | None = Field(
        default=None,
        max_length=100
    )


class ChatResponse(BaseModel):
    reply: str