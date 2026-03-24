import requests


def segment_image_via_ml_service(
    image_bytes: bytes,
    filename: str,
    ml_service_url: str = "http://host.docker.internal:8001",
) -> list[dict]:
    files = {
        "image": (filename, image_bytes, "image/jpeg")
    }

    response = requests.post(
        f"{ml_service_url}/segment",
        files=files,
        timeout=300,
    )
    response.raise_for_status()
    data = response.json()
    return data["items"]