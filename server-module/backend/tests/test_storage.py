import io
from pathlib import Path

import pytest
import requests

from app import storage


class FakeResponse:
    def __init__(self, status_code=200, content=b"data", json_data=None, error=None):
        self.status_code = status_code
        self.content = content
        self._json_data = json_data or {}
        self._error = error

    def raise_for_status(self):
        if self._error:
            raise self._error
        if self.status_code >= 400:
            raise requests.HTTPError(f"HTTP {self.status_code}")

    def json(self):
        return self._json_data


def configure_local_storage(monkeypatch, tmp_path):
    monkeypatch.setattr(storage, "STORAGE_BACKEND", "local")
    monkeypatch.setattr(storage, "MEDIA_ROOT", str(tmp_path / "media"))
    monkeypatch.setattr(storage, "BASE_URL", "http://testserver")


def configure_seaweed_storage(monkeypatch):
    monkeypatch.setattr(storage, "STORAGE_BACKEND", "seaweedfs")
    monkeypatch.setattr(storage, "SEAWEEDFS_FILER_URL", "http://seaweed-filer:8888")
    monkeypatch.setattr(storage, "SEAWEEDFS_COLLECTION", "stylist-media")
    monkeypatch.setattr(storage, "SEAWEEDFS_TIMEOUT", 1.0)
    monkeypatch.setattr(storage, "SEAWEEDFS_RETRIES", 2)
    monkeypatch.setattr(storage, "SEAWEEDFS_RETRY_DELAY", 0)
    monkeypatch.setattr(storage, "_seaweedfs_ready", False)


def test_local_storage_ensure_dirs_creates_expected_folders(monkeypatch, tmp_path):
    configure_local_storage(monkeypatch, tmp_path)

    storage.ensure_dirs()

    assert Path(storage.MEDIA_ROOT, "original").is_dir()
    assert Path(storage.MEDIA_ROOT, "normalized").is_dir()


def test_local_storage_save_bytes_creates_file(monkeypatch, tmp_path):
    configure_local_storage(monkeypatch, tmp_path)

    key = storage.save_bytes("normalized", "shirt.png", b"image-bytes")

    assert key.startswith("normalized/")
    assert Path(storage.MEDIA_ROOT, key).read_bytes() == b"image-bytes"


def test_local_storage_save_file_creates_file(monkeypatch, tmp_path):
    configure_local_storage(monkeypatch, tmp_path)

    key = storage.save_file("original", io.BytesIO(b"uploaded"), "jpg")

    assert key.startswith("original/")
    assert Path(storage.MEDIA_ROOT, key).read_bytes() == b"uploaded"


def test_local_storage_copy_key_copies_object(monkeypatch, tmp_path):
    configure_local_storage(monkeypatch, tmp_path)
    source_key = storage.save_bytes("original", "coat.jpg", b"coat")

    copied_key = storage.copy_key(source_key, "normalized")

    assert copied_key.startswith("normalized/")
    assert Path(storage.MEDIA_ROOT, copied_key).read_bytes() == b"coat"


def test_storage_url_for_returns_media_path(monkeypatch, tmp_path):
    configure_local_storage(monkeypatch, tmp_path)

    assert storage.url_for("normalized/a.png") == "http://testserver/media/normalized/a.png"


def test_abs_path_for_returns_local_path(monkeypatch, tmp_path):
    configure_local_storage(monkeypatch, tmp_path)

    assert storage.abs_path_for("normalized/a.png") == str(Path(storage.MEDIA_ROOT) / "normalized/a.png")


def test_abs_path_for_seaweedfs_raises(monkeypatch):
    configure_seaweed_storage(monkeypatch)

    with pytest.raises(RuntimeError):
        storage.abs_path_for("normalized/a.png")


def test_seaweedfs_save_bytes_calls_filer_put(monkeypatch):
    configure_seaweed_storage(monkeypatch)
    calls = []

    monkeypatch.setattr(storage, "_seaweedfs_object_exists", lambda key: True)

    def fake_put(url, data, headers, timeout):
        calls.append({"url": url, "data": data, "headers": headers, "timeout": timeout})
        return FakeResponse()

    monkeypatch.setattr(storage.requests, "put", fake_put)

    key = storage.save_bytes("normalized", "shirt.png", b"png")

    assert key.startswith("normalized/")
    assert calls[0]["url"].startswith("http://seaweed-filer:8888/stylist-media/normalized/")
    assert calls[0]["data"] == b"png"
    assert calls[0]["headers"]["Content-Type"] == "image/png"


def test_seaweedfs_retries_on_temporary_failure(monkeypatch):
    configure_seaweed_storage(monkeypatch)
    monkeypatch.setattr(storage, "_seaweedfs_object_exists", lambda key: True)
    monkeypatch.setattr(storage.time, "sleep", lambda delay: None)
    attempts = {"count": 0}

    def fake_put(url, data, headers, timeout):
        attempts["count"] += 1
        if attempts["count"] == 1:
            raise requests.ConnectionError("not ready")
        return FakeResponse()

    monkeypatch.setattr(storage.requests, "put", fake_put)

    storage.save_bytes("normalized", "shirt.png", b"png")

    assert attempts["count"] == 2


def test_seaweedfs_failure_raises_controlled_error(monkeypatch):
    configure_seaweed_storage(monkeypatch)
    monkeypatch.setattr(storage, "_seaweedfs_object_exists", lambda key: True)
    monkeypatch.setattr(storage.time, "sleep", lambda delay: None)
    monkeypatch.setattr(
        storage.requests,
        "put",
        lambda url, data, headers, timeout: (_ for _ in ()).throw(requests.ConnectionError("down")),
    )

    with pytest.raises(RuntimeError, match="SeaweedFS filer is not available"):
        storage.save_bytes("normalized", "shirt.png", b"png")


def test_seaweedfs_copy_key_reads_then_puts(monkeypatch):
    configure_seaweed_storage(monkeypatch)
    monkeypatch.setattr(storage, "_seaweedfs_object_exists", lambda key: True)
    copied = []

    monkeypatch.setattr(storage.requests, "get", lambda url, timeout: FakeResponse(content=b"source"))
    monkeypatch.setattr(storage, "_put_seaweedfs_object", lambda key, content, ext: copied.append((key, content, ext)))

    copied_key = storage.copy_key("original/source.jpg", "normalized")

    assert copied_key.startswith("normalized/")
    assert copied[0][1] == b"source"
    assert copied[0][2] == "jpg"


def test_seed_from_local_uploads_is_idempotent(monkeypatch, tmp_path):
    configure_seaweed_storage(monkeypatch)
    seed_root = tmp_path / "seed"
    source = seed_root / "normalized" / "shirt.png"
    source.parent.mkdir(parents=True)
    source.write_bytes(b"shirt")
    uploaded = set()

    monkeypatch.setattr(storage, "ensure_dirs", lambda: None)
    monkeypatch.setattr(storage, "_seaweedfs_object_exists", lambda key: key in uploaded)

    def fake_put(key, content, ext):
        uploaded.add(key)

    monkeypatch.setattr(storage, "_put_seaweedfs_object", fake_put)

    storage.seed_from_local_uploads(str(seed_root))
    storage.seed_from_local_uploads(str(seed_root))

    assert uploaded == {"normalized/shirt.png"}


def test_seaweedfs_object_url_quotes_path(monkeypatch):
    configure_seaweed_storage(monkeypatch)

    assert (
        storage._seaweedfs_object_url("normalized/file with space.png")
        == "http://seaweed-filer:8888/stylist-media/normalized/file%20with%20space.png"
    )
