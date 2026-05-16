import pytest
import requests

from app.ml_client import segment_image_via_ml_service


class FakeResponse:
    def __init__(self, payload=None, error=None, json_error=None):
        self.payload = payload or {"items": []}
        self.error = error
        self.json_error = json_error

    def raise_for_status(self):
        if self.error:
            raise self.error

    def json(self):
        if self.json_error:
            raise self.json_error
        return self.payload


def test_segment_image_success(monkeypatch):
    expected_items = [{"filename": "shirt.png", "category": "top"}]

    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: FakeResponse({"items": expected_items}),
    )

    items = segment_image_via_ml_service(b"image", "shirt.jpg", "http://ml-service")

    assert items == expected_items


def test_segment_image_sends_multipart_and_endpoint(monkeypatch):
    captured = {}

    def fake_post(url, files, timeout):
        captured["url"] = url
        captured["files"] = files
        captured["timeout"] = timeout
        return FakeResponse({"items": []})

    monkeypatch.setattr(requests, "post", fake_post)

    segment_image_via_ml_service(b"image-bytes", "upload.png", "http://ml-service")

    assert captured["url"] == "http://ml-service/segment"
    assert captured["files"]["image"] == ("upload.png", b"image-bytes", "image/jpeg")
    assert captured["timeout"] == 300


def test_segment_image_timeout_raises(monkeypatch):
    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: (_ for _ in ()).throw(requests.Timeout("slow")),
    )

    with pytest.raises(requests.Timeout):
        segment_image_via_ml_service(b"image", "shirt.jpg")


def test_segment_image_connection_error_raises(monkeypatch):
    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: (_ for _ in ()).throw(requests.ConnectionError("down")),
    )

    with pytest.raises(requests.ConnectionError):
        segment_image_via_ml_service(b"image", "shirt.jpg")


def test_segment_image_http_error_raises(monkeypatch):
    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: FakeResponse(error=requests.HTTPError("bad gateway")),
    )

    with pytest.raises(requests.HTTPError):
        segment_image_via_ml_service(b"image", "shirt.jpg")


def test_segment_image_invalid_json_raises(monkeypatch):
    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: FakeResponse(json_error=ValueError("invalid json")),
    )

    with pytest.raises(ValueError):
        segment_image_via_ml_service(b"image", "shirt.jpg")


def test_segment_image_missing_items_field_raises(monkeypatch):
    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: FakeResponse({"unexpected": []}),
    )

    with pytest.raises(KeyError):
        segment_image_via_ml_service(b"image", "shirt.jpg")


def test_segment_image_empty_items_returns_empty_list(monkeypatch):
    monkeypatch.setattr(
        requests,
        "post",
        lambda url, files, timeout: FakeResponse({"items": []}),
    )

    assert segment_image_via_ml_service(b"image", "shirt.jpg") == []
