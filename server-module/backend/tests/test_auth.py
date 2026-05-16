import uuid

import pytest
from fastapi import HTTPException

from app.auth import (
    authenticate_user,
    create_user,
    hash_password,
    normalize_email,
    token_for_user,
    user_from_token,
    verify_password,
)


def test_normalize_email_lowercases_and_strips():
    assert normalize_email("  USER@Example.COM  ") == "user@example.com"


def test_validate_email_accepts_valid_email():
    assert normalize_email("person@test.local") == "person@test.local"


@pytest.mark.parametrize("email", ["bad", "bad@", "@domain.com", "user@localhost"])
def test_validate_email_rejects_invalid_email(email):
    with pytest.raises(HTTPException) as exc:
        normalize_email(email)
    assert exc.value.status_code == 400


def test_hash_password_is_not_plain_text():
    hashed = hash_password("secret123")
    assert hashed != "secret123"
    assert hashed.startswith("pbkdf2_sha256$")


def test_hash_password_rejects_short_password():
    with pytest.raises(HTTPException) as exc:
        hash_password("123")
    assert exc.value.status_code == 400


def test_verify_password_success():
    hashed = hash_password("secret123")
    assert verify_password("secret123", hashed) is True


def test_verify_password_wrong_password_returns_false():
    hashed = hash_password("secret123")
    assert verify_password("wrong123", hashed) is False


@pytest.mark.parametrize("stored_hash", [None, "", "broken", "unknown$120000$aa$bb"])
def test_verify_password_invalid_hash_returns_false(stored_hash):
    assert verify_password("secret123", stored_hash) is False


def test_create_user_success(test_db_session):
    user = create_user(test_db_session, "New@Example.com", "secret123", " Alice ")
    assert user.email == "new@example.com"
    assert user.name == "Alice"
    assert user.password_hash != "secret123"


def test_create_user_duplicate_email_fails(test_db_session):
    create_user(test_db_session, "dupe@example.com", "secret123")
    with pytest.raises(HTTPException) as exc:
        create_user(test_db_session, " DUPE@example.com ", "secret123")
    assert exc.value.status_code == 409


def test_login_success_returns_user(test_db_session):
    created = create_user(test_db_session, "login@example.com", "secret123")
    logged_in = authenticate_user(test_db_session, "LOGIN@example.com", "secret123")
    assert logged_in.id == created.id


def test_login_wrong_password_fails(test_db_session):
    create_user(test_db_session, "wrong@example.com", "secret123")
    with pytest.raises(HTTPException) as exc:
        authenticate_user(test_db_session, "wrong@example.com", "badpass")
    assert exc.value.status_code == 401


def test_login_nonexistent_user_fails(test_db_session):
    with pytest.raises(HTTPException) as exc:
        authenticate_user(test_db_session, "missing@example.com", "secret123")
    assert exc.value.status_code == 401


def test_token_for_user_returns_user_uuid(test_db_session):
    user = create_user(test_db_session, "token@example.com", "secret123")
    assert token_for_user(user) == str(user.id)
    assert uuid.UUID(token_for_user(user)) == user.id


def test_user_from_token_returns_user(test_db_session):
    user = create_user(test_db_session, "lookup@example.com", "secret123")
    assert user_from_token(test_db_session, token_for_user(user)).id == user.id


def test_user_from_invalid_token_returns_none(test_db_session):
    assert user_from_token(test_db_session, "not-a-uuid") is None


def test_user_from_missing_user_returns_none(test_db_session):
    assert user_from_token(test_db_session, str(uuid.uuid4())) is None
