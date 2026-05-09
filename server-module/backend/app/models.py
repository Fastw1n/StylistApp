import enum
import uuid
from datetime import datetime
from sqlalchemy import (
    String,
    Enum,
    DateTime,
    ForeignKey,
    Integer,
    Text,
    Boolean,
    Index,
    Float,
)
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.sql import func

from .db import Base


class Gender(str, enum.Enum):
    male = "male"
    female = "female"
    prefer_not_to_say = "prefer_not_to_say"


class ClothingCategory(str, enum.Enum):
    top = "top"
    bottom = "bottom"
    shoes = "shoes"
    outerwear = "outerwear"
    accessory = "accessory"


class Season(str, enum.Enum):
    winter = "winter"
    spring = "spring"
    summer = "summer"
    autumn = "autumn"
    all_season = "all_season"


class DraftStatus(str, enum.Enum):
    created = "created"
    processed = "processed"
    confirmed = "confirmed"
    canceled = "canceled"
    failed = "failed"


class OutfitSource(str, enum.Enum):
    user_created = "user_created"
    generated = "generated"


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str | None] = mapped_column(String, unique=True, nullable=True)
    password_hash: Mapped[str | None] = mapped_column(String, nullable=True)
    name: Mapped[str | None] = mapped_column(String, nullable=True)
    gender: Mapped[Gender] = mapped_column(
        Enum(Gender),
        nullable=False,
        default=Gender.prefer_not_to_say
    )
    avatar_url: Mapped[str | None] = mapped_column(Text, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False
    )


class UserBodyProfile(Base):
    __tablename__ = "user_body_profile"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id"),
        nullable=False,
        unique=True
    )

    height_cm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    weight_kg: Mapped[int | None] = mapped_column(Integer, nullable=True)
    chest_cm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    waist_cm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    body_type: Mapped[str | None] = mapped_column(String, nullable=True)
    skin_tone: Mapped[str | None] = mapped_column(String, nullable=True)
    eye_color: Mapped[str | None] = mapped_column(String, nullable=True)
    hair_color: Mapped[str | None] = mapped_column(String, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False
    )

class ClothingItemDraft(Base):
    __tablename__ = "clothing_item_drafts"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)

    status: Mapped[DraftStatus] = mapped_column(
        Enum(DraftStatus),
        nullable=False,
        default=DraftStatus.created
    )

    original_image_key: Mapped[str] = mapped_column(Text, nullable=False)
    normalized_image_key: Mapped[str | None] = mapped_column(Text, nullable=True)

    attributes: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    error_text: Mapped[str | None] = mapped_column(Text, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False
    )

    __table_args__ = (
        Index("ix_clothing_item_drafts_user_id", "user_id"),
        Index("ix_clothing_item_drafts_status", "status"),
        Index("ix_clothing_item_drafts_created_at", "created_at"),
    )


class ClothingItem(Base):
    __tablename__ = "clothing_items"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)

    name: Mapped[str | None] = mapped_column(String, nullable=True)
    category: Mapped[ClothingCategory] = mapped_column(Enum(ClothingCategory), nullable=False)
    subcategory: Mapped[str | None] = mapped_column(String, nullable=True)

    season: Mapped[Season | None] = mapped_column(Enum(Season), nullable=True)
    warmth_level: Mapped[int | None] = mapped_column(Integer, nullable=True)

    size: Mapped[str | None] = mapped_column(String, nullable=True)
    fit_type: Mapped[str | None] = mapped_column(String, nullable=True)
    material: Mapped[str | None] = mapped_column(String, nullable=True)
    brand: Mapped[str | None] = mapped_column(String, nullable=True)

    normalized_image_key: Mapped[str] = mapped_column(Text, nullable=False)
    colors: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    ml_meta: Mapped[dict | None] = mapped_column(JSONB, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False
    )
    __table_args__ = (
        Index("ix_clothing_items_user_id", "user_id"),
        Index("ix_clothing_items_category", "category"),
        Index("ix_clothing_items_season", "season"),
    )


class Outfit(Base):
    __tablename__ = "outfits"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)

    source: Mapped[OutfitSource] = mapped_column(
        Enum(OutfitSource),
        nullable=False,
        default=OutfitSource.user_created
    )

    name: Mapped[str | None] = mapped_column(String, nullable=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_favorite: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    season: Mapped[Season | None] = mapped_column(Enum(Season), nullable=True)
    style: Mapped[str | None] = mapped_column(String, nullable=True)
    context: Mapped[dict | None] = mapped_column(JSONB, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False
    )
    
    __table_args__ = (
        Index("ix_outfits_user_id", "user_id"),
        Index("ix_outfits_is_favorite", "is_favorite"),
        Index("ix_outfits_source", "source"),
    )


class OutfitItem(Base):
    __tablename__ = "outfit_items"

    outfit_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("outfits.id"),
        primary_key=True
    )
    clothing_item_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("clothing_items.id"),
        primary_key=True
    )

class ClothingItemDraftCandidate(Base):
    __tablename__ = "clothing_item_draft_candidates"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    draft_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("clothing_item_drafts.id"),
        nullable=False
    )

    category: Mapped[ClothingCategory | None] = mapped_column(Enum(ClothingCategory), nullable=True)
    subcategory: Mapped[str | None] = mapped_column(String, nullable=True)

    season: Mapped[Season | None] = mapped_column(Enum(Season), nullable=True)
    warmth_level: Mapped[int | None] = mapped_column(Integer, nullable=True)

    normalized_image_key: Mapped[str] = mapped_column(Text, nullable=False)
    colors: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    ml_meta: Mapped[dict | None] = mapped_column(JSONB, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )

    __table_args__ = (
        Index("ix_clothing_item_draft_candidates_draft_id", "draft_id"),
    )
