from app.services.language_service import (
    AUTO,
    ENGLISH,
    TAMIL,
    detect_language,
    resolve_language,
)


def test_detects_tamil_script():
    assert detect_language("நான் இன்று நலமாக இருக்கிறேன்") == TAMIL


def test_latin_script_is_auto():
    assert detect_language("hello how are you") == AUTO


def test_mixed_script_counts_as_tamil():
    assert detect_language("hello நண்பா how are you") == TAMIL


def test_resolve_detection_beats_hint():
    # Tamil script wins even if the app hint says English.
    assert resolve_language("வணக்கம்", "en-IN") == TAMIL


def test_resolve_uses_hint_when_latin():
    assert resolve_language("hello", "en-IN") == ENGLISH
    assert resolve_language("hello", "ta-IN") == TAMIL


def test_resolve_auto_without_hint():
    assert resolve_language("hello", None) == AUTO
