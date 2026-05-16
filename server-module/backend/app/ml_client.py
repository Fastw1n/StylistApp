import os

import requests


def segment_image_via_ml_service(
    image_bytes: bytes,
    filename: str,
    ml_service_url: str | None = None,
) -> list[dict]:
    service_url = (
        ml_service_url
        or os.environ.get("ML_SERVICE_URL")
        or "http://host.docker.internal:8001"
    ).rstrip("/")
    files = {
        "image": (filename, image_bytes, "image/jpeg")
    }

    response = requests.post(
        f"{service_url}/segment",
        files=files,
        timeout=300,
    )
    response.raise_for_status()
    data = response.json()
    return data["items"]
