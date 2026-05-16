import uuid

import pytest

from app import crud
from app.models import ClothingCategory, ClothingItem, DraftStatus, Season
from app.schemas import ConfirmSelectedItem


def add_item(db, user, category=ClothingCategory.top, name="Item"):
    item = ClothingItem(
        user_id=user.id,
        name=name,
        category=category,
        subcategory="basic",
        season=Season.summer,
        warmth_level=1,
        normalized_image_key=f"normalized/{uuid.uuid4().hex}.png",
        colors={"colors": [{"name": "white"}]},
        ml_meta={"confidence": 0.9},
    )
    db.add(item)
    db.commit()
    db.refresh(item)
    return item


def test_create_draft_success(test_db_session, create_test_user):
    user = create_test_user()

    draft = crud.create_draft(test_db_session, user.id, "original/a.png", None, {"source": "test"})

    assert draft.id is not None
    assert draft.user_id == user.id
    assert draft.status == DraftStatus.processed
    assert draft.attributes == {"source": "test"}


def test_get_draft_returns_requested_draft(test_db_session, create_test_user):
    user = create_test_user()
    draft = crud.create_draft(test_db_session, user.id, "original/a.png", None, {})

    found = crud.get_draft(test_db_session, draft.id)

    assert found.id == draft.id


def test_cancel_draft_sets_canceled_status(test_db_session, create_test_user):
    user = create_test_user()
    draft = crud.create_draft(test_db_session, user.id, "original/a.png", None, {})

    crud.cancel_draft(test_db_session, draft)
    test_db_session.refresh(draft)

    assert draft.status == DraftStatus.canceled


def test_create_draft_candidates_success(test_db_session, create_test_user):
    user = create_test_user()
    draft = crud.create_draft(test_db_session, user.id, "original/a.png", None, {})

    candidate = crud.create_draft_candidate(
        test_db_session,
        draft.id,
        "normalized/a.png",
        ClothingCategory.top,
        "shirt",
        Season.summer,
        1,
        {"colors": [{"name": "white"}]},
        0.95,
        {"confidence": 0.95},
    )

    candidates = crud.get_draft_candidates(test_db_session, draft.id)
    assert candidates == [candidate]
    assert candidate.category == ClothingCategory.top


def test_confirm_candidates_creates_clothing_items(test_db_session, create_test_user):
    user = create_test_user()
    draft = crud.create_draft(test_db_session, user.id, "original/a.png", None, {})
    candidate = crud.create_draft_candidate(
        test_db_session,
        draft.id,
        "normalized/a.png",
        ClothingCategory.top,
        "shirt",
        Season.summer,
        1,
        {"colors": [{"name": "white"}]},
        0.95,
        {"confidence": 0.95, "subcategory": "shirt"},
    )

    items = crud.confirm_candidates_to_items(
        test_db_session,
        draft,
        [ConfirmSelectedItem(candidate_id=str(candidate.id), user_overrides={"name": "White shirt"})],
    )

    assert len(items) == 1
    assert items[0].user_id == user.id
    assert items[0].name == "White shirt"
    assert draft.status == DraftStatus.confirmed


def test_confirm_candidates_does_not_use_candidate_from_another_draft(test_db_session, create_test_user):
    user = create_test_user()
    own_draft = crud.create_draft(test_db_session, user.id, "original/own.png", None, {})
    foreign_draft = crud.create_draft(test_db_session, user.id, "original/foreign.png", None, {})
    foreign_candidate = crud.create_draft_candidate(
        test_db_session,
        foreign_draft.id,
        "normalized/foreign.png",
        ClothingCategory.top,
        "shirt",
        Season.summer,
        1,
        {"colors": []},
        0.8,
        {},
    )

    items = crud.confirm_candidates_to_items(
        test_db_session,
        own_draft,
        [ConfirmSelectedItem(candidate_id=str(foreign_candidate.id))],
    )

    assert items == []
    assert crud.get_items_by_user(test_db_session, user.id) == []


def test_confirm_legacy_draft_to_item_uses_overrides(test_db_session, create_test_user):
    user = create_test_user()
    draft = crud.create_draft(
        test_db_session,
        user.id,
        "original/a.png",
        "normalized/a.png",
        {"category": "unknown", "season": "bad-season"},
    )

    item = crud.confirm_draft_to_item(
        test_db_session,
        draft,
        {"category": "bottom", "season": "winter", "name": "Jeans"},
    )

    assert item.category == ClothingCategory.bottom
    assert item.season == Season.winter
    assert item.name == "Jeans"


def test_get_items_by_user_returns_only_own_items(test_db_session, create_test_user):
    user_a = create_test_user(email="a@example.com")
    user_b = create_test_user(email="b@example.com")
    item_a = add_item(test_db_session, user_a, name="A")
    add_item(test_db_session, user_b, name="B")

    items = crud.get_items_by_user(test_db_session, user_a.id)

    assert [item.id for item in items] == [item_a.id]


def test_update_item_favorite_success(test_db_session, create_test_user):
    user = create_test_user()
    item = add_item(test_db_session, user, name="Old")

    updated = crud.update_item_favorite(test_db_session, item, True, "  New name  ")

    assert updated.is_favorite is True
    assert updated.name == "New name"


def test_update_item_name_success(test_db_session, create_test_user):
    user = create_test_user()
    item = add_item(test_db_session, user, name="Old")

    updated = crud.update_item_name(test_db_session, item, "  Jacket  ")

    assert updated.name == "Jacket"


def test_delete_item_success_removes_outfit_links(test_db_session, create_test_user):
    user = create_test_user()
    item = add_item(test_db_session, user)
    outfit = crud.create_outfit(test_db_session, user.id, "Look", [item.id])

    crud.delete_item(test_db_session, item)

    assert crud.get_item_by_user(test_db_session, user.id, item.id) is None
    assert crud.get_outfit_items(test_db_session, outfit.id, user.id) == []


def test_body_profile_crud_success(test_db_session, create_test_user):
    user = create_test_user()

    profile = crud.upsert_body_profile(
        test_db_session,
        user.id,
        {"skin_tone": "warm", "height_cm": 171, "weight_kg": 62, "waist_cm": 70},
    )
    updated = crud.upsert_body_profile(
        test_db_session,
        user.id,
        {"skin_tone": "cool", "height_cm": 172, "weight_kg": 63, "chest_cm": 88},
    )

    assert profile.id == updated.id
    assert updated.skin_tone == "cool"
    assert updated.height_cm == 172

    crud.reset_body_profile(test_db_session, user.id)
    assert crud.get_body_profile(test_db_session, user.id) is None


def test_body_profile_is_user_scoped(test_db_session, create_test_user):
    user_a = create_test_user(email="a@example.com")
    user_b = create_test_user(email="b@example.com")
    crud.upsert_body_profile(test_db_session, user_a.id, {"height_cm": 180})

    assert crud.get_body_profile(test_db_session, user_b.id) is None


def test_create_outfit_success(test_db_session, create_test_user):
    user = create_test_user()
    top = add_item(test_db_session, user, ClothingCategory.top)
    shoes = add_item(test_db_session, user, ClothingCategory.shoes)

    outfit = crud.create_outfit(
        test_db_session,
        user.id,
        "Casual",
        [top.id, shoes.id],
        style=" casual ",
        colors=[" white ", "", "blue"],
        season="summer",
    )

    assert outfit.name == "Casual"
    assert outfit.style == "casual"
    assert outfit.context == {"colors": ["white", "blue"]}
    assert outfit.season == Season.summer
    assert {item.id for item in crud.get_outfit_items(test_db_session, outfit.id, user.id)} == {
        top.id,
        shoes.id,
    }


def test_create_outfit_rejects_foreign_item_id(test_db_session, create_test_user):
    user_a = create_test_user(email="a@example.com")
    user_b = create_test_user(email="b@example.com")
    foreign_item = add_item(test_db_session, user_b)

    with pytest.raises(ValueError):
        crud.create_outfit(test_db_session, user_a.id, "Bad", [foreign_item.id])


def test_create_outfit_rejects_invalid_season(test_db_session, create_test_user):
    user = create_test_user()
    item = add_item(test_db_session, user)

    with pytest.raises(ValueError, match="Invalid season"):
        crud.create_outfit(test_db_session, user.id, "Bad", [item.id], season="monsoon")


def test_get_outfits_by_user_returns_only_own_outfits(test_db_session, create_test_user):
    user_a = create_test_user(email="a@example.com")
    user_b = create_test_user(email="b@example.com")
    item_a = add_item(test_db_session, user_a)
    item_b = add_item(test_db_session, user_b)
    outfit_a = crud.create_outfit(test_db_session, user_a.id, "A", [item_a.id])
    crud.create_outfit(test_db_session, user_b.id, "B", [item_b.id])

    outfits = crud.get_outfits_by_user(test_db_session, user_a.id)

    assert [outfit.id for outfit in outfits] == [outfit_a.id]


def test_update_and_delete_outfit_success(test_db_session, create_test_user):
    user = create_test_user()
    item = add_item(test_db_session, user)
    outfit = crud.create_outfit(test_db_session, user.id, "Old", [item.id])

    updated = crud.update_outfit_name(test_db_session, outfit, "  New  ")

    assert updated.name == "New"
    assert crud.get_outfit_by_user(test_db_session, user.id, outfit.id).id == outfit.id

    crud.delete_outfit(test_db_session, outfit)
    assert crud.get_outfit_by_user(test_db_session, user.id, outfit.id) is None
