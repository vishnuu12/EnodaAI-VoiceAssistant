from tavily import TavilyClient

from app.core.config import get_settings


def search_web(
    query: str,
    max_results: int = 5,
) -> dict:
    """
    Search the web using Tavily.
    """

    settings = get_settings()

    if not settings.tavily_api_key:
        raise RuntimeError(
            "Tavily API key is not configured."
        )

    client = TavilyClient(
        api_key=settings.tavily_api_key
    )

    response = client.search(
        query=query,
        max_results=max_results,
        search_depth="basic",
    )

    results = response.get("results", [])

    return {
        "query": query,
        "results": [
            {
                "title": result.get("title", ""),
                "url": result.get("url", ""),
                "content": result.get("content", ""),
            }
            for result in results
        ],
    }