import pytest
from pydantic import ValidationError

from app.models import ClothingCategory, DraftStatus, Gender, OutfitSource, Season
from app.schemas import (
    AuthRequest,
    AuthResponse,
    BodyProfileDto,
    BodyProfileRequest,
    ClothingItemDto,
    ConfirmRequest,
    CreateOutfitRequest,
    DraftCandidateResponse,
    PrepareResponse,
    RegisterRequest,
    UpdateOutfitRequest,
    UserProfileDto,
)


def test_register_request_requires_email_and_password():
    with pytest.raises(ValidationError):
        RegisterRequest(email="user@example.com")


def test_auth_request_serializes_expected_fields():
    request = AuthRequest(email="user@example.com", password="secret123")

    assert request.dict() == {"email": "user@example.com", "password": "secret123"}


def test_register_request_accepts_optional_name():
    request = RegisterRequest(email="user@example.com", password="secret123", name="Alice")

    assert request.name == "Alice"


def test_auth_response_contains_token_and_profile():
    response = AuthResponse(
        token="token-1",
        user=UserProfileDto(user_id="user-1", email="user@example.com", name="Alice"),
    )

    assert response.token == "token-1"
    assert response.user.email == "user@example.com"


def test_body_profile_request_accepts_measurements():
    profile = BodyProfileRequest(
        skin_tone="warm",
        eye_color="brown",
        hair_color="black",
        height_cm=170,
        weight_kg=60,
        chest_cm=88,
        waist_cm=70,
    )

    assert profile.height_cm == 170
    assert BodyProfileDto(**profile.dict()).waist_cm == 70


def test_confirm_request_requires_draft_id():
    with pytest.raises(ValidationError):
        ConfirmRequest(selected_items=[])


def test_confirm_request_accepts_candidate_ids():
    request = ConfirmRequest(
        draft_id="draft-1",
        selected_items=[{"candidate_id": "candidate-1", "user_overrides": {"name": "White shirt"}}],
    )

    assert request.selected_items[0].candidate_id == "candidate-1"
    assert request.selected_items[0].user_overrides["name"] == "White shirt"


def test_prepare_response_serializes_candidates():
    response = PrepareResponse(
        draft_id="draft-1",
        original_image_url="http://testserver/media/original/a.png",
        items=[
            DraftCandidateResponse(
                candidate_id="candidate-1",
                normalized_image_url="http://testserver/media/normalized/a.png",
                category="top",
                subcategory="shirt",
                season="summer",
                warmth_level=1,
                confidence=0.9,
                colors=[{"name": "white", "hex": "#ffffff", "share": 0.8}],
            )
        ],
    )

    data = response.dict()
    assert data["needs_user_review"] is True
    assert data["items"][0]["colors"][0]["name"] == "white"


def test_clothing_item_dto_serializes_expected_fields():
    item = ClothingItemDto(
        item_id="item-1",
        name="Sneakers",
        category="shoes",
        subcategory="sneakers",
        normalized_image_url="http://testserver/media/normalized/shoes.png",
        season=None,
        warmth_level=None,
        is_favorite=True,
    )

    assert item.dict()["is_favorite"] is True
    assert item.category == "shoes"


def test_create_outfit_request_requires_item_ids():
    with pytest.raises(ValidationError):
        CreateOutfitRequest(name="Daily")


def test_create_outfit_request_accepts_style_colors_season():
    request = CreateOutfitRequest(
        name="Daily",
        item_ids=["item-1", "item-2"],
        style="casual",
        colors=["white", "blue"],
        season="summer",
    )

    assert request.style == "casual"
    assert request.colors == ["white", "blue"]
    assert request.season == "summer"


def test_update_outfit_request_allows_null_name():
    request = UpdateOutfitRequest(name=None)

    assert request.name is None


def test_enum_values_match_mobile_contract():
    assert {value.value for value in Gender} == {"male", "female", "prefer_not_to_say"}
    assert {value.value for value in ClothingCategory} == {
        "top",
        "bottom",
        "shoes",
        "outerwear",
        "accessory",
    }
    assert {value.value for value in Season} == {
        "winter",
        "spring",
        "summer",
        "autumn",
        "all_season",
    }
    assert {value.value for value in DraftStatus} == {
        "created",
        "processed",
        "confirmed",
        "canceled",
        "failed",
    }
    assert {value.value for value in OutfitSource} == {"user_created", "generated"}
