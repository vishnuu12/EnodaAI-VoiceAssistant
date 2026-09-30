from app.services.tools.datetime_tool import get_current_datetime
from app.services.tools.location_tool import extract_city
from app.services.tools.weather_tool import get_weather_by_city
from app.services.tools.web_search_tool import search_web


def detect_tool(message: str) -> str | None:
    """
    Detect whether the user message requires a tool.
    """

    text = message.lower().strip()

    # ---------------------------------------------------------
    # Date / Time
    # ---------------------------------------------------------

    time_keywords = [
        "what time",
        "current time",
        "time now",
        "time is it",
        "what's the time",
        "whats the time",
        "today's date",
        "todays date",
        "what date",
        "current date",
        "today",
        "date today",
        "day today",
        "what day",
    ]

    if any(keyword in text for keyword in time_keywords):
        return "datetime"

    # ---------------------------------------------------------
    # Weather
    # ---------------------------------------------------------

    weather_keywords = [
        "weather",
        "temperature",
        "how hot",
        "how cold",
        "is it raining",
        "will it rain",
        "rain today",
        "raining",
    ]

    if any(keyword in text for keyword in weather_keywords):
        return "weather"

    # ---------------------------------------------------------
    # Web Search
    # ---------------------------------------------------------

    web_search_keywords = [
        "search for",
        "search the web",
        "search online",
        "search",
        "latest",
        "news",
        "today's news",
        "todays news",
        "current news",
        "recent news",
        "what happened",
        "who is the current",
        "current president",
        "current ceo",
        "current prime minister",
        "current chief minister",
        "current version",
        "latest version",
    ]

    if any(keyword in text for keyword in web_search_keywords):
        return "web_search"

    return None


async def execute_tool(
    tool_name: str,
    message: str = "",
) -> dict | None:

    # ---------------------------------------------------------
    # Date / Time
    # ---------------------------------------------------------

    if tool_name == "datetime":
        return get_current_datetime()

    # ---------------------------------------------------------
    # Weather
    # ---------------------------------------------------------

    if tool_name == "weather":
        city = extract_city(message)

        # Default location when the user doesn't mention a city.
        if city is None:
            city = "Chennai"

        return await get_weather_by_city(city)

    # ---------------------------------------------------------
    # Web Search
    # ---------------------------------------------------------

    if tool_name == "web_search":
        return search_web(
            query=message,
            max_results=5,
        )

    return None