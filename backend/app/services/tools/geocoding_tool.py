import httpx


async def geocode_city(
    city: str,
) -> dict | None:
    """
    Convert a city name into latitude and longitude
    using Open-Meteo's geocoding API.
    """

    url = "https://geocoding-api.open-meteo.com/v1/search"

    params = {
        "name": city,
        "count": 1,
        "language": "en",
        "format": "json",
    }

    async with httpx.AsyncClient(timeout=10.0) as client:
        response = await client.get(
            url,
            params=params,
        )

        response.raise_for_status()

        data = response.json()

    results = data.get("results")

    if not results:
        return None

    location = results[0]

    return {
        "name": location["name"],
        "latitude": location["latitude"],
        "longitude": location["longitude"],
        "country": location.get("country", ""),
        "timezone": location.get("timezone", ""),
    }