import os
import sys
import tempfile
import uuid
from pathlib import Path

import pytest
from fastapi.testclient import TestClient


TEST_ROOT = Path(tempfile.mkdtemp(prefix="stylist_backend_tests_"))
PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))
os.environ["DATABASE_URL"] = f"sqlite:///{(TEST_ROOT / 'test.db').as_posix()}"
os.environ["MEDIA_ROOT"] = str(TEST_ROOT / "media")
os.environ["BASE_URL"] = "http://testserver"
os.environ["STORAGE_BACKEND"] = "local"

from app.auth import create_user, token_for_user  # noqa: E402
from app.db import SessionLocal, engine  # noqa: E402
from app.main import app as fastapi_app  # noqa: E402
from app.main import get_db as route_get_db  # noqa: E402
from app.models import Base  # noqa: E402


@pytest.fixture
def clean_database():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def test_db_session(clean_database):
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@pytest.fixture
def override_get_db(clean_database):
    def _override_get_db():
        db = SessionLocal()
        try:
            yield db
        finally:
            db.close()

    fastapi_app.dependency_overrides[route_get_db] = _override_get_db
    yield _override_get_db
    fastapi_app.dependency_overrides.clear()


@pytest.fixture
def test_app(override_get_db):
    return fastapi_app


@pytest.fixture
def test_client(test_app):
    with TestClient(test_app, raise_server_exceptions=False) as client:
        yield client


@pytest.fixture
def create_test_user(test_db_session):
    def _create_test_user(
        email: str | None = None,
        password: str = "secret123",
        name: str | None = "Test User",
    ):
        unique = uuid.uuid4().hex[:8]
        return create_user(
            test_db_session,
            email or f"user_{unique}@example.com",
            password,
            name,
        )

    return _create_test_user


@pytest.fixture
def auth_headers(create_test_user):
    user = create_test_user(email="user_a@example.com", password="secret123", name="User A")
    return {"Authorization": f"Bearer {token_for_user(user)}"}


@pytest.fixture
def second_user_auth_headers(create_test_user):
    user = create_test_user(email="user_b@example.com", password="secret123", name="User B")
    return {"Authorization": f"Bearer {token_for_user(user)}"}


@pytest.fixture
def override_auth_user():
    return None


@pytest.fixture
def fake_ml_response():
    return [
        {
            "filename": "candidate_top.png",
            "image_base64": "",
            "category": "top",
            "subcategory": "shirt",
            "season": "summer",
            "warmth_level": 1,
            "confidence": 0.91,
            "colors": [{"name": "white", "hex": "#ffffff", "share": 0.8}],
            "ml_meta": {"confidence": 0.91, "yolo_class_name": "shirt"},
        },
        {
            "filename": "candidate_shoes.png",
            "image_base64": "",
            "category": "shoes",
            "subcategory": "sneakers",
            "season": None,
            "warmth_level": None,
            "confidence": 0.82,
            "colors": [],
            "ml_meta": {"confidence": 0.82, "yolo_class_name": "sneakers"},
        },
    ]


@pytest.fixture
def fake_storage(monkeypatch):
    from app import main as main_module

    saved: list[dict] = []

    def save_bytes(folder: str, filename: str, content: bytes) -> str:
        key = f"{folder}/{len(saved)}_{filename}"
        saved.append({"folder": folder, "filename": filename, "content": content, "key": key})
        return key

    monkeypatch.setattr(main_module.storage, "save_bytes", save_bytes)
    monkeypatch.setattr(main_module.storage, "url_for", lambda key: f"http://testserver/media/{key}")
    return saved


@pytest.fixture
def sample_image_bytes():
    return (
        b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01"
        b"\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde"
        b"\x00\x00\x00\x0cIDATx\x9cc\xf8\xff\xff?\x00\x05"
        b"\xfe\x02\xfeA\xde\xfc\x83\x00\x00\x00\x00IEND\xaeB`\x82"
    )


@pytest.fixture
def sample_upload_file(sample_image_bytes):
    return {"image": ("sample.png", sample_image_bytes, "image/png")}
