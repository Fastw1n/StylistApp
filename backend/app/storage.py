import os
import uuid
from pathlib import Path
from typing import BinaryIO

MEDIA_ROOT = os.environ.get("MEDIA_ROOT", "/data/uploads")
BASE_URL = os.environ.get("BASE_URL", "http://localhost:8000")

def ensure_dirs():
    Path(MEDIA_ROOT, "original").mkdir(parents=True, exist_ok=True)
    Path(MEDIA_ROOT, "normalized").mkdir(parents=True, exist_ok=True)

def save_file(folder: str, fileobj: BinaryIO, ext: str) -> str:
    ensure_dirs()
    key = f"{folder}/{uuid.uuid4().hex}.{ext}"
    path = Path(MEDIA_ROOT) / key
    with open(path, "wb") as f:
        f.write(fileobj.read())
    return key

def abs_path_for(key: str) -> str:
    return str(Path(MEDIA_ROOT) / key)

def make_key(folder: str, ext: str) -> str:
    ensure_dirs()
    return f"{folder}/{uuid.uuid4().hex}.{ext}"

def copy_key(src_key: str, dst_folder: str) -> str:
    ensure_dirs()
    src_path = Path(MEDIA_ROOT) / src_key
    ext = src_path.suffix.lstrip(".") or "jpg"
    dst_key = f"{dst_folder}/{uuid.uuid4().hex}.{ext}"
    dst_path = Path(MEDIA_ROOT) / dst_key
    dst_path.write_bytes(src_path.read_bytes())
    return dst_key

def url_for(key: str) -> str:
    return f"{BASE_URL}/media/{key}"

def save_bytes(folder: str, filename: str, content: bytes) -> str:
    ensure_dirs()
    import uuid
    from pathlib import Path

    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else "png"
    key = f"{folder}/{uuid.uuid4().hex}.{ext}"
    path = Path(MEDIA_ROOT) / key

    with open(path, "wb") as f:
        f.write(content)

    return key