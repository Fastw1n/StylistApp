from app import main as main_module


def mock_ml(monkeypatch, fake_ml_response):
    monkeypatch.setattr(
        main_module,
        "segment_image_via_ml_service",
        lambda image_bytes, filename: fake_ml_response,
    )


def prepare_draft(test_client, headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response):
    mock_ml(monkeypatch, fake_ml_response)
    response = test_client.post("/v1/items/prepare", headers=headers, files=sample_upload_file)
    assert response.status_code == 200
    return response.json()


def confirm_all(test_client, headers, draft_payload):
    response = test_client.post(
        "/v1/items/confirm",
        headers=headers,
        json={
            "draft_id": draft_payload["draft_id"],
            "selected_items": [
                {
                    "candidate_id": item["candidate_id"],
                    "user_overrides": {"name": f"Confirmed {index + 1}"},
                }
                for index, item in enumerate(draft_payload["items"])
            ],
        },
    )
    assert response.status_code == 200
    return response.json()


def create_confirmed_items(test_client, headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response):
    draft = prepare_draft(test_client, headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response)
    return confirm_all(test_client, headers, draft)


def test_prepare_item_success_creates_draft_and_candidates(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    payload = prepare_draft(test_client, auth_headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response)

    assert payload["draft_id"]
    assert payload["original_image_url"].endswith("original/0_sample.png")
    assert len(payload["items"]) == 2
    assert payload["items"][0]["category"] == "top"
    assert payload["items"][0]["colors"][0]["name"] == "white"
    assert [saved["folder"] for saved in fake_storage] == ["original", "normalized", "normalized"]


def test_prepare_item_requires_auth(test_client, sample_upload_file):
    response = test_client.post("/v1/items/prepare", files=sample_upload_file)

    assert response.status_code == 401


def test_prepare_item_invalid_file_returns_400(test_client, auth_headers):
    response = test_client.post(
        "/v1/items/prepare",
        headers=auth_headers,
        files={"image": ("notes.txt", b"not an image", "text/plain")},
    )

    assert response.status_code == 400


def test_prepare_item_empty_ml_result_returns_422(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
):
    monkeypatch.setattr(main_module, "segment_image_via_ml_service", lambda image_bytes, filename: [])

    response = test_client.post("/v1/items/prepare", headers=auth_headers, files=sample_upload_file)

    assert response.status_code == 422


def test_prepare_item_ml_failure_returns_controlled_error_and_no_items(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
):
    def fail_ml(image_bytes, filename):
        raise RuntimeError("model unavailable")

    monkeypatch.setattr(main_module, "segment_image_via_ml_service", fail_ml)

    response = test_client.post("/v1/items/prepare", headers=auth_headers, files=sample_upload_file)
    items_response = test_client.get("/v1/items", headers=auth_headers)

    assert response.status_code == 500
    assert "ML service failed" in response.json()["detail"]
    assert items_response.json()["items"] == []


def test_prepare_item_storage_failure_does_not_create_items(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_ml_response,
):
    monkeypatch.setattr(main_module, "segment_image_via_ml_service", lambda image_bytes, filename: fake_ml_response)

    def fail_storage(folder, filename, content):
        raise RuntimeError("storage down")

    monkeypatch.setattr(main_module.storage, "save_bytes", fail_storage)

    response = test_client.post("/v1/items/prepare", headers=auth_headers, files=sample_upload_file)
    items_response = test_client.get("/v1/items", headers=auth_headers)

    assert response.status_code == 500
    assert items_response.json()["items"] == []


def test_confirm_item_success_creates_clothing_items(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    draft = prepare_draft(test_client, auth_headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response)

    payload = confirm_all(test_client, auth_headers, draft)
    list_response = test_client.get("/v1/items", headers=auth_headers)

    assert len(payload["items"]) == 2
    assert payload["items"][0]["name"] == "Confirmed 1"
    assert len(list_response.json()["items"]) == 2


def test_confirm_item_requires_own_draft(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    draft = prepare_draft(test_client, auth_headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response)

    response = test_client.post(
        "/v1/items/confirm",
        headers=second_user_auth_headers,
        json={
            "draft_id": draft["draft_id"],
            "selected_items": [{"candidate_id": draft["items"][0]["candidate_id"]}],
        },
    )

    assert response.status_code == 403


def test_cancel_draft_success_and_confirm_canceled_draft_is_rejected(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    draft = prepare_draft(test_client, auth_headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response)

    cancel_response = test_client.post(
        "/v1/items/cancel",
        headers=auth_headers,
        json={"draft_id": draft["draft_id"]},
    )
    confirm_response = test_client.post(
        "/v1/items/confirm",
        headers=auth_headers,
        json={
            "draft_id": draft["draft_id"],
            "selected_items": [{"candidate_id": draft["items"][0]["candidate_id"]}],
        },
    )
    items_response = test_client.get("/v1/items", headers=auth_headers)

    assert cancel_response.status_code == 200
    assert confirm_response.status_code == 400
    assert items_response.json()["items"] == []


def test_cancel_foreign_draft_returns_403(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    draft = prepare_draft(test_client, auth_headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response)

    response = test_client.post(
        "/v1/items/cancel",
        headers=second_user_auth_headers,
        json={"draft_id": draft["draft_id"]},
    )

    assert response.status_code == 403


def test_get_items_returns_only_current_user_items(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    create_confirmed_items(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )

    own_items = test_client.get("/v1/items", headers=auth_headers).json()["items"]
    other_items = test_client.get("/v1/items", headers=second_user_auth_headers).json()["items"]

    assert len(own_items) == 2
    assert other_items == []


def test_update_item_favorite_success(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    created = create_confirmed_items(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    item_id = created["items"][0]["item_id"]

    response = test_client.put(
        f"/v1/items/{item_id}/favorite",
        headers=auth_headers,
        json={"is_favorite": True, "name": "Favorite shirt"},
    )

    assert response.status_code == 200
    assert response.json()["is_favorite"] is True
    assert response.json()["name"] == "Favorite shirt"


def test_update_item_name_success(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    created = create_confirmed_items(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    item_id = created["items"][0]["item_id"]

    response = test_client.put(
        f"/v1/items/{item_id}",
        headers=auth_headers,
        json={"name": "Renamed shirt"},
    )

    assert response.status_code == 200
    assert response.json()["name"] == "Renamed shirt"


def test_delete_item_success(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    created = create_confirmed_items(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    item_id = created["items"][0]["item_id"]

    delete_response = test_client.delete(f"/v1/items/{item_id}", headers=auth_headers)
    list_response = test_client.get("/v1/items", headers=auth_headers)

    assert delete_response.status_code == 200
    assert all(item["item_id"] != item_id for item in list_response.json()["items"])


def test_user_cannot_modify_or_delete_another_user_item(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    created = create_confirmed_items(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    item_id = created["items"][0]["item_id"]

    favorite_response = test_client.put(
        f"/v1/items/{item_id}/favorite",
        headers=second_user_auth_headers,
        json={"is_favorite": True},
    )
    rename_response = test_client.put(
        f"/v1/items/{item_id}",
        headers=second_user_auth_headers,
        json={"name": "Taken"},
    )
    delete_response = test_client.delete(f"/v1/items/{item_id}", headers=second_user_auth_headers)

    assert favorite_response.status_code == 404
    assert rename_response.status_code == 404
    assert delete_response.status_code == 404


def test_invalid_item_id_returns_400(test_client, auth_headers):
    response = test_client.delete("/v1/items/not-a-uuid", headers=auth_headers)

    assert response.status_code == 400
