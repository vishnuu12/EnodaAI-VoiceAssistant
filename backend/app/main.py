import logging
import time
import uuid

from fastapi import FastAPI, Request

from app.api.v1.routes import chat, health
from app.core.config import get_settings
from app.core.logging_config import setup_logging

settings = get_settings()
setup_logging(debug=settings.debug)

logger = logging.getLogger("enodaai")

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)

app.include_router(health.router, prefix=settings.api_v1_prefix)
app.include_router(chat.router, prefix=settings.api_v1_prefix)


@app.middleware("http")
async def request_context(request: Request, call_next):
    """Wrap every request: assign an id, time it, log the outcome.

    Deliberately logs NO request/response bodies - only method,
    path, status and duration - so sensitive content can never
    leak into logs.
    """
    request_id = uuid.uuid4().hex[:8]
    request.state.request_id = request_id

    start = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception:
        logger.exception(
            "request_id=%s method=%s path=%s failed",
            request_id,
            request.method,
            request.url.path,
        )
        raise
    duration_ms = (time.perf_counter() - start) * 1000

    logger.info(
        "request_id=%s method=%s path=%s status=%s duration_ms=%.1f",
        request_id,
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
    )
    response.headers["X-Request-ID"] = request_id
    return response
