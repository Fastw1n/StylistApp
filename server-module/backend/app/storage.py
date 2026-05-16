import mimetypes
import os
import time
import uuid
from pathlib import Path
from typing import BinaryIO
from urllib.parse import quote

import requests

MEDIA_ROOT = os.environ.get("MEDIA_ROOT", "/data/uploads")
BASE_URL = os.environ.get("BASE_URL", "http://localhost:8000")
STORAGE_BACKEND = os.environ.get("STORAGE_BACKEND", "local").lower()
SEAWEEDFS_FILER_URL = os.environ.get("SEAWEEDFS_FILER_URL", "http://localhost:8888").rstrip("/")
SEAWEEDFS_COLLECTION = os.environ.get("SEAWEEDFS_COLLECTION", "stylist-media").strip("/")
SEAWEEDFS_TIMEOUT = float(os.environ.get("SEAWEEDFS_TIMEOUT", "10"))
SEAWEEDFS_RETRIES = int(os.environ.get("SEAWEEDFS_RETRIES", "90"))
SEAWEEDFS_RETRY_DELAY = float(os.environ.get("SEAWEEDFS_RETRY_DELAY", "1"))

_seaweedfs_ready = False


def is_seaweedfs_enabled() -> bool:
    return STORAGE_BACKEND == "seaweedfs"


def is_local_storage() -> bool:
    return not is_seaweedfs_enabled()


def ensure_dirs() -> None:
    if is_seaweedfs_enabled():
        _ensure_seaweedfs_collection()
        return

    Path(MEDIA_ROOT, "original").mkdir(parents=True, exist_ok=True)
    Path(MEDIA_ROOT, "normalized").mkdir(parents=True, exist_ok=True)


def seed_from_local_uploads(seed_root: str = "/seed/uploads") -> None:
    if not is_seaweedfs_enabled():
        return

    root = Path(seed_root)
    if not root.exists():
        return

    ensure_dirs()
    for path in root.rglob("*"):
        if path.is_file():
            key = path.relative_to(root).as_posix()
            ext = path.suffix.lstrip(".") or "bin"
            if not _seaweedfs_object_exists(key):
                _put_seaweedfs_object(key, path.read_bytes(), ext)


def save_file(folder: str, fileobj: BinaryIO, ext: str) -> str:
    return _save_content(folder, ext, fileobj.read())


def abs_path_for(key: str) -> str:
    if is_seaweedfs_enabled():
        raise RuntimeError("SeaweedFS storage does not expose local filesystem paths")

    return str(Path(MEDIA_ROOT) / key)


def make_key(folder: str, ext: str) -> str:
    ensure_dirs()
    return f"{folder}/{uuid.uuid4().hex}.{ext}"


def copy_key(src_key: str, dst_folder: str) -> str:
    ensure_dirs()
    ext = Path(src_key).suffix.lstrip(".") or "jpg"
    dst_key = f"{dst_folder}/{uuid.uuid4().hex}.{ext}"

    if is_seaweedfs_enabled():
        response = requests.get(
            _seaweedfs_object_url(src_key),
            timeout=SEAWEEDFS_TIMEOUT,
        )
        response.raise_for_status()
        _put_seaweedfs_object(dst_key, response.content, ext)
        return dst_key

    src_path = Path(MEDIA_ROOT) / src_key
    dst_path = Path(MEDIA_ROOT) / dst_key
    dst_path.write_bytes(src_path.read_bytes())
    return dst_key


def url_for(key: str) -> str:
    return f"{BASE_URL}/media/{key}"


def save_bytes(folder: str, filename: str, content: bytes) -> str:
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else "png"
    return _save_content(folder, ext, content)


def _save_content(folder: str, ext: str, content: bytes) -> str:
    ensure_dirs()
    key = f"{folder}/{uuid.uuid4().hex}.{ext}"

    if is_seaweedfs_enabled():
        _put_seaweedfs_object(key, content, ext)
        return key

    path = Path(MEDIA_ROOT) / key
    with open(path, "wb") as f:
        f.write(content)

    return key


def _ensure_seaweedfs_collection() -> None:
    global _seaweedfs_ready
    if _seaweedfs_ready:
        return

    if not _seaweedfs_object_exists(".keep"):
        _put_seaweedfs_object(".keep", b"", "txt")
    _seaweedfs_ready = True


def _seaweedfs_object_exists(key: str) -> bool:
    try:
        response = requests.head(
            _seaweedfs_object_url(key),
            timeout=SEAWEEDFS_TIMEOUT,
        )
        if response.status_code == 404:
            return False
        response.raise_for_status()
        return True
    except requests.RequestException:
        return False


def _put_seaweedfs_object(key: str, content: bytes, ext: str) -> None:
    content_type = mimetypes.guess_type(f"file.{ext}")[0] or "application/octet-stream"
    last_error: Exception | None = None

    for _ in range(SEAWEEDFS_RETRIES):
        try:
            response = requests.put(
                _seaweedfs_object_url(key),
                data=content,
                headers={"Content-Type": content_type},
                timeout=SEAWEEDFS_TIMEOUT,
            )
            response.raise_for_status()
            return
        except requests.RequestException as error:
            last_error = error
            time.sleep(SEAWEEDFS_RETRY_DELAY)

    raise RuntimeError("SeaweedFS filer is not available") from last_error


def _seaweedfs_object_url(key: str) -> str:
    safe_collection = quote(SEAWEEDFS_COLLECTION, safe="/")
    safe_key = quote(key.strip("/"), safe="/")
    return f"{SEAWEEDFS_FILER_URL}/{safe_collection}/{safe_key}"
