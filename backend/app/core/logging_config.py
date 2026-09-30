import logging
import sys

LOG_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"


def setup_logging(debug: bool = False) -> None:
    """Configure root logging once, at startup.

    Simple for now: console output with one consistent format.
    Never log secrets, tokens, or sensitive user content - log
    lines carry ids, statuses and timings, not payloads.
    """
    level = logging.DEBUG if debug else logging.INFO

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter(LOG_FORMAT))

    root = logging.getLogger()
    root.setLevel(level)
    root.handlers.clear()
    root.addHandler(handler)

    # uvicorn's own access log duplicates what our middleware logs
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
