import uuid
from fastapi import FastAPI, UploadFile, File, Depends, HTTPException
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session
from .schemas import (
    PrepareResponse,
    ConfirmRequest,
    CancelRequest,
    ItemsListResponse,
    ClothingItemDto,
    DraftCandidateResponse,
    ConfirmResponse,
    ConfirmedItemDto,
    Attributes,
)
from .crud import (
    create_draft,
    get_draft,
    cancel_draft,
    get_items_by_user,
    create_draft_candidate,
    confirm_candidates_to_items,
)
from .db import get_db, engine, Base
from . import storage
from .models import User, Gender, ClothingCategory, Season
from . import storage
import base64
from .ml_client import segment_image_via_ml_service

app = FastAPI(title="Stylist API")


@app.on_event("startup")
def on_startup():
    Base.metadata.create_all(bind=engine)


storage.ensure_dirs()
app.mount("/media", StaticFiles(directory=storage.MEDIA_ROOT), name="media")


def get_or_create_demo_user(db: Session) -> uuid.UUID:
    user = db.query(User).first()
    if not user:
        user = User(
            email="demo@demo.local",
            password_hash="demo",
            gender=Gender.prefer_not_to_say
        )
        db.add(user)
        db.commit()
        db.refresh(user)
    return user.id


@app.post("/v1/items/prepare", response_model=PrepareResponse)
async def prepare_item(
    image: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    user_id = get_or_create_demo_user(db)

    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image uploads are supported")

    ext = "jpg"
    if image.filename and "." in image.filename:
        ext = image.filename.rsplit(".", 1)[-1].lower()[:5]

    image_bytes = await image.read()

    # сохраняем original
    original_key = storage.save_bytes("original", image.filename or f"upload.{ext}", image_bytes)

    draft = create_draft(
        db=db,
        user_id=user_id,
        original_key=original_key,
        normalized_key=None,
        attributes={}
    )

    try:
        detected_items = segment_image_via_ml_service(
            image_bytes=image_bytes,
            filename=image.filename or f"upload.{ext}",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"ML service failed: {str(e)}")

    if not detected_items:
        raise HTTPException(status_code=422, detail="No clothing items detected")

    response_items = []

    for detected in detected_items:
        category = None
        if detected.get("category"):
            try:
                category = ClothingCategory(detected["category"])
            except Exception:
                category = None

        season = None
        if detected.get("season"):
            try:
                season = Season(detected["season"])
            except Exception:
                season = None

        # сохраняем candidate image из base64
        image_b64 = detected["image_base64"]
        candidate_bytes = base64.b64decode(image_b64)
        normalized_key = storage.save_bytes("normalized", detected["filename"], candidate_bytes)

        candidate = create_draft_candidate(
            db=db,
            draft_id=draft.id,
            normalized_image_key=normalized_key,
            category=category,
            subcategory=detected.get("subcategory"),
            season=season,
            warmth_level=detected.get("warmth_level"),
            colors={"colors": detected.get("colors", [])},
            confidence=detected.get("confidence"),
            ml_meta=detected.get("ml_meta"),
        )

        response_items.append(
            DraftCandidateResponse(
                candidate_id=str(candidate.id),
                normalized_image_url=storage.url_for(candidate.normalized_image_key),
                category=candidate.category.value if candidate.category else None,
                subcategory=candidate.subcategory,
                season=candidate.season.value if candidate.season else None,
                warmth_level=candidate.warmth_level,
                confidence=candidate.confidence,
                colors=detected.get("colors", []),
            )
        )

    return PrepareResponse(
        draft_id=str(draft.id),
        original_image_url=storage.url_for(original_key),
        items=response_items,
        needs_user_review=True
    )

@app.post("/v1/items/confirm", response_model=ConfirmResponse)
async def confirm_item(body: ConfirmRequest, db: Session = Depends(get_db)):
    draft = get_draft(db, uuid.UUID(body.draft_id))
    if not draft:
        raise HTTPException(status_code=404, detail="Draft not found")

    if draft.status in ("canceled", "failed"):
        raise HTTPException(status_code=400, detail=f"Draft status is {draft.status}")

    if not body.selected_items:
        raise HTTPException(status_code=400, detail="No selected_items provided")

    items = confirm_candidates_to_items(db, draft, body.selected_items)

    return ConfirmResponse(
        items=[
            ConfirmedItemDto(
                item_id=str(item.id),
                normalized_image_url=storage.url_for(item.normalized_image_key),
                attributes=Attributes(
                    category=item.category.value if item.category else None,
                    subcategory=item.subcategory,
                    season=item.season.value if item.season else None,
                    warmth_level=item.warmth_level,
                    confidence=(item.ml_meta or {}).get("confidence"),
                    colors=((item.colors or {}).get("colors") or [])
                )
            )
            for item in items
        ]
    )


@app.post("/v1/items/cancel")
async def cancel_item(body: CancelRequest, db: Session = Depends(get_db)):
    draft = get_draft(db, uuid.UUID(body.draft_id))
    if not draft:
        raise HTTPException(status_code=404, detail="Draft not found")
    cancel_draft(db, draft)
    return {"ok": True}

@app.get("/v1/items", response_model=ItemsListResponse)
async def get_items(db: Session = Depends(get_db)):
    user_id = get_or_create_demo_user(db)
    items = get_items_by_user(db, user_id)

    return ItemsListResponse(
        items=[
            ClothingItemDto(
                item_id=str(item.id),
                category=item.category.value,
                subcategory=item.subcategory,
                normalized_image_url=storage.url_for(item.normalized_image_key),
                season=item.season.value if item.season else None,
                warmth_level=item.warmth_level
            )
            for item in items
        ]
    )