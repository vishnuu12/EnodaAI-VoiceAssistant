import httpx

from app.services.tools.geocoding_tool import geocode_city


async def get_weather(
    latitude: float,
    longitude: float,
) -> dict:
    """
    Get current weather using Open-Meteo.
    """

    url = "https://api.open-meteo.com/v1/forecast"

    params = {
        "latitude": latitude,
        "longitude": longitude,
        "current": (
            "temperature_2m,"
            "relative_humidity_2m,"
            "apparent_temperature,"
            "weather_code,"
            "wind_speed_10m"
        ),
        "timezone": "auto",
    }

    async with httpx.AsyncClient(timeout=10.0) as client:
        response = await client.get(
            url,
            params=params,
        )

        response.raise_for_status()

        data = response.json()

    current = data["current"]

    return {
        "temperature": current["temperature_2m"],
        "humidity": current["relative_humidity_2m"],
        "feels_like": current["apparent_temperature"],
        "weather_code": current["weather_code"],
        "wind_speed": current["wind_speed_10m"],
        "timezone": data["timezone"],
    }


async def get_weather_by_city(
    city: str,
) -> dict | None:
    """
    Get current weather for a city.
    """

    location = await geocode_city(city)

    if location is None:
        return None

    weather = await get_weather(
        latitude=location["latitude"],
        longitude=location["longitude"],
    )

    return {
        "city": location["name"],
        "country": location["country"],
        **weather,
    }