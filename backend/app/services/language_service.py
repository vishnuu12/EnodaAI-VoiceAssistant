import re

# Tamil Unicode block: U+0B80 to U+0BFF.
_TAMIL_SCRIPT = re.compile(r"[\u0B80-\u0BFF]")

# Language codes used across the system. "auto" = the LLM matches
# the user's language itself.
ENGLISH = "en"
TAMIL = "ta"
AUTO = "auto"


def detect_language(text: str) -> str:
    """Script-based detection.

    Any Tamil-script character makes the text Tamil - reliable for
    STT output, which produces either Tamil script (ta-IN) or
    Latin script (en-IN). Returns AUTO when the script is Latin,
    because Latin text may be English or Tanglish.
    """
    if _TAMIL_SCRIPT.search(text):
        return TAMIL
    return AUTO


def resolve_language(text: str, hint: str | None) -> str:
    """Decide the reply language.

    Detection wins when it can (Tamil script). Otherwise the
    app's mode hint (the STT language the user chose) decides;
    with no hint, the LLM matches the user's language itself.
    """
    detected = detect_language(text)
    if detected != AUTO:
        return detected

    if hint:
        normalized = hint.lower()
        if normalized.startswith("ta"):
            return TAMIL
        if normalized.startswith("en"):
            return ENGLISH
    return AUTO
