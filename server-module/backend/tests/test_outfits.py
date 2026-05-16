from test_items_flow import create_confirmed_items


def create_item_ids(test_client, headers, sample_upload_file, monkeypatch, fake_storage, fake_ml_response):
    created = create_confirmed_items(
        test_client,
        headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    return [item["item_id"] for item in created["items"]]


def create_outfit_payload(item_ids):
    return {
        "name": "Weekend",
        "item_ids": item_ids,
        "style": "casual",
        "colors": ["white", "blue"],
        "season": "summer",
    }


def test_create_outfit_success(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )

    response = test_client.post("/v1/outfits", headers=auth_headers, json=create_outfit_payload(item_ids))

    assert response.status_code == 200
    payload = response.json()
    assert payload["name"] == "Weekend"
    assert payload["style"] == "casual"
    assert payload["colors"] == ["white", "blue"]
    assert payload["season"] == "summer"
    assert {item["item_id"] for item in payload["items"]} == set(item_ids)


def test_create_outfit_requires_auth(test_client):
    response = test_client.post("/v1/outfits", json={"name": "No auth", "item_ids": []})

    assert response.status_code == 401


def test_create_outfit_requires_item_ids(test_client, auth_headers):
    response = test_client.post("/v1/outfits", headers=auth_headers, json={"name": "Empty", "item_ids": []})

    assert response.status_code == 400


def test_create_outfit_rejects_invalid_item_id(test_client, auth_headers):
    response = test_client.post(
        "/v1/outfits",
        headers=auth_headers,
        json={"name": "Bad", "item_ids": ["not-a-uuid"]},
    )

    assert response.status_code == 400


def test_create_outfit_rejects_invalid_season(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )

    response = test_client.post(
        "/v1/outfits",
        headers=auth_headers,
        json={**create_outfit_payload(item_ids), "season": "monsoon"},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "Invalid season"


def test_create_outfit_rejects_foreign_items(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    foreign_item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )

    response = test_client.post(
        "/v1/outfits",
        headers=second_user_auth_headers,
        json=create_outfit_payload([foreign_item_ids[0]]),
    )

    assert response.status_code == 400
    assert "do not belong" in response.json()["detail"]


def test_get_outfits_returns_only_current_user_outfits(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    created = test_client.post("/v1/outfits", headers=auth_headers, json=create_outfit_payload(item_ids)).json()

    own_response = test_client.get("/v1/outfits", headers=auth_headers)
    foreign_response = test_client.get("/v1/outfits", headers=second_user_auth_headers)

    assert [outfit["outfit_id"] for outfit in own_response.json()["outfits"]] == [created["outfit_id"]]
    assert foreign_response.json()["outfits"] == []


def test_update_outfit_success(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    outfit = test_client.post("/v1/outfits", headers=auth_headers, json=create_outfit_payload(item_ids)).json()

    response = test_client.put(
        f"/v1/outfits/{outfit['outfit_id']}",
        headers=auth_headers,
        json={"name": "Updated"},
    )

    assert response.status_code == 200
    assert response.json()["name"] == "Updated"


def test_update_foreign_outfit_returns_404(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    outfit = test_client.post("/v1/outfits", headers=auth_headers, json=create_outfit_payload(item_ids)).json()

    response = test_client.put(
        f"/v1/outfits/{outfit['outfit_id']}",
        headers=second_user_auth_headers,
        json={"name": "Taken"},
    )

    assert response.status_code == 404


def test_delete_outfit_success(
    test_client,
    auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    outfit = test_client.post("/v1/outfits", headers=auth_headers, json=create_outfit_payload(item_ids)).json()

    delete_response = test_client.delete(f"/v1/outfits/{outfit['outfit_id']}", headers=auth_headers)
    list_response = test_client.get("/v1/outfits", headers=auth_headers)

    assert delete_response.status_code == 200
    assert delete_response.json() == {"ok": True}
    assert list_response.json()["outfits"] == []


def test_delete_foreign_outfit_returns_404(
    test_client,
    auth_headers,
    second_user_auth_headers,
    sample_upload_file,
    monkeypatch,
    fake_storage,
    fake_ml_response,
):
    item_ids = create_item_ids(
        test_client,
        auth_headers,
        sample_upload_file,
        monkeypatch,
        fake_storage,
        fake_ml_response,
    )
    outfit = test_client.post("/v1/outfits", headers=auth_headers, json=create_outfit_payload(item_ids)).json()

    response = test_client.delete(f"/v1/outfits/{outfit['outfit_id']}", headers=second_user_auth_headers)

    assert response.status_code == 404


def test_invalid_outfit_id_returns_400(test_client, auth_headers):
    update_response = test_client.put(
        "/v1/outfits/not-a-uuid",
        headers=auth_headers,
        json={"name": "Bad"},
    )
    delete_response = test_client.delete("/v1/outfits/not-a-uuid", headers=auth_headers)

    assert update_response.status_code == 400
    assert delete_response.status_code == 400
