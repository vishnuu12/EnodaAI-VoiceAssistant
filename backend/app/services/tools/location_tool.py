import re


def extract_city(message: str) -> str | None:
    """
    Extract a city name from a weather-related message.

    Supports English, Tanglish, and common Tamil weather phrases.

    Examples:
        "What's the weather in Mumbai?" -> "Mumbai"
        "Weather in Chennai" -> "Chennai"
        "Tell me weather for Bangalore" -> "Bangalore"
        "Mumbai weather எப்படி இருக்கு?" -> "Mumbai"
        "Chennai weather எப்படி?" -> "Chennai"
        "Mumbai-ல weather எப்படி?" -> "Mumbai"
    """

    text = message.strip()

    patterns = [
        # English:
        # What's the weather in Mumbai?
        r"\bweather\s+(?:in|at|for)\s+([A-Za-z][A-Za-z\s.-]*?)(?:\?|$)",

        # English:
        # What's the temperature in Mumbai?
        r"\btemperature\s+(?:in|at|for)\s+([A-Za-z][A-Za-z\s.-]*?)(?:\?|$)",

        # English:
        # Mumbai weather
        r"\b([A-Za-z][A-Za-z\s.-]*?)\s+(?:weather|temperature)\b",

        # Tamil/Tanglish:
        # Mumbai weather எப்படி
        r"\b([A-Za-z][A-Za-z\s.-]*?)\s+weather\s+",

        # Tanglish:
        # Mumbai-ல weather
        r"\b([A-Za-z][A-Za-z\s.-]*?)(?:-ல|-ல்)\s+weather\b",
    ]

    for pattern in patterns:
        match = re.search(
            pattern,
            text,
            re.IGNORECASE,
        )

        if match:
            city = match.group(1).strip()

            city = re.sub(
                r"\s+",
                " ",
                city,
            )

            # Remove trailing punctuation.
            city = city.rstrip("?.!, ")

            if city:
                return city

    return None