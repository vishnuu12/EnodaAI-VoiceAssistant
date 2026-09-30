from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables (and .env).

    Secrets (AI provider keys) live ONLY here - read from the
    environment, never hard-coded, never committed to git.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    app_name: str = "EnodaAI"
    app_version: str = "0.1.0"
    environment: str = "development"  # development | production
    debug: bool = True
    api_v1_prefix: str = "/api/v1"

    # --- AI providers (values come from .env / environment) ---

    gemini_api_key: str | None = None
    groq_api_key: str | None = None
    tavily_api_key: str | None = None

    # Model names - override in .env to experiment or when
    # providers rename their models.
    gemini_model: str = "gemini-2.5-flash"
    groq_model: str = "openai/gpt-oss-120b"


@lru_cache
def get_settings() -> Settings:
    """Cached accessor so the environment is parsed once per process."""
    return Settings()
