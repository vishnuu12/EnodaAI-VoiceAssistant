from datetime import datetime
from zoneinfo import ZoneInfo


def get_current_datetime(
    timezone: str = "Asia/Kolkata",
) -> dict[str, str]:
    """
    Returns the current date and time for the requested timezone.
    """

    current_time = datetime.now(
        ZoneInfo(timezone)
    )

    return {
        "date": current_time.strftime("%Y-%m-%d"),
        "time": current_time.strftime("%I:%M:%S %p"),
        "day": current_time.strftime("%A"),
        "timezone": timezone,
    }