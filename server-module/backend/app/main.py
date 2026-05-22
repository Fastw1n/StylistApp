import uuid
from fastapi import FastAPI, UploadFile, File, Depends, Header, HTTPException
from fastapi.staticfiles import StaticFiles
from sqlalchemy import inspect, text
from sqlalchemy.orm import Session
from .schemas import (
    AuthRequest,
    AuthResponse,
    BodyProfileDto,
    BodyProfileRequest,
    PrepareResponse,
    ConfirmRequest,
    CancelRequest,
    ItemsListResponse,
    ClothingItemDto,
    DraftCandidateResponse,
    ConfirmResponse,
    ConfirmedItemDto,
    RegisterRequest,
    Attributes,
    UserProfileDto,
    FavoriteItemRequest,
    UpdateItemRequest,
    CreateOutfitRequest,
    OutfitDto,
    OutfitItemDto,
    OutfitsListResponse,
    UpdateOutfitRequest,
    DeleteOutfitResponse,
)
from .auth import authenticate_user, create_user, token_for_user, user_from_token
from .crud import (
    create_draft,
    get_draft,
    cancel_draft,
    get_items_by_user,
    get_item_by_user,
    update_item_favorite,
    update_item_name,
    delete_item,
    create_outfit,
    get_outfits_by_user,
    get_outfit_by_user,
    get_outfit_items,
    update_outfit_name,
    delete_outfit,
    get_body_profile,
    upsert_body_profile,
    reset_body_profile,
    create_draft_candidate,
    confirm_candidates_to_items,
)
from .db import get_db, engine, Base
from . import storage
from .models import User, Gender, ClothingCategory, Season, DraftStatus
from . import storage
import base64
from .ml_client import segment_image_via_ml_service

app = FastAPI(title="Stylist API")


@app.on_event("startup")
def on_startup():
    Base.metadata.create_all(bind=engine)
    ensure_body_profile_schema()
    ensure_clothing_item_favorite_schema()
    storage.ensure_dirs()
    storage.seed_from_local_uploads()


if storage.is_local_storage():
    storage.ensure_dirs()
    app.mount("/media", StaticFiles(directory=storage.MEDIA_ROOT), name="media")


def get_or_create_demo_user(db: Session) -> uuid.UUID:
    user = db.query(User).filter(User.email == "demo@demo.local").first()
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


def user_profile(user: User) -> UserProfileDto:
    return UserProfileDto(
        user_id=str(user.id),
        email=user.email or "",
        name=user.name,
    )


def ensure_body_profile_schema() -> None:
    expected_columns = {
        "eye_color": "VARCHAR",
        "hair_color": "VARCHAR",
        "chest_cm": "INTEGER",
        "waist_cm": "INTEGER",
    }

    with engine.begin() as connection:
        existing_columns = {
            column["name"]
            for column in inspect(connection).get_columns("user_body_profile")
        }

        for column_name, column_type in expected_columns.items():
            if column_name not in existing_columns:
                connection.execute(
                    text(f"ALTER TABLE user_body_profile ADD COLUMN {column_name} {column_type}")
                )


def ensure_clothing_item_favorite_schema() -> None:
    expected_columns = {
        "name": "VARCHAR",
        "is_favorite": "BOOLEAN NOT NULL DEFAULT FALSE",
    }

    with engine.begin() as connection:
        existing_columns = {
            column["name"]
            for column in inspect(connection).get_columns("clothing_items")
        }

        for column_name, column_type in expected_columns.items():
            if column_name not in existing_columns:
                connection.execute(
                    text(f"ALTER TABLE clothing_items ADD COLUMN {column_name} {column_type}")
                )


def body_profile_response(profile) -> BodyProfileDto:
    if not profile:
        return BodyProfileDto()

    return BodyProfileDto(
        skin_tone=profile.skin_tone,
        eye_color=profile.eye_color,
        hair_color=profile.hair_color,
        height_cm=profile.height_cm,
        weight_kg=profile.weight_kg,
        chest_cm=profile.chest_cm,
        waist_cm=profile.waist_cm,
    )


def clothing_item_response(item) -> ClothingItemDto:
    return ClothingItemDto(
        item_id=str(item.id),
        name=item.name,
        category=item.category.value,
        subcategory=item.subcategory,
        normalized_image_url=storage.url_for(item.normalized_image_key),
        season=item.season.value if item.season else None,
        warmth_level=item.warmth_level,
        is_favorite=item.is_favorite,
    )


def outfit_item_response(item) -> OutfitItemDto:
    return OutfitItemDto(
        item_id=str(item.id),
        name=item.name,
        category=item.category.value,
        subcategory=item.subcategory,
        normalized_image_url=storage.url_for(item.normalized_image_key),
    )


def outfit_response(outfit, db: Session) -> OutfitDto:
    items = get_outfit_items(db, outfit.id, outfit.user_id)
    return OutfitDto(
        outfit_id=str(outfit.id),
        name=outfit.name,
        items=[outfit_item_response(item) for item in items],
        style=outfit.style,
        colors=(outfit.context or {}).get("colors") or [],
        season=outfit.season.value if outfit.season else None,
    )


def get_current_user_id(
    db: Session,
    authorization: str | None,
) -> uuid.UUID:
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization.split(" ", 1)[1].strip()
        user = user_from_token(db, token)
        if user:
            return user.id
        raise HTTPException(status_code=401, detail="Invalid auth token")

    raise HTTPException(status_code=401, detail="Missing auth token")


@app.post("/v1/auth/register", response_model=AuthResponse)
async def register_user(body: RegisterRequest, db: Session = Depends(get_db)):
    user = create_user(
        db=db,
        email=body.email,
        password=body.password,
        name=body.name,
    )
    return AuthResponse(
        token=token_for_user(user),
        user=user_profile(user),
    )


@app.post("/v1/auth/login", response_model=AuthResponse)
async def login_user(body: AuthRequest, db: Session = Depends(get_db)):
    user = authenticate_user(db, body.email, body.password)
    return AuthResponse(
        token=token_for_user(user),
        user=user_profile(user),
    )


@app.get("/v1/auth/me", response_model=UserProfileDto)
async def get_me(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    user = db.get(User, user_id)
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user_profile(user)


@app.get("/v1/profile/body", response_model=BodyProfileDto)
async def get_user_body_profile(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    return body_profile_response(get_body_profile(db, user_id))


@app.put("/v1/profile/body", response_model=BodyProfileDto)
async def save_user_body_profile(
    body: BodyProfileRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    profile = upsert_body_profile(db, user_id, body.dict())
    return body_profile_response(profile)


@app.delete("/v1/profile/body", response_model=BodyProfileDto)
async def clear_user_body_profile(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    reset_body_profile(db, user_id)
    return BodyProfileDto()


@app.post("/v1/items/prepare", response_model=PrepareResponse)
async def prepare_item(
    image: UploadFile = File(...),
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)

    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image uploads are supported")

    ext = "jpg"
    if image.filename and "." in image.filename:
        ext = image.filename.rsplit(".", 1)[-1].lower()[:5]

    image_bytes = await image.read()

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
async def confirm_item(
    body: ConfirmRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    draft = get_draft(db, uuid.UUID(body.draft_id))
    if not draft:
        raise HTTPException(status_code=404, detail="Draft not found")

    if draft.user_id != user_id:
        raise HTTPException(status_code=403, detail="Draft belongs to another user")

    if draft.status in (DraftStatus.canceled, DraftStatus.failed):
        raise HTTPException(status_code=400, detail=f"Draft status is {draft.status}")

    if not body.selected_items:
        raise HTTPException(status_code=400, detail="No selected_items provided")

    items = confirm_candidates_to_items(db, draft, body.selected_items)

    return ConfirmResponse(
        items=[
            ConfirmedItemDto(
                item_id=str(item.id),
                normalized_image_url=storage.url_for(item.normalized_image_key),
                name=item.name,
                is_favorite=item.is_favorite,
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
async def cancel_item(
    body: CancelRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    draft = get_draft(db, uuid.UUID(body.draft_id))
    if not draft:
        raise HTTPException(status_code=404, detail="Draft not found")
    if draft.user_id != user_id:
        raise HTTPException(status_code=403, detail="Draft belongs to another user")
    cancel_draft(db, draft)
    return {"ok": True}

@app.get("/v1/items", response_model=ItemsListResponse)
async def get_items(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    items = get_items_by_user(db, user_id)

    return ItemsListResponse(
        items=[clothing_item_response(item) for item in items]
    )


@app.put("/v1/items/{item_id}/favorite", response_model=ClothingItemDto)
async def set_item_favorite(
    item_id: str,
    body: FavoriteItemRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)

    try:
        parsed_item_id = uuid.UUID(item_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid item_id")

    item = get_item_by_user(db, user_id, parsed_item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    updated_item = update_item_favorite(
        db=db,
        item=item,
        is_favorite=body.is_favorite,
        name=body.name,
    )

    return clothing_item_response(updated_item)


@app.put("/v1/items/{item_id}", response_model=ClothingItemDto)
async def update_item(
    item_id: str,
    body: UpdateItemRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)

    try:
        parsed_item_id = uuid.UUID(item_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid item_id")

    item = get_item_by_user(db, user_id, parsed_item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    updated_item = update_item_name(
        db=db,
        item=item,
        name=body.name,
    )

    return clothing_item_response(updated_item)


@app.delete("/v1/items/{item_id}")
async def remove_item(
    item_id: str,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)

    try:
        parsed_item_id = uuid.UUID(item_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid item_id")

    item = get_item_by_user(db, user_id, parsed_item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")

    delete_item(db, item)

    return {"ok": True}


@app.get("/v1/outfits", response_model=OutfitsListResponse)
async def get_outfits(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    outfits = get_outfits_by_user(db, user_id)
    return OutfitsListResponse(
        outfits=[outfit_response(outfit, db) for outfit in outfits]
    )


@app.post("/v1/outfits", response_model=OutfitDto)
async def save_outfit(
    body: CreateOutfitRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)
    if not body.item_ids:
        raise HTTPException(status_code=400, detail="No item_ids provided")

    try:
        parsed_item_ids = [uuid.UUID(item_id) for item_id in body.item_ids]
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid item_id")

    try:
        outfit = create_outfit(
            db=db,
            user_id=user_id,
            name=body.name,
            item_ids=parsed_item_ids,
            style=body.style,
            colors=body.colors,
            season=body.season,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error))

    return outfit_response(outfit, db)


@app.put("/v1/outfits/{outfit_id}", response_model=OutfitDto)
async def update_saved_outfit(
    outfit_id: str,
    body: UpdateOutfitRequest,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)

    try:
        parsed_outfit_id = uuid.UUID(outfit_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid outfit_id")

    outfit = get_outfit_by_user(db, user_id, parsed_outfit_id)
    if not outfit:
        raise HTTPException(status_code=404, detail="Outfit not found")

    updated_outfit = update_outfit_name(
        db=db,
        outfit=outfit,
        name=body.name,
    )

    return outfit_response(updated_outfit, db)


@app.delete("/v1/outfits/{outfit_id}", response_model=DeleteOutfitResponse)
async def remove_saved_outfit(
    outfit_id: str,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    user_id = get_current_user_id(db, authorization)

    try:
        parsed_outfit_id = uuid.UUID(outfit_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid outfit_id")

    outfit = get_outfit_by_user(db, user_id, parsed_outfit_id)
    if not outfit:
        raise HTTPException(status_code=404, detail="Outfit not found")

    delete_outfit(db, outfit)

    return DeleteOutfitResponse(ok=True)
