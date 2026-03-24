import uuid
from sqlalchemy.orm import Session
from .models import ClothingItem
from .models import ClothingItemDraftCandidate
from .models import (
    ClothingItemDraft,
    ClothingItem,
    DraftStatus,
    ClothingCategory,
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