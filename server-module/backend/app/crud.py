import uuid
from sqlalchemy.orm import Session
from .models import ClothingItem
from .models import ClothingItemDraftCandidate
from .models import (
    ClothingItemDraft,
    ClothingItem,
    Outfit,
    OutfitItem,
    UserBodyProfile,
    DraftStatus,
    ClothingCategory,
    OutfitSource,
    Season,
)

def create_draft(db: Session, user_id: uuid.UUID, original_key: str, normalized_key: str, attributes: dict) -> ClothingItemDraft:
    draft = ClothingItemDraft(
        user_id=user_id,
        status=DraftStatus.processed,
        original_image_key=original_key,
        normalized_image_key=normalized_key,
        attributes=attributes
    )
    db.add(draft)
    db.commit()
    db.refresh(draft)
    return draft

def get_draft(db: Session, draft_id: uuid.UUID) -> ClothingItemDraft | None:
    return db.get(ClothingItemDraft, draft_id)

def cancel_draft(db: Session, draft: ClothingItemDraft) -> None:
    draft.status = DraftStatus.canceled
    db.commit()

def confirm_draft_to_item(db: Session, draft: ClothingItemDraft, user_overrides: dict | None) -> ClothingItem:
    attrs = draft.attributes or {}
    if user_overrides:
        attrs.update(user_overrides)

    category_str = attrs.get("category") or "top"
    try:
        category = ClothingCategory(category_str)
    except Exception:
        category = ClothingCategory.top

    season = None
    season_str = attrs.get("season")
    if season_str:
        try:
            season = Season(season_str)
        except Exception:
            season = None

    item = ClothingItem(
        user_id=draft.user_id,
        name=attrs.get("name"),
        category=category,
        subcategory=attrs.get("subcategory"),
        season=season,
        warmth_level=attrs.get("warmth_level"),
        size=attrs.get("size"),
        fit_type=attrs.get("fit_type"),
        material=attrs.get("material"),
        brand=attrs.get("brand"),
        normalized_image_key=draft.normalized_image_key or draft.original_image_key,
        colors={"colors": attrs.get("colors", [])},
        ml_meta=attrs
    )
    db.add(item)

    draft.status = DraftStatus.confirmed
    db.commit()
    db.refresh(item)
    return item

def get_items_by_user(db: Session, user_id: uuid.UUID) -> list[ClothingItem]:
    return (
        db.query(ClothingItem)
        .filter(ClothingItem.user_id == user_id)
        .order_by(ClothingItem.created_at.desc())
        .all()
    )

def get_item_by_user(db: Session, user_id: uuid.UUID, item_id: uuid.UUID) -> ClothingItem | None:
    return (
        db.query(ClothingItem)
        .filter(
            ClothingItem.id == item_id,
            ClothingItem.user_id == user_id,
        )
        .first()
    )

def update_item_favorite(
    db: Session,
    item: ClothingItem,
    is_favorite: bool,
    name: str | None,
) -> ClothingItem:
    item.is_favorite = is_favorite
    if name is not None:
        cleaned_name = name.strip()
        item.name = cleaned_name or None

    db.commit()
    db.refresh(item)
    return item

def update_item_name(
    db: Session,
    item: ClothingItem,
    name: str | None,
) -> ClothingItem:
    item.name = name.strip() if name and name.strip() else None
    db.commit()
    db.refresh(item)
    return item

def delete_item(db: Session, item: ClothingItem) -> None:
    db.query(OutfitItem).filter(OutfitItem.clothing_item_id == item.id).delete()
    db.delete(item)
    db.commit()

def create_outfit(
    db: Session,
    user_id: uuid.UUID,
    name: str | None,
    item_ids: list[uuid.UUID],
    style: str | None = None,
    colors: list[str] | None = None,
    season: str | None = None,
) -> Outfit:
    owned_items = (
        db.query(ClothingItem)
        .filter(
            ClothingItem.user_id == user_id,
            ClothingItem.id.in_(item_ids),
        )
        .all()
    )
    owned_item_ids = {item.id for item in owned_items}
    if len(owned_item_ids) != len(set(item_ids)):
        raise ValueError("One or more items do not belong to user")

    outfit_season = None
    if season:
        try:
            outfit_season = Season(season)
        except Exception:
            raise ValueError("Invalid season")

    cleaned_colors = [
        color.strip()
        for color in (colors or [])
        if color and color.strip()
    ]

    outfit = Outfit(
        user_id=user_id,
        source=OutfitSource.user_created,
        name=name.strip() if name and name.strip() else None,
        style=style.strip() if style and style.strip() else None,
        season=outfit_season,
        context={"colors": cleaned_colors} if cleaned_colors else None,
    )
    db.add(outfit)
    db.flush()

    for item_id in dict.fromkeys(item_ids):
        db.add(OutfitItem(outfit_id=outfit.id, clothing_item_id=item_id))

    db.commit()
    db.refresh(outfit)
    return outfit

def get_outfits_by_user(db: Session, user_id: uuid.UUID) -> list[Outfit]:
    return (
        db.query(Outfit)
        .filter(Outfit.user_id == user_id)
        .order_by(Outfit.created_at.desc())
        .all()
    )

def get_outfit_by_user(db: Session, user_id: uuid.UUID, outfit_id: uuid.UUID) -> Outfit | None:
    return (
        db.query(Outfit)
        .filter(
            Outfit.id == outfit_id,
            Outfit.user_id == user_id,
        )
        .first()
    )

def update_outfit_name(
    db: Session,
    outfit: Outfit,
    name: str | None,
) -> Outfit:
    outfit.name = name.strip() if name and name.strip() else None
    db.commit()
    db.refresh(outfit)
    return outfit

def delete_outfit(db: Session, outfit: Outfit) -> None:
    db.query(OutfitItem).filter(OutfitItem.outfit_id == outfit.id).delete()
    db.delete(outfit)
    db.commit()

def get_outfit_items(db: Session, outfit_id: uuid.UUID, user_id: uuid.UUID) -> list[ClothingItem]:
    return (
        db.query(ClothingItem)
        .join(OutfitItem, OutfitItem.clothing_item_id == ClothingItem.id)
        .filter(
            OutfitItem.outfit_id == outfit_id,
            ClothingItem.user_id == user_id,
        )
        .all()
    )

def get_body_profile(db: Session, user_id: uuid.UUID) -> UserBodyProfile | None:
    return (
        db.query(UserBodyProfile)
        .filter(UserBodyProfile.user_id == user_id)
        .first()
    )

def upsert_body_profile(db: Session, user_id: uuid.UUID, values: dict) -> UserBodyProfile:
    profile = get_body_profile(db, user_id)
    if not profile:
        profile = UserBodyProfile(user_id=user_id)
        db.add(profile)

    for field in (
        "skin_tone",
        "eye_color",
        "hair_color",
        "height_cm",
        "weight_kg",
        "chest_cm",
        "waist_cm",
    ):
        setattr(profile, field, values.get(field))

    db.commit()
    db.refresh(profile)
    return profile

def reset_body_profile(db: Session, user_id: uuid.UUID) -> None:
    profile = get_body_profile(db, user_id)
    if profile:
        db.delete(profile)
        db.commit()

def create_draft_candidate(
    db: Session,
    draft_id: uuid.UUID,
    normalized_image_key: str | None,
    category,
    subcategory,
    season,
    warmth_level,
    colors,
    confidence,
    ml_meta,
) -> ClothingItemDraftCandidate:
    candidate = ClothingItemDraftCandidate(
        draft_id=draft_id,
        normalized_image_key=normalized_image_key,
        category=category,
        subcategory=subcategory,
        season=season,
        warmth_level=warmth_level,
        colors=colors,
        confidence=confidence,
        ml_meta=ml_meta,
    )
    db.add(candidate)
    db.commit()
    db.refresh(candidate)
    return candidate

def get_draft_candidates(db: Session, draft_id: uuid.UUID) -> list[ClothingItemDraftCandidate]:
    return (
        db.query(ClothingItemDraftCandidate)
        .filter(ClothingItemDraftCandidate.draft_id == draft_id)
        .all()
    )

def get_candidate(db: Session, candidate_id: uuid.UUID) -> ClothingItemDraftCandidate | None:
    return db.get(ClothingItemDraftCandidate, candidate_id)

def confirm_candidates_to_items(
    db: Session,
    draft,
    selected_items: list,
) -> list[ClothingItem]:
    created_items = []

    for selected in selected_items:
        candidate = get_candidate(db, uuid.UUID(selected.candidate_id))
        if not candidate:
            continue
        if candidate.draft_id != draft.id:
            continue

        attrs = candidate.ml_meta or {}
        if selected.user_overrides:
            attrs.update(selected.user_overrides)

        category = candidate.category or ClothingCategory.top

        item = ClothingItem(
            user_id=draft.user_id,
            name=attrs.get("name"),
            category=category,
            subcategory=attrs.get("subcategory", candidate.subcategory),
            season=candidate.season,
            warmth_level=attrs.get("warmth_level", candidate.warmth_level),
            size=attrs.get("size"),
            fit_type=attrs.get("fit_type"),
            material=attrs.get("material"),
            brand=attrs.get("brand"),
            normalized_image_key=candidate.normalized_image_key,
            colors=candidate.colors,
            ml_meta=attrs,
        )
        db.add(item)
        created_items.append(item)

    draft.status = DraftStatus.confirmed
    db.commit()

    for item in created_items:
        db.refresh(item)

    return created_items
