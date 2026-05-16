import hashlib
import hmac
import os
import uuid

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from .models import Gender, User

HASH_ALGORITHM = "pbkdf2_sha256"
HASH_ITERATIONS = 120_000


def normalize_email(email: str) -> str:
    value = email.strip().lower()
    local_part, separator, domain = value.partition("@")
    if (
        not separator
        or not local_part
        or not domain
        or "." not in domain
    ):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid email",
        )
    return value


def hash_password(password: str) -> str:
    if len(password) < 6:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Password must contain at least 6 characters",
        )

    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt,
        HASH_ITERATIONS,
    )
    return f"{HASH_ALGORITHM}${HASH_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(password: str, password_hash: str | None) -> bool:
    if not password_hash:
        return False

    try:
        algorithm, iterations_raw, salt_hex, digest_hex = password_hash.split("$", 3)
        if algorithm != HASH_ALGORITHM:
            return False

        digest = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode("utf-8"),
            bytes.fromhex(salt_hex),
            int(iterations_raw),
        )
        return hmac.compare_digest(digest.hex(), digest_hex)
    except Exception:
        return False


def create_user(db: Session, email: str, password: str, name: str | None = None) -> User:
    normalized_email = normalize_email(email)
    existing = db.query(User).filter(User.email == normalized_email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="User with this email already exists",
        )

    user = User(
        email=normalized_email,
        password_hash=hash_password(password),
        name=name.strip() if name and name.strip() else None,
        gender=Gender.prefer_not_to_say,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def authenticate_user(db: Session, email: str, password: str) -> User:
    normalized_email = normalize_email(email)
    user = db.query(User).filter(User.email == normalized_email).first()
    if not user or not verify_password(password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
        )
    return user


def token_for_user(user: User) -> str:
    return str(user.id)


def user_from_token(db: Session, token: str) -> User | None:
    try:
        user_id = uuid.UUID(token)
    except Exception:
        return None
    return db.get(User, user_id)
