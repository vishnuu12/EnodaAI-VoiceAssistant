from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app

settings = get_settings()
client = TestClient(app)

HEALTH_URL = f"{settings.api_v1_prefix}/health"


def test_health_returns_ok():
    response = client.get(HEALTH_URL)

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_health_reports_app_identity():
    response = client.get(HEALTH_URL)

    assert response.json()["app"] == "EnodaAI"
    assert response.json()["version"] == settings.app_version


def test_health_sets_request_id_header():
    response = client.get(HEALTH_URL)

    assert response.headers["X-Request-ID"]
